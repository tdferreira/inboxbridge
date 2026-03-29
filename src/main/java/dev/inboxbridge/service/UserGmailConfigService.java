package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.UpdateUserGmailConfigRequest;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserGmailConfigService {

    @Inject
    UserGmailConfigRepository repository;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    GoogleOAuthService googleOAuthService;

    public Optional<UserGmailConfigView> viewForUser(Long userId) {
        return repository.findByUserId(userId).map(config -> toView(userId, config));
    }

    public UserGmailConfigView defaultView(Long userId) {
        boolean sharedClientConfigured = sharedGoogleClientConfigured();
        boolean tokenStored = googleOAuthStored(userId);
        return new UserGmailConfigView(
                inboxBridgeConfig.gmail().destinationUser(),
                false,
                false,
                tokenStored,
                defaultRedirectUri(),
                defaultRedirectUri(),
                sharedClientConfigured,
                inboxBridgeConfig.gmail().createMissingLabels(),
                inboxBridgeConfig.gmail().neverMarkSpam(),
                inboxBridgeConfig.gmail().processForCalendar());
    }

    @Transactional
    public GmailUnlinkResult unlinkForUser(Long userId) {
        Optional<UserGmailConfig> storedConfig = repository.findByUserId(userId);
        String subjectKey = "user-gmail:" + userId;
        String refreshToken = effectiveRefreshToken(userId, storedConfig.orElse(null));

        boolean providerRevocationAttempted = refreshToken != null && !refreshToken.isBlank();
        boolean providerRevoked = !providerRevocationAttempted || googleOAuthService.revokeToken(refreshToken);

        storedConfig.ifPresent(config -> {
            config.refreshTokenCiphertext = null;
            config.refreshTokenNonce = null;
            config.updatedAt = Instant.now();
            repository.persist(config);
        });

        oAuthCredentialService.deleteGoogleCredential(subjectKey);
        googleOAuthService.clearCachedToken(subjectKey);

        return new GmailUnlinkResult(providerRevocationAttempted, providerRevoked);
    }

    @Transactional
    public boolean markGoogleAccessRevoked(GmailTarget target) {
        if (target == null || target.userId() == null) {
            return false;
        }

        Optional<UserGmailConfig> storedConfig = repository.findByUserId(target.userId());
        boolean hadStoredRefreshToken = storedConfig.map(config ->
                config.refreshTokenCiphertext != null || config.refreshTokenNonce != null)
                .orElse(false);
        boolean hadOAuthCredential = oAuthCredentialService.deleteGoogleCredential(target.subjectKey());

        storedConfig.ifPresent(config -> {
            config.refreshTokenCiphertext = null;
            config.refreshTokenNonce = null;
            config.updatedAt = Instant.now();
            repository.persist(config);
        });

        googleOAuthService.clearCachedToken(target.subjectKey());
        return hadStoredRefreshToken || hadOAuthCredential;
    }

    @Transactional
    public boolean markGoogleAccessRevoked(GmailApiDestinationTarget target) {
        if (target == null || target.userId() == null) {
            return false;
        }

        Optional<UserGmailConfig> storedConfig = repository.findByUserId(target.userId());
        boolean hadStoredRefreshToken = storedConfig.map(config ->
                config.refreshTokenCiphertext != null || config.refreshTokenNonce != null)
                .orElse(false);
        boolean hadOAuthCredential = oAuthCredentialService.deleteGoogleCredential(target.subjectKey());

        storedConfig.ifPresent(config -> {
            config.refreshTokenCiphertext = null;
            config.refreshTokenNonce = null;
            config.updatedAt = Instant.now();
            repository.persist(config);
        });

        googleOAuthService.clearCachedToken(target.subjectKey());
        return hadStoredRefreshToken || hadOAuthCredential;
    }

    public Optional<ResolvedUserGmailConfig> resolveForUser(Long userId) {
        if (!secretEncryptionService.isConfigured()) {
            return Optional.empty();
        }
        Optional<UserGmailConfig> storedConfig = repository.findByUserId(userId);
        boolean hasStoredGoogleRefreshToken = oAuthCredentialService.findGoogleCredential("user-gmail:" + userId)
                .map(credential -> credential.refreshToken() != null && !credential.refreshToken().isBlank())
                .orElse(false);
        if (storedConfig.isEmpty() && !hasStoredGoogleRefreshToken) {
            return Optional.empty();
        }

        UserGmailConfig config = storedConfig.orElse(null);
        return Optional.of(new ResolvedUserGmailConfig(
                userId,
                config == null ? inboxBridgeConfig.gmail().destinationUser() : config.destinationUser,
                effectiveClientId(userId, config),
                effectiveClientSecret(userId, config),
                effectiveRefreshToken(userId, config),
                config == null ? defaultRedirectUri() : nonBlankOrDefault(config.redirectUri, defaultRedirectUri()),
                config == null ? inboxBridgeConfig.gmail().createMissingLabels() : config.createMissingLabels,
                config == null ? inboxBridgeConfig.gmail().neverMarkSpam() : config.neverMarkSpam,
                config == null ? inboxBridgeConfig.gmail().processForCalendar() : config.processForCalendar));
    }

    public boolean destinationLinked(Long userId) {
        return googleOAuthStored(userId);
    }

    @Transactional
    public UserGmailConfigView update(AppUser user, UpdateUserGmailConfigRequest request) {
        // User-owned Gmail OAuth for regular accounts is intentionally a
        // button-driven consent flow. Only admins can override the advanced
        // Gmail account settings or secret material from the UI.
        if (user.role != AppUser.Role.ADMIN) {
            throw new IllegalStateException("Only admins can override advanced Gmail account settings from the admin UI.");
        }
        if (!secretEncryptionService.isConfigured() && containsSecrets(request)) {
            throw new IllegalStateException("Secure secret storage must be configured before storing user Gmail credentials in the database.");
        }

        UserGmailConfig config = repository.findByUserId(user.id).orElseGet(UserGmailConfig::new);
        config.userId = user.id;
        config.destinationUser = nonBlankOrDefault(request.destinationUser(), inboxBridgeConfig.gmail().destinationUser());
        config.redirectUri = nonBlankOrDefault(request.redirectUri(), inboxBridgeConfig.gmail().redirectUri());
        config.createMissingLabels = request.createMissingLabels() == null ? inboxBridgeConfig.gmail().createMissingLabels() : request.createMissingLabels();
        config.neverMarkSpam = request.neverMarkSpam() == null ? inboxBridgeConfig.gmail().neverMarkSpam() : request.neverMarkSpam();
        config.processForCalendar = request.processForCalendar() == null ? inboxBridgeConfig.gmail().processForCalendar() : request.processForCalendar();
        config.keyVersion = secretEncryptionService.isConfigured() ? secretEncryptionService.keyVersion() : null;
        config.updatedAt = Instant.now();

        setSecret(request.clientId(), config, "client-id");
        setSecret(request.clientSecret(), config, "client-secret");
        setSecret(request.refreshToken(), config, "refresh-token");

        repository.persist(config);
        return toView(user.id, config);
    }

    private void setSecret(String value, UserGmailConfig config, String secretName) {
        if (value == null || value.isBlank()) {
            return;
        }
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(value, "user-gmail:" + config.userId + ":" + secretName);
        switch (secretName) {
            case "client-id" -> {
                config.clientIdCiphertext = encrypted.ciphertextBase64();
                config.clientIdNonce = encrypted.nonceBase64();
            }
            case "client-secret" -> {
                config.clientSecretCiphertext = encrypted.ciphertextBase64();
                config.clientSecretNonce = encrypted.nonceBase64();
            }
            case "refresh-token" -> {
                config.refreshTokenCiphertext = encrypted.ciphertextBase64();
                config.refreshTokenNonce = encrypted.nonceBase64();
            }
            default -> throw new IllegalArgumentException("Unsupported secret name");
        }
    }

    public Optional<GoogleOAuthService.GoogleOAuthProfile> googleProfileForUser(Long userId) {
        if (secretEncryptionService.isConfigured()) {
            Optional<GoogleOAuthService.GoogleOAuthProfile> userManaged = resolveForUser(userId)
                    .filter(config -> !config.clientId().isBlank() && !config.clientSecret().isBlank())
                    .map(config -> new GoogleOAuthService.GoogleOAuthProfile(
                            "user-gmail:" + userId,
                            config.clientId(),
                            config.clientSecret(),
                            config.refreshToken(),
                            nonBlankOrDefault(config.redirectUri(), defaultRedirectUri()),
                            GoogleOAuthService.GMAIL_TARGET_SCOPE));
            if (userManaged.isPresent()) {
                return userManaged;
            }
        }

        if (!sharedGoogleClientConfigured()) {
            return Optional.empty();
        }

        String redirectUri = repository.findByUserId(userId)
                .map(config -> nonBlankOrDefault(config.redirectUri, defaultRedirectUri()))
                .orElse(defaultRedirectUri());

        return Optional.of(new GoogleOAuthService.GoogleOAuthProfile(
                "user-gmail:" + userId,
                systemOAuthAppSettingsService.googleClientId(),
                systemOAuthAppSettingsService.googleClientSecret(),
                "",
                redirectUri,
                GoogleOAuthService.GMAIL_TARGET_SCOPE));
    }

    public boolean sharedGoogleClientConfigured() {
        return systemOAuthAppSettingsService.googleClientConfigured();
    }

    public String defaultRedirectUri() {
        return nonBlankOrDefault(inboxBridgeConfig.gmail().redirectUri(), "https://localhost:3000/api/google-oauth/callback");
    }

    private String decrypt(String ciphertext, String nonce, String keyVersion, String context) {
        if (ciphertext == null || nonce == null) {
            return "";
        }
        return secretEncryptionService.decrypt(ciphertext, nonce, keyVersion, context);
    }

    private String effectiveClientId(Long userId, UserGmailConfig config) {
        String stored = config == null ? "" : decrypt(config.clientIdCiphertext, config.clientIdNonce, config.keyVersion, "user-gmail:" + userId + ":client-id");
        if (!stored.isBlank()) {
            return stored;
        }
        return sharedGoogleClientConfigured() ? systemOAuthAppSettingsService.googleClientId() : "";
    }

    private String effectiveClientSecret(Long userId, UserGmailConfig config) {
        String stored = config == null ? "" : decrypt(config.clientSecretCiphertext, config.clientSecretNonce, config.keyVersion, "user-gmail:" + userId + ":client-secret");
        if (!stored.isBlank()) {
            return stored;
        }
        return sharedGoogleClientConfigured() ? systemOAuthAppSettingsService.googleClientSecret() : "";
    }

    private String effectiveRefreshToken(Long userId, UserGmailConfig config) {
        String stored = config == null ? "" : decrypt(config.refreshTokenCiphertext, config.refreshTokenNonce, config.keyVersion, "user-gmail:" + userId + ":refresh-token");
        if (!stored.isBlank()) {
            return stored;
        }
        return oAuthCredentialService.findGoogleCredential("user-gmail:" + userId)
                .map(OAuthCredentialService.StoredOAuthCredential::refreshToken)
                .orElse("");
    }

    private UserGmailConfigView toView(Long userId, UserGmailConfig config) {
        boolean sharedClientConfigured = sharedGoogleClientConfigured();
        return new UserGmailConfigView(
                config.destinationUser,
                config.clientIdCiphertext != null,
                config.clientSecretCiphertext != null,
                config.refreshTokenCiphertext != null || googleOAuthStored(userId),
                nonBlankOrDefault(config.redirectUri, defaultRedirectUri()),
                defaultRedirectUri(),
                sharedClientConfigured,
                config.createMissingLabels,
                config.neverMarkSpam,
                config.processForCalendar);
    }

    private boolean googleOAuthStored(Long userId) {
        return oAuthCredentialService.findGoogleCredential("user-gmail:" + userId)
                .map(credential -> credential.refreshToken() != null && !credential.refreshToken().isBlank())
                .orElse(false);
    }

    private boolean containsSecrets(UpdateUserGmailConfigRequest request) {
        return isProvided(request.clientId()) || isProvided(request.clientSecret()) || isProvided(request.refreshToken());
    }

    private String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean isConfiguredValue(String value) {
        return isProvided(value) && !"replace-me".equals(value.trim());
    }

    private boolean isProvided(String value) {
        return value != null && !value.isBlank();
    }

    public record ResolvedUserGmailConfig(
            Long userId,
            String destinationUser,
            String clientId,
            String clientSecret,
            String refreshToken,
            String redirectUri,
            boolean createMissingLabels,
            boolean neverMarkSpam,
            boolean processForCalendar) {
    }

    public record GmailUnlinkResult(
            boolean providerRevocationAttempted,
            boolean providerRevoked) {
    }
}
