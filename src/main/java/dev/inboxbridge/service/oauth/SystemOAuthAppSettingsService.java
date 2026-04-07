package dev.inboxbridge.service.oauth;

import java.time.Instant;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.SystemOAuthAppSettingsView;
import dev.inboxbridge.dto.UpdateSystemOAuthAppSettingsRequest;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.service.SecretEncryptionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SystemOAuthAppSettingsService {

    @Inject
    InboxBridgeConfig config;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    SystemOAuthAppSettingsRepository repository;

    public void setConfig(InboxBridgeConfig config) {
        this.config = config;
    }

    public void setSecretEncryptionService(SecretEncryptionService secretEncryptionService) {
        this.secretEncryptionService = secretEncryptionService;
    }

    public void setRepository(SystemOAuthAppSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SystemOAuthAppSettingsView view() {
        SystemOAuthAppSettings settings = repository.findSingleton().orElse(null);
        return new SystemOAuthAppSettingsView(
                effectiveMultiUserEnabled(settings),
                settings == null ? null : settings.multiUserEnabledOverride,
                effectiveGoogleDestinationUser(settings),
                effectiveGoogleRedirectUri(settings),
                effectiveGoogleClientId(settings),
                googleClientSecretConfigured(settings),
                googleRefreshTokenConfigured(settings),
                effectiveMicrosoftClientId(settings),
                config.microsoft().redirectUri(),
                microsoftClientSecretConfigured(settings),
                secretEncryptionService.isConfigured());
    }

    @Transactional
    public SystemOAuthAppSettingsView update(UpdateSystemOAuthAppSettingsRequest request) {
        if (!secretEncryptionService.isConfigured() && containsSecretUpdates(request)) {
            throw new IllegalStateException("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
        }
        SystemOAuthAppSettings settings = repository.findSingleton().orElseGet(SystemOAuthAppSettings::new);
        if (settings.id == null) {
            settings.id = 1L;
        }
        settings.keyVersion = currentKeyVersion(settings);
        settings.multiUserEnabledOverride = request.multiUserEnabledOverride();
        settings.googleDestinationUser = normalizePlaintext(request.googleDestinationUser());
        settings.googleRedirectUri = normalizePlaintext(request.googleRedirectUri());
        applySecret(request.googleClientId(), settings, "google-client-id");
        applySecret(request.googleClientSecret(), settings, "google-client-secret");
        applySecret(request.googleRefreshToken(), settings, "google-refresh-token");
        applySecret(request.microsoftClientId(), settings, "microsoft-client-id");
        applySecret(request.microsoftClientSecret(), settings, "microsoft-client-secret");
        settings.updatedAt = Instant.now();
        repository.persist(settings);
        return view();
    }

    @Transactional
    public String googleClientId() {
        return effectiveGoogleClientId(repository.findSingleton().orElse(null));
    }

    @Transactional
    public String googleClientSecret() {
        return effectiveGoogleClientSecret(repository.findSingleton().orElse(null));
    }

    @Transactional
    public String googleDestinationUser() {
        return effectiveGoogleDestinationUser(repository.findSingleton().orElse(null));
    }

    @Transactional
    public String googleRedirectUri() {
        return effectiveGoogleRedirectUri(repository.findSingleton().orElse(null));
    }

    @Transactional
    public String googleRefreshToken() {
        return effectiveGoogleRefreshToken(repository.findSingleton().orElse(null));
    }

    @Transactional
    public String microsoftClientId() {
        return effectiveMicrosoftClientId(repository.findSingleton().orElse(null));
    }

    @Transactional
    public String microsoftClientSecret() {
        return effectiveMicrosoftClientSecret(repository.findSingleton().orElse(null));
    }

    public boolean googleClientConfigured() {
        return isConfiguredValue(googleClientId()) && isConfiguredValue(googleClientSecret());
    }

    public boolean microsoftClientConfigured() {
        return isConfiguredValue(microsoftClientId()) && isConfiguredValue(microsoftClientSecret());
    }

    @Transactional
    public Boolean multiUserEnabledOverride() {
        return repository.findSingleton()
                .map(settings -> settings.multiUserEnabledOverride)
                .orElse(null);
    }

    @Transactional
    public boolean effectiveMultiUserEnabled() {
        return effectiveMultiUserEnabled(repository.findSingleton().orElse(null));
    }

    @Transactional
    public void setMultiUserEnabledOverride(Boolean enabled) {
        SystemOAuthAppSettings settings = repository.findSingleton().orElseGet(SystemOAuthAppSettings::new);
        if (settings.id == null) {
            settings.id = 1L;
        }
        settings.multiUserEnabledOverride = enabled;
        settings.updatedAt = Instant.now();
        settings.keyVersion = currentKeyVersion(settings);
        repository.persist(settings);
    }

    private void applySecret(String value, SystemOAuthAppSettings settings, String secretName) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (secretName.equals("google-client-id")) {
            if (normalized.isBlank()) {
                settings.googleClientIdCiphertext = null;
                settings.googleClientIdNonce = null;
                return;
            }
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(normalized, "system-oauth:" + secretName);
            settings.googleClientIdCiphertext = encrypted.ciphertextBase64();
            settings.googleClientIdNonce = encrypted.nonceBase64();
            return;
        }
        if (secretName.equals("google-client-secret")) {
            if (normalized.isBlank()) {
                settings.googleClientSecretCiphertext = null;
                settings.googleClientSecretNonce = null;
                return;
            }
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(normalized, "system-oauth:" + secretName);
            settings.googleClientSecretCiphertext = encrypted.ciphertextBase64();
            settings.googleClientSecretNonce = encrypted.nonceBase64();
            return;
        }
        if (secretName.equals("google-refresh-token")) {
            if (normalized.isBlank()) {
                settings.googleRefreshTokenCiphertext = null;
                settings.googleRefreshTokenNonce = null;
                return;
            }
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(normalized, "system-oauth:" + secretName);
            settings.googleRefreshTokenCiphertext = encrypted.ciphertextBase64();
            settings.googleRefreshTokenNonce = encrypted.nonceBase64();
            return;
        }
        if (secretName.equals("microsoft-client-id")) {
            if (normalized.isBlank()) {
                settings.microsoftClientIdCiphertext = null;
                settings.microsoftClientIdNonce = null;
                return;
            }
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(normalized, "system-oauth:" + secretName);
            settings.microsoftClientIdCiphertext = encrypted.ciphertextBase64();
            settings.microsoftClientIdNonce = encrypted.nonceBase64();
            return;
        }
        if (normalized.isBlank()) {
            settings.microsoftClientSecretCiphertext = null;
            settings.microsoftClientSecretNonce = null;
            return;
        }
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(normalized, "system-oauth:" + secretName);
        settings.microsoftClientSecretCiphertext = encrypted.ciphertextBase64();
        settings.microsoftClientSecretNonce = encrypted.nonceBase64();
    }

    private String effectiveGoogleClientId(SystemOAuthAppSettings settings) {
        String stored = decrypt(settings == null ? null : settings.googleClientIdCiphertext, settings == null ? null : settings.googleClientIdNonce, settings == null ? null : settings.keyVersion, "system-oauth:google-client-id");
        return !stored.isBlank() ? stored : config.gmail().clientId();
    }

    private boolean effectiveMultiUserEnabled(SystemOAuthAppSettings settings) {
        return settings != null && settings.multiUserEnabledOverride != null
                ? settings.multiUserEnabledOverride
                : config.multiUserEnabled();
    }

    private String effectiveGoogleClientSecret(SystemOAuthAppSettings settings) {
        String stored = decrypt(settings == null ? null : settings.googleClientSecretCiphertext, settings == null ? null : settings.googleClientSecretNonce, settings == null ? null : settings.keyVersion, "system-oauth:google-client-secret");
        return !stored.isBlank() ? stored : config.gmail().clientSecret();
    }

    private String effectiveGoogleDestinationUser(SystemOAuthAppSettings settings) {
        String stored = settings == null ? "" : normalizePlaintext(settings.googleDestinationUser);
        return !stored.isBlank() ? stored : normalizePlaintext(config.gmail().destinationUser());
    }

    private String effectiveGoogleRedirectUri(SystemOAuthAppSettings settings) {
        String stored = settings == null ? "" : normalizePlaintext(settings.googleRedirectUri);
        return !stored.isBlank() ? stored : normalizePlaintext(config.gmail().redirectUri());
    }

    private String effectiveGoogleRefreshToken(SystemOAuthAppSettings settings) {
        String stored = decrypt(settings == null ? null : settings.googleRefreshTokenCiphertext, settings == null ? null : settings.googleRefreshTokenNonce, settings == null ? null : settings.keyVersion, "system-oauth:google-refresh-token");
        return !stored.isBlank() ? stored : config.gmail().refreshToken();
    }

    private String effectiveMicrosoftClientId(SystemOAuthAppSettings settings) {
        String stored = decrypt(settings == null ? null : settings.microsoftClientIdCiphertext, settings == null ? null : settings.microsoftClientIdNonce, settings == null ? null : settings.keyVersion, "system-oauth:microsoft-client-id");
        return !stored.isBlank() ? stored : config.microsoft().clientId();
    }

    private String effectiveMicrosoftClientSecret(SystemOAuthAppSettings settings) {
        String stored = decrypt(settings == null ? null : settings.microsoftClientSecretCiphertext, settings == null ? null : settings.microsoftClientSecretNonce, settings == null ? null : settings.keyVersion, "system-oauth:microsoft-client-secret");
        return !stored.isBlank() ? stored : config.microsoft().clientSecret();
    }

    private boolean googleClientSecretConfigured(SystemOAuthAppSettings settings) {
        return isConfiguredValue(effectiveGoogleClientSecret(settings));
    }

    private boolean googleRefreshTokenConfigured(SystemOAuthAppSettings settings) {
        return isConfiguredValue(effectiveGoogleRefreshToken(settings));
    }

    private boolean microsoftClientSecretConfigured(SystemOAuthAppSettings settings) {
        return isConfiguredValue(effectiveMicrosoftClientSecret(settings));
    }

    private String decrypt(String ciphertext, String nonce, String keyVersion, String context) {
        if (ciphertext == null || nonce == null || keyVersion == null || !secretEncryptionService.isConfigured()) {
            return "";
        }
        return secretEncryptionService.decrypt(ciphertext, nonce, keyVersion, context);
    }

    private boolean isConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"replace-me".equals(value.trim());
    }

    private boolean containsSecretUpdates(UpdateSystemOAuthAppSettingsRequest request) {
        return isProvided(request.googleClientId())
                || isProvided(request.googleClientSecret())
                || isProvided(request.googleRefreshToken())
                || isProvided(request.microsoftClientId())
                || isProvided(request.microsoftClientSecret());
    }

    private String currentKeyVersion(SystemOAuthAppSettings settings) {
        if (secretEncryptionService.isConfigured()) {
            return secretEncryptionService.keyVersion();
        }
        return settings == null || settings.keyVersion == null || settings.keyVersion.isBlank()
                ? "plain"
                : settings.keyVersion;
    }

    private String normalizePlaintext(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isProvided(String value) {
        return value != null && !value.isBlank();
    }
}
