package dev.inboxbridge.service.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.dto.SecretManagementKeyUsageView;
import dev.inboxbridge.dto.SecretReencryptionAreaResultView;
import dev.inboxbridge.dto.SecretReencryptionResultView;
import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Reports the active secret-management mode together with the stored key
 * versions still referenced by encrypted records, so operators can rotate keys
 * safely and know when legacy keys can be retired.
 */
@ApplicationScoped
public class SecretManagementService {

    @Inject
    LocalSecretKeyProvider localSecretKeyProvider;

    @Inject
    SecretProviderResolver secretProviderResolver;

    @Inject
    OAuthCredentialRepository oAuthCredentialRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    SystemOAuthAppSettingsRepository systemOAuthAppSettingsRepository;

    @Inject
    SystemAuthSecuritySettingRepository systemAuthSecuritySettingRepository;

    public SecretManagementStatusView status() {
        SecretProviderHealth providerHealth = providerResolver().health();
        if (!providerHealth.writable()) {
            return new SecretManagementStatusView(
                    false,
                    providerHealth.mode().name(),
                    providerHealth.providerId(),
                    providerHealth.healthy(),
                    providerHealth.writable(),
                    providerHealth.statusMessage(),
                    null,
                    null,
                    List.of(),
                    0,
                    0,
                    0,
                    0,
                    false,
                    List.of());
        }

        String activeKeyVersion = providerResolver().activeKeyVersion();
        String activeKeyId = providerResolver().activeKeyId();
        List<String> configuredLegacyKeyIds = providerHealth.mode() == SecretProviderMode.LOCAL
                ? localSecretKeyProvider.configuredLegacyKeyIds().stream()
                        .sorted()
                        .toList()
                : List.of();
        Map<String, UsageAccumulator> usage = new LinkedHashMap<>();
        collectUsage(usage);

        List<SecretManagementKeyUsageView> keyUsage = usage.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, UsageAccumulator> entry) -> !entry.getKey().equals(activeKeyVersion))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new SecretManagementKeyUsageView(
                        entry.getKey(),
                        entry.getValue().recordCount,
                        String.join(", ", entry.getValue().areas),
                        activeKeyVersion.equals(entry.getKey()),
                        providerResolver().isStoredKeyVersionAvailable(entry.getKey())))
                .toList();

        long protectedRecordCount = keyUsage.stream().mapToLong(SecretManagementKeyUsageView::recordCount).sum();
        long activeKeyRecordCount = keyUsage.stream()
                .filter(SecretManagementKeyUsageView::active)
                .mapToLong(SecretManagementKeyUsageView::recordCount)
                .sum();
        long unavailableKeyRecordCount = keyUsage.stream()
                .filter(usageView -> !usageView.availableForDecryption())
                .mapToLong(SecretManagementKeyUsageView::recordCount)
                .sum();
        long nonActiveKeyRecordCount = protectedRecordCount - activeKeyRecordCount;

        return new SecretManagementStatusView(
                true,
                providerHealth.mode().name(),
                providerHealth.providerId(),
                providerHealth.healthy(),
                providerHealth.writable(),
                providerHealth.statusMessage(),
                activeKeyVersion,
                activeKeyId,
                configuredLegacyKeyIds,
                protectedRecordCount,
                activeKeyRecordCount,
                nonActiveKeyRecordCount,
                unavailableKeyRecordCount,
                nonActiveKeyRecordCount == 0 && unavailableKeyRecordCount == 0,
                keyUsage);
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets() {
        if (!providerResolver().isWritable()) {
            throw new IllegalStateException(providerResolver().health().statusMessage());
        }

        List<SecretReencryptionAreaResultView> areas = new ArrayList<>();
        areas.add(reencryptOAuthCredentials());
        areas.add(reencryptSourceMailboxes());
        areas.add(reencryptDestinationMailboxes());
        areas.add(reencryptUserGmailConfig());
        areas.add(reencryptSystemOAuthSettings());
        areas.add(reencryptAuthSecuritySettings());

        int totalRecordsUpdated = areas.stream().mapToInt(SecretReencryptionAreaResultView::recordsUpdated).sum();
        int totalSecretValuesReencrypted = areas.stream().mapToInt(SecretReencryptionAreaResultView::secretValuesReencrypted).sum();
        return new SecretReencryptionResultView(
                secretEncryptionService.keyVersion(),
                totalRecordsUpdated,
                totalSecretValuesReencrypted,
                areas);
    }

    public void setLocalSecretKeyProvider(LocalSecretKeyProvider localSecretKeyProvider) {
        this.localSecretKeyProvider = localSecretKeyProvider;
    }

    public void setSecretEncryptionService(SecretEncryptionService secretEncryptionService) {
        this.secretEncryptionService = secretEncryptionService;
    }

    public void setSecretProviderResolver(SecretProviderResolver secretProviderResolver) {
        this.secretProviderResolver = secretProviderResolver;
    }

    public void setOAuthCredentialRepository(OAuthCredentialRepository oAuthCredentialRepository) {
        this.oAuthCredentialRepository = oAuthCredentialRepository;
    }

    public void setUserEmailAccountRepository(UserEmailAccountRepository userEmailAccountRepository) {
        this.userEmailAccountRepository = userEmailAccountRepository;
    }

    public void setUserMailDestinationConfigRepository(UserMailDestinationConfigRepository userMailDestinationConfigRepository) {
        this.userMailDestinationConfigRepository = userMailDestinationConfigRepository;
    }

    public void setUserGmailConfigRepository(UserGmailConfigRepository userGmailConfigRepository) {
        this.userGmailConfigRepository = userGmailConfigRepository;
    }

    public void setSystemOAuthAppSettingsRepository(SystemOAuthAppSettingsRepository systemOAuthAppSettingsRepository) {
        this.systemOAuthAppSettingsRepository = systemOAuthAppSettingsRepository;
    }

    public void setSystemAuthSecuritySettingRepository(SystemAuthSecuritySettingRepository systemAuthSecuritySettingRepository) {
        this.systemAuthSecuritySettingRepository = systemAuthSecuritySettingRepository;
    }

    private void collectUsage(Map<String, UsageAccumulator> usage) {
        for (OAuthCredential credential : oAuthCredentialRepository.listAll()) {
            if (hasAny(credential.refreshTokenCiphertext, credential.accessTokenCiphertext)) {
                track(usage, credential.keyVersion, "oauth-credentials");
            }
        }
        for (UserEmailAccount account : userEmailAccountRepository.listAll()) {
            if (hasAny(account.passwordCiphertext, account.oauthRefreshTokenCiphertext)) {
                track(usage, account.keyVersion, "source-mailboxes");
            }
        }
        for (UserMailDestinationConfig config : userMailDestinationConfigRepository.listAll()) {
            if (hasAny(config.passwordCiphertext)) {
                track(usage, config.keyVersion, "destination-mailboxes");
            }
        }
        for (UserGmailConfig config : userGmailConfigRepository.listAll()) {
            if (hasAny(config.clientIdCiphertext, config.clientSecretCiphertext, config.refreshTokenCiphertext)) {
                track(usage, config.keyVersion, "gmail-user-config");
            }
        }
        SystemOAuthAppSettings systemOAuth = systemOAuthAppSettingsRepository.findSingleton().orElse(null);
        if (systemOAuth != null
                && hasAny(
                        systemOAuth.googleClientIdCiphertext,
                        systemOAuth.googleClientSecretCiphertext,
                        systemOAuth.googleRefreshTokenCiphertext,
                        systemOAuth.microsoftClientIdCiphertext,
                        systemOAuth.microsoftClientSecretCiphertext)) {
            track(usage, systemOAuth.keyVersion, "system-oauth");
        }
        SystemAuthSecuritySetting authSecurity = systemAuthSecuritySettingRepository.findSingleton().orElse(null);
        if (authSecurity != null
                && hasAny(
                        authSecurity.registrationTurnstileSecretCiphertext,
                        authSecurity.registrationHcaptchaSecretCiphertext,
                        authSecurity.geoIpIpinfoTokenCiphertext)) {
            track(usage, authSecurity.keyVersion, "auth-security");
        }
    }

    private void track(Map<String, UsageAccumulator> usage, String keyVersion, String area) {
        if (keyVersion == null || keyVersion.isBlank()) {
            return;
        }
        usage.computeIfAbsent(keyVersion, ignored -> new UsageAccumulator())
                .add(area);
    }

    private boolean hasAny(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    @Inject
    SecretEncryptionService secretEncryptionService;

    private SecretReencryptionAreaResultView reencryptOAuthCredentials() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        for (OAuthCredential credential : oAuthCredentialRepository.listAll()) {
            int updatedFields = 0;
            updatedFields += rewriteSecret(
                    credential.refreshTokenCiphertext,
                    credential.refreshTokenNonce,
                    credential.keyVersion,
                    value -> {
                        credential.refreshTokenCiphertext = value.ciphertextBase64();
                        credential.refreshTokenNonce = value.nonceBase64();
                    },
                    credential.provider + ":" + credential.subjectKey + ":refresh");
            updatedFields += rewriteSecret(
                    credential.accessTokenCiphertext,
                    credential.accessTokenNonce,
                    credential.keyVersion,
                    value -> {
                        credential.accessTokenCiphertext = value.ciphertextBase64();
                        credential.accessTokenNonce = value.nonceBase64();
                    },
                    credential.provider + ":" + credential.subjectKey + ":access");
            if (updatedFields > 0) {
                credential.keyVersion = secretEncryptionService.keyVersion();
                credential.updatedAt = Instant.now();
                oAuthCredentialRepository.persist(credential);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields;
            }
        }
        return new SecretReencryptionAreaResultView("oauth-credentials", recordsUpdated, secretValuesReencrypted);
    }

    private SecretReencryptionAreaResultView reencryptSourceMailboxes() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        for (UserEmailAccount account : userEmailAccountRepository.listAll()) {
            int updatedFields = 0;
            updatedFields += rewriteSecret(
                    account.passwordCiphertext,
                    account.passwordNonce,
                    account.keyVersion,
                    value -> {
                        account.passwordCiphertext = value.ciphertextBase64();
                        account.passwordNonce = value.nonceBase64();
                    },
                    "user-bridge:" + account.userId + ":" + account.emailAccountId + ":password");
            updatedFields += rewriteSecret(
                    account.oauthRefreshTokenCiphertext,
                    account.oauthRefreshTokenNonce,
                    account.keyVersion,
                    value -> {
                        account.oauthRefreshTokenCiphertext = value.ciphertextBase64();
                        account.oauthRefreshTokenNonce = value.nonceBase64();
                    },
                    "user-bridge:" + account.userId + ":" + account.emailAccountId + ":oauth-refresh-token");
            if (updatedFields > 0) {
                account.keyVersion = secretEncryptionService.keyVersion();
                account.updatedAt = Instant.now();
                userEmailAccountRepository.persist(account);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields;
            }
        }
        return new SecretReencryptionAreaResultView("source-mailboxes", recordsUpdated, secretValuesReencrypted);
    }

    private SecretReencryptionAreaResultView reencryptDestinationMailboxes() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        for (UserMailDestinationConfig config : userMailDestinationConfigRepository.listAll()) {
            int updatedFields = rewriteSecret(
                    config.passwordCiphertext,
                    config.passwordNonce,
                    config.keyVersion,
                    value -> {
                        config.passwordCiphertext = value.ciphertextBase64();
                        config.passwordNonce = value.nonceBase64();
                    },
                    "user-destination:" + config.userId + ":password");
            if (updatedFields > 0) {
                config.keyVersion = secretEncryptionService.keyVersion();
                config.updatedAt = Instant.now();
                userMailDestinationConfigRepository.persist(config);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields;
            }
        }
        return new SecretReencryptionAreaResultView("destination-mailboxes", recordsUpdated, secretValuesReencrypted);
    }

    private SecretReencryptionAreaResultView reencryptUserGmailConfig() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        for (UserGmailConfig config : userGmailConfigRepository.listAll()) {
            int updatedFields = 0;
            updatedFields += rewriteSecret(
                    config.clientIdCiphertext,
                    config.clientIdNonce,
                    config.keyVersion,
                    value -> {
                        config.clientIdCiphertext = value.ciphertextBase64();
                        config.clientIdNonce = value.nonceBase64();
                    },
                    "user-gmail:" + config.userId + ":client-id");
            updatedFields += rewriteSecret(
                    config.clientSecretCiphertext,
                    config.clientSecretNonce,
                    config.keyVersion,
                    value -> {
                        config.clientSecretCiphertext = value.ciphertextBase64();
                        config.clientSecretNonce = value.nonceBase64();
                    },
                    "user-gmail:" + config.userId + ":client-secret");
            updatedFields += rewriteSecret(
                    config.refreshTokenCiphertext,
                    config.refreshTokenNonce,
                    config.keyVersion,
                    value -> {
                        config.refreshTokenCiphertext = value.ciphertextBase64();
                        config.refreshTokenNonce = value.nonceBase64();
                    },
                    "user-gmail:" + config.userId + ":refresh-token");
            if (updatedFields > 0) {
                config.keyVersion = secretEncryptionService.keyVersion();
                config.updatedAt = Instant.now();
                userGmailConfigRepository.persist(config);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields;
            }
        }
        return new SecretReencryptionAreaResultView("gmail-user-config", recordsUpdated, secretValuesReencrypted);
    }

    private SecretReencryptionAreaResultView reencryptSystemOAuthSettings() {
        SystemOAuthAppSettings settings = systemOAuthAppSettingsRepository.findSingleton().orElse(null);
        if (settings == null) {
            return new SecretReencryptionAreaResultView("system-oauth", 0, 0);
        }
        int updatedFields = 0;
        updatedFields += rewriteSecret(
                settings.googleClientIdCiphertext,
                settings.googleClientIdNonce,
                settings.keyVersion,
                value -> {
                    settings.googleClientIdCiphertext = value.ciphertextBase64();
                    settings.googleClientIdNonce = value.nonceBase64();
                },
                "system-oauth:google-client-id");
        updatedFields += rewriteSecret(
                settings.googleClientSecretCiphertext,
                settings.googleClientSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.googleClientSecretCiphertext = value.ciphertextBase64();
                    settings.googleClientSecretNonce = value.nonceBase64();
                },
                "system-oauth:google-client-secret");
        updatedFields += rewriteSecret(
                settings.googleRefreshTokenCiphertext,
                settings.googleRefreshTokenNonce,
                settings.keyVersion,
                value -> {
                    settings.googleRefreshTokenCiphertext = value.ciphertextBase64();
                    settings.googleRefreshTokenNonce = value.nonceBase64();
                },
                "system-oauth:google-refresh-token");
        updatedFields += rewriteSecret(
                settings.microsoftClientIdCiphertext,
                settings.microsoftClientIdNonce,
                settings.keyVersion,
                value -> {
                    settings.microsoftClientIdCiphertext = value.ciphertextBase64();
                    settings.microsoftClientIdNonce = value.nonceBase64();
                },
                "system-oauth:microsoft-client-id");
        updatedFields += rewriteSecret(
                settings.microsoftClientSecretCiphertext,
                settings.microsoftClientSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.microsoftClientSecretCiphertext = value.ciphertextBase64();
                    settings.microsoftClientSecretNonce = value.nonceBase64();
                },
                "system-oauth:microsoft-client-secret");
        if (updatedFields > 0) {
            settings.keyVersion = secretEncryptionService.keyVersion();
            settings.updatedAt = Instant.now();
            systemOAuthAppSettingsRepository.persist(settings);
            return new SecretReencryptionAreaResultView("system-oauth", 1, updatedFields);
        }
        return new SecretReencryptionAreaResultView("system-oauth", 0, 0);
    }

    private SecretReencryptionAreaResultView reencryptAuthSecuritySettings() {
        SystemAuthSecuritySetting settings = systemAuthSecuritySettingRepository.findSingleton().orElse(null);
        if (settings == null) {
            return new SecretReencryptionAreaResultView("auth-security", 0, 0);
        }
        int updatedFields = 0;
        updatedFields += rewriteSecret(
                settings.registrationTurnstileSecretCiphertext,
                settings.registrationTurnstileSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.registrationTurnstileSecretCiphertext = value.ciphertextBase64();
                    settings.registrationTurnstileSecretNonce = value.nonceBase64();
                },
                "system-auth-security:registration-turnstile-secret");
        updatedFields += rewriteSecret(
                settings.registrationHcaptchaSecretCiphertext,
                settings.registrationHcaptchaSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.registrationHcaptchaSecretCiphertext = value.ciphertextBase64();
                    settings.registrationHcaptchaSecretNonce = value.nonceBase64();
                },
                "system-auth-security:registration-hcaptcha-secret");
        updatedFields += rewriteSecret(
                settings.geoIpIpinfoTokenCiphertext,
                settings.geoIpIpinfoTokenNonce,
                settings.keyVersion,
                value -> {
                    settings.geoIpIpinfoTokenCiphertext = value.ciphertextBase64();
                    settings.geoIpIpinfoTokenNonce = value.nonceBase64();
                },
                "system-auth-security:geo-ip-ipinfo-token");
        if (updatedFields > 0) {
            settings.keyVersion = secretEncryptionService.keyVersion();
            settings.updatedAt = Instant.now();
            systemAuthSecuritySettingRepository.persist(settings);
            return new SecretReencryptionAreaResultView("auth-security", 1, updatedFields);
        }
        return new SecretReencryptionAreaResultView("auth-security", 0, 0);
    }

    private int rewriteSecret(
            String ciphertext,
            String nonce,
            String keyVersion,
            java.util.function.Consumer<SecretEncryptionService.EncryptedValue> saveEncrypted,
            String context) {
        if (ciphertext == null || ciphertext.isBlank() || nonce == null || nonce.isBlank() || keyVersion == null || keyVersion.isBlank()) {
            return 0;
        }
        if (secretEncryptionService.keyVersion().equals(keyVersion)) {
            return 0;
        }
        String plaintext = secretEncryptionService.decrypt(ciphertext, nonce, keyVersion, context);
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(plaintext, context);
        saveEncrypted.accept(encrypted);
        return 1;
    }

    private static final class UsageAccumulator {
        private long recordCount;
        private final LinkedHashSet<String> areas = new LinkedHashSet<>();

        private void add(String area) {
            recordCount++;
            areas.add(area);
        }
    }

    private SecretProviderResolver providerResolver() {
        if (secretProviderResolver == null) {
            SecretProviderResolver resolver = new SecretProviderResolver();
            resolver.setLocalSecretKeyProvider(localSecretKeyProvider);
            secretProviderResolver = resolver;
        }
        return secretProviderResolver;
    }
}
