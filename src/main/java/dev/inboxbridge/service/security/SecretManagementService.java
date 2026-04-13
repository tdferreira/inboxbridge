package dev.inboxbridge.service.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.dto.SecretManagementKeyUsageView;
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
        if (!localSecretKeyProvider.isConfigured()) {
            return new SecretManagementStatusView(
                    false,
                    "UNCONFIGURED",
                    localSecretKeyProvider.providerId(),
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

        SecretKeyMaterial activeKey = localSecretKeyProvider.activeKey();
        List<String> configuredLegacyKeyIds = localSecretKeyProvider.configuredLegacyKeyIds().stream()
                .sorted()
                .toList();
        Map<String, UsageAccumulator> usage = new LinkedHashMap<>();
        collectUsage(usage);

        List<SecretManagementKeyUsageView> keyUsage = usage.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, UsageAccumulator> entry) -> !entry.getKey().equals(activeKey.storedKeyVersion()))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new SecretManagementKeyUsageView(
                        entry.getKey(),
                        entry.getValue().recordCount,
                        String.join(", ", entry.getValue().areas),
                        activeKey.storedKeyVersion().equals(entry.getKey()),
                        localSecretKeyProvider.resolveKey(entry.getKey()).isPresent()))
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
                "LOCAL",
                activeKey.providerId(),
                activeKey.storedKeyVersion(),
                activeKey.keyId(),
                configuredLegacyKeyIds,
                protectedRecordCount,
                activeKeyRecordCount,
                nonActiveKeyRecordCount,
                unavailableKeyRecordCount,
                nonActiveKeyRecordCount == 0 && unavailableKeyRecordCount == 0,
                keyUsage);
    }

    public void setLocalSecretKeyProvider(LocalSecretKeyProvider localSecretKeyProvider) {
        this.localSecretKeyProvider = localSecretKeyProvider;
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

    private static final class UsageAccumulator {
        private long recordCount;
        private final LinkedHashSet<String> areas = new LinkedHashSet<>();

        private void add(String area) {
            recordCount++;
            areas.add(area);
        }
    }
}
