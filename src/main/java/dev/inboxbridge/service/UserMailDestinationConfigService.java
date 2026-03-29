package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.dto.UpdateUserMailDestinationRequest;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.dto.UserMailDestinationView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserMailDestinationConfigService {

    public static final String PROVIDER_GMAIL = "GMAIL_API";
    public static final String PROVIDER_OUTLOOK = "OUTLOOK_IMAP";
    public static final String PROVIDER_YAHOO = "YAHOO_IMAP";
    public static final String PROVIDER_PROTON_BRIDGE = "PROTON_BRIDGE_IMAP";
    public static final String PROVIDER_CUSTOM = "CUSTOM_IMAP";

    @Inject
    UserMailDestinationConfigRepository repository;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    public UserMailDestinationView viewForUser(Long userId) {
        UserMailDestinationConfig config = repository.findByUserId(userId).orElse(null);
        if (config == null || PROVIDER_GMAIL.equals(config.provider)) {
            UserGmailConfigView gmailView = userGmailConfigService.viewForUser(userId)
                    .orElse(userGmailConfigService.defaultView(userId));
            return new UserMailDestinationView(
                    PROVIDER_GMAIL,
                    "GMAIL_API",
                    gmailView.refreshTokenConfigured(),
                    false,
                    gmailView.refreshTokenConfigured(),
                    gmailView.sharedClientConfigured(),
                    systemOAuthAppSettingsService.microsoftClientConfigured(),
                    gmailView.defaultRedirectUri(),
                    inboxBridgeConfig.microsoft().redirectUri(),
                    null,
                    null,
                    true,
                    InboxBridgeConfig.AuthMethod.OAUTH2.name(),
                    InboxBridgeConfig.OAuthProvider.GOOGLE.name(),
                    null,
                    null);
        }

        boolean passwordConfigured = config.passwordCiphertext != null && config.passwordNonce != null;
        boolean oauthConnected = InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(config.authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(config.oauthProvider)
                && microsoftOAuthService.destinationLinked(userId);

        return new UserMailDestinationView(
                config.provider,
                "IMAP_APPEND",
                passwordConfigured || oauthConnected,
                passwordConfigured,
                oauthConnected,
                userGmailConfigService.sharedGoogleClientConfigured(),
                systemOAuthAppSettingsService.microsoftClientConfigured(),
                userGmailConfigService.defaultRedirectUri(),
                inboxBridgeConfig.microsoft().redirectUri(),
                config.host,
                config.port,
                config.tls,
                config.authMethod,
                config.oauthProvider,
                config.username,
                config.folderName == null || config.folderName.isBlank() ? "INBOX" : config.folderName);
    }

    public Optional<MailDestinationTarget> resolveForUser(Long userId, String ownerUsername) {
        UserMailDestinationConfig config = repository.findByUserId(userId).orElse(null);
        if (config == null || PROVIDER_GMAIL.equals(config.provider)) {
            UserGmailConfigView gmailView = userGmailConfigService.viewForUser(userId)
                    .orElse(userGmailConfigService.defaultView(userId));
            Optional<GoogleOAuthService.GoogleOAuthProfile> profile = userGmailConfigService.googleProfileForUser(userId);
            if (profile.isEmpty()) {
                return Optional.empty();
            }
            GoogleOAuthService.GoogleOAuthProfile googleProfile = profile.get();
            return Optional.of(new GmailApiDestinationTarget(
                    googleProfile.subjectKey(),
                    userId,
                    ownerUsername,
                    PROVIDER_GMAIL,
                    gmailView.destinationUser(),
                    googleProfile.clientId(),
                    googleProfile.clientSecret(),
                    googleProfile.refreshToken(),
                    googleProfile.redirectUri(),
                    gmailView.createMissingLabels(),
                    gmailView.neverMarkSpam(),
                    gmailView.processForCalendar()));
        }

        if (!secretEncryptionService.isConfigured()) {
            return Optional.empty();
        }
        return Optional.of(new ImapAppendDestinationTarget(
                "user-destination:" + userId,
                userId,
                ownerUsername,
                config.provider,
                requireNonBlank(config.host, "Destination host"),
                config.port == null ? 993 : config.port,
                config.tls,
                InboxBridgeConfig.AuthMethod.valueOf(config.authMethod),
                InboxBridgeConfig.OAuthProvider.valueOf(config.oauthProvider),
                requireNonBlank(config.username, "Destination username"),
                decryptPassword(config),
                config.folderName == null || config.folderName.isBlank() ? "INBOX" : config.folderName));
    }

    public boolean isAnyDestinationConfigured(Long userId) {
        return repository.findByUserId(userId).isPresent() || userGmailConfigService.googleProfileForUser(userId).isPresent();
    }

    @Transactional
    public UserMailDestinationView update(AppUser user, UpdateUserMailDestinationRequest request) {
        String provider = normalizeProvider(request.provider());
        UserMailDestinationConfig config = repository.findByUserId(user.id).orElseGet(UserMailDestinationConfig::new);
        String nextAuthMethod = PROVIDER_GMAIL.equals(provider)
            ? InboxBridgeConfig.AuthMethod.OAUTH2.name()
            : PROVIDER_OUTLOOK.equals(provider)
                ? InboxBridgeConfig.AuthMethod.OAUTH2.name()
                : parseAuthMethod(request.authMethod()).name();
        String nextOAuthProvider = PROVIDER_GMAIL.equals(provider)
            ? InboxBridgeConfig.OAuthProvider.GOOGLE.name()
            : PROVIDER_OUTLOOK.equals(provider)
                ? InboxBridgeConfig.OAuthProvider.MICROSOFT.name()
                : parseOAuthProvider(request.oauthProvider()).name();
        unlinkReplacedOAuthDestination(user.id, config, provider, nextAuthMethod, nextOAuthProvider);
        config.userId = user.id;
        config.provider = provider;
        config.updatedAt = Instant.now();
        config.keyVersion = secretEncryptionService.isConfigured() ? secretEncryptionService.keyVersion() : config.keyVersion;

        if (PROVIDER_GMAIL.equals(provider)) {
            clearImapFields(config);
            repository.persist(config);
            return viewForUser(user.id);
        }

        config.host = requireNonBlank(request.host(), "Destination host");
        config.port = request.port() == null ? 993 : request.port();
        config.tls = request.tls() == null || request.tls();
        config.authMethod = nextAuthMethod;
        config.oauthProvider = nextOAuthProvider;
        config.folderName = blankToDefault(request.folder(), "INBOX");

        if (InboxBridgeConfig.AuthMethod.PASSWORD.name().equals(config.authMethod)) {
            config.username = requireNonBlank(request.username(), "Destination username");
            if (!secretEncryptionService.isConfigured()) {
                throw new IllegalStateException("Secure secret storage must be configured before storing destination mailbox passwords in the database.");
            }
            setPassword(config, request.password());
            if (config.passwordCiphertext == null || config.passwordNonce == null) {
                throw new IllegalArgumentException("Destination mailbox password is required");
            }
        } else {
            config.passwordCiphertext = null;
            config.passwordNonce = null;
            if (!InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(config.oauthProvider)) {
                throw new IllegalArgumentException("Only Microsoft OAuth2 is currently supported for IMAP destination mailboxes.");
            }
            config.username = blankToNull(request.username());
        }

        repository.persist(config);
        return viewForUser(user.id);
    }

    @Transactional
    public DestinationUnlinkResult unlinkForUser(Long userId) {
        UserMailDestinationConfig config = repository.findByUserId(userId).orElse(null);
        if (config == null || PROVIDER_GMAIL.equals(config.provider)) {
            UserGmailConfigService.GmailUnlinkResult result = userGmailConfigService.unlinkForUser(userId);
            return new DestinationUnlinkResult(result.providerRevocationAttempted(), result.providerRevoked());
        }
        if (InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(config.authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(config.oauthProvider)) {
            microsoftOAuthService.unlinkDestination(userId);
        }
        return new DestinationUnlinkResult(false, false);
    }

    private void clearImapFields(UserMailDestinationConfig config) {
        config.host = null;
        config.port = null;
        config.tls = true;
        config.authMethod = InboxBridgeConfig.AuthMethod.OAUTH2.name();
        config.oauthProvider = InboxBridgeConfig.OAuthProvider.GOOGLE.name();
        config.username = null;
        config.passwordCiphertext = null;
        config.passwordNonce = null;
        config.folderName = null;
    }

    private void unlinkReplacedOAuthDestination(
            Long userId,
            UserMailDestinationConfig existingConfig,
            String nextProvider,
            String nextAuthMethod,
            String nextOAuthProvider) {
        String currentLinkedProvider = linkedOauthProvider(userId, existingConfig);
        if (currentLinkedProvider == null) {
            return;
        }
        String requestedLinkedProvider = requestedOauthProvider(nextProvider, nextAuthMethod, nextOAuthProvider);
        if (Objects.equals(currentLinkedProvider, requestedLinkedProvider)) {
            return;
        }
        if (InboxBridgeConfig.OAuthProvider.GOOGLE.name().equals(currentLinkedProvider)) {
            userGmailConfigService.unlinkForUser(userId);
            return;
        }
        microsoftOAuthService.unlinkDestination(userId);
    }

    private String linkedOauthProvider(Long userId, UserMailDestinationConfig existingConfig) {
        if (existingConfig == null || PROVIDER_GMAIL.equals(existingConfig.provider)) {
            return userGmailConfigService.destinationLinked(userId)
                    ? InboxBridgeConfig.OAuthProvider.GOOGLE.name()
                    : null;
        }
        if (InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(existingConfig.authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(existingConfig.oauthProvider)
                && microsoftOAuthService.destinationLinked(userId)) {
            return InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        }
        return null;
    }

    private String requestedOauthProvider(String provider, String authMethod, String oauthProvider) {
        if (PROVIDER_GMAIL.equals(provider)) {
            return InboxBridgeConfig.OAuthProvider.GOOGLE.name();
        }
        if (InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(oauthProvider)) {
            return InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        }
        return null;
    }

    private void setPassword(UserMailDestinationConfig config, String password) {
        if (password == null || password.isBlank()) {
            return;
        }
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(password, "user-destination:" + config.userId + ":password");
        config.passwordCiphertext = encrypted.ciphertextBase64();
        config.passwordNonce = encrypted.nonceBase64();
    }

    private String decryptPassword(UserMailDestinationConfig config) {
        if (config.passwordCiphertext == null || config.passwordNonce == null) {
            return "";
        }
        return secretEncryptionService.decrypt(
                config.passwordCiphertext,
                config.passwordNonce,
                config.keyVersion,
                "user-destination:" + config.userId + ":password");
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_GMAIL;
        }
        String normalized = provider.trim().toUpperCase();
        return switch (normalized) {
            case PROVIDER_GMAIL, PROVIDER_OUTLOOK, PROVIDER_YAHOO, PROVIDER_PROTON_BRIDGE, PROVIDER_CUSTOM -> normalized;
            default -> throw new IllegalArgumentException("Unsupported destination provider: " + provider);
        };
    }

    private InboxBridgeConfig.AuthMethod parseAuthMethod(String authMethod) {
        if (authMethod == null || authMethod.isBlank()) {
            return InboxBridgeConfig.AuthMethod.PASSWORD;
        }
        return InboxBridgeConfig.AuthMethod.valueOf(authMethod.trim().toUpperCase());
    }

    private InboxBridgeConfig.OAuthProvider parseOAuthProvider(String oauthProvider) {
        if (oauthProvider == null || oauthProvider.isBlank()) {
            return InboxBridgeConfig.OAuthProvider.NONE;
        }
        return InboxBridgeConfig.OAuthProvider.valueOf(oauthProvider.trim().toUpperCase());
    }

    private String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record DestinationUnlinkResult(
            boolean providerRevocationAttempted,
            boolean providerRevoked) {
    }
}