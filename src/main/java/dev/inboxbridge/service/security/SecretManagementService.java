package dev.inboxbridge.service.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.config.SecretManagementPolicyConfig;
import dev.inboxbridge.dto.SecretManagementKeyUsageView;
import dev.inboxbridge.dto.SecretReencryptionFollowUpView;
import dev.inboxbridge.dto.SecretReencryptionRequest;
import dev.inboxbridge.dto.SecretReencryptionAreaResultView;
import dev.inboxbridge.dto.SecretReencryptionRequirementView;
import dev.inboxbridge.dto.SecretReencryptionResultView;
import dev.inboxbridge.dto.SecretReencryptionRequestStatusView;
import dev.inboxbridge.dto.SecretReencryptionVerificationView;
import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequest;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequestRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.mail.EnvSourceService;
import dev.inboxbridge.service.oauth.OAuthCredentialService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.remote.RemoteSessionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.scheduler.Scheduled;

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

    @Inject
    ExtensionSessionService extensionSessionService;

    @Inject
    RemoteSessionService remoteSessionService;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    SecretManagementPolicyConfig secretManagementPolicyConfig;

    @Inject
    SystemSecretReencryptionRequestRepository systemSecretReencryptionRequestRepository;

    public SecretManagementStatusView status() {
        SecretProviderHealth providerHealth = providerResolver().health();
        SystemSecretReencryptionRequest requestState = currentReencryptionRequest();
        if (!providerHealth.writable()) {
            List<SecretReencryptionRequirementView> requirements = buildRequirements(
                    false,
                    providerHealth,
                    null,
                    0,
                    requestState);
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
                    envManagedMailboxSecretsAllowed(),
                    configuredEnvManagedSourceCount(),
                    envManagedGoogleRefreshTokenConfigured(),
                    false,
                    List.of(),
                    false,
                    requirements,
                    toRequestStatusView(requestState),
                    reencryptionCooldown().toString(),
                    allowImmediateReencryptOverride());
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
        List<SecretReencryptionRequirementView> requirements = buildRequirements(
                true,
                providerHealth,
                activeKeyVersion,
                unavailableKeyRecordCount,
                requestState);
        boolean reencryptionReady = requirements.stream()
                .filter(SecretReencryptionRequirementView::blocking)
                .allMatch(SecretReencryptionRequirementView::satisfied);

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
                envManagedMailboxSecretsAllowed(),
                configuredEnvManagedSourceCount(),
                envManagedGoogleRefreshTokenConfigured(),
                nonActiveKeyRecordCount == 0 && unavailableKeyRecordCount == 0,
                keyUsage,
                reencryptionReady,
                requirements,
                toRequestStatusView(requestState),
                reencryptionCooldown().toString(),
                allowImmediateReencryptOverride());
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets() {
        return reencryptAllStoredSecrets(null, new SecretReencryptionRequest(false, false, false, false));
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets(SecretReencryptionRequest request) {
        return reencryptAllStoredSecrets(null, request);
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets(AppUser actor, SecretReencryptionRequest request) {
        SecretReencryptionRequest effectiveRequest = effectiveRequest(request);
        ensureNoPendingRequest();
        validateReencryptionReadiness();
        if (effectiveRequest.immediateExecutionOverride() && !allowImmediateReencryptOverride()) {
            throw new IllegalStateException(
                    "Immediate secret re-encryption override is disabled by server policy. Wait for the configured cooldown window or enable the testing override on the server first.");
        }
        Duration cooldown = reencryptionCooldown();
        Instant now = Instant.now();
        if (!effectiveRequest.immediateExecutionOverride() && !cooldown.isZero() && !cooldown.isNegative()) {
            Instant executeAfter = now.plus(cooldown);
            SystemSecretReencryptionRequest requestState = upsertRequestState(actor, effectiveRequest, now, executeAfter);
            requestState.status = RequestStatus.PENDING.name();
            requestState.lastErrorMessage = null;
            requestState.lastResultMessage = "Secret re-encryption is queued and will execute after the cooldown window.";
            requestState.lastVerificationPassed = null;
            persistRequestState(requestState);
            return new SecretReencryptionResultView(
                    RequestStatus.SCHEDULED.name(),
                    "Secret re-encryption was scheduled after the configured cooldown window.",
                    executeAfter,
                    providerResolver().activeKeyVersion(),
                    0,
                    0,
                    List.of(),
                    new SecretReencryptionFollowUpView(0, 0, 0),
                    buildVerification(status()));
        }
        return executeReencryption(actor, effectiveRequest, now, true);
    }

    @Scheduled(every = "1m")
    @Transactional
    void executeDueReencryptionRequests() {
        executeDueReencryptionRequestsAt(Instant.now());
    }

    void executeDueReencryptionRequestsAt(Instant now) {
        SystemSecretReencryptionRequest requestState = currentReencryptionRequest();
        if (requestState == null || !RequestStatus.PENDING.name().equals(requestState.status) || requestState.executeAfter == null) {
            return;
        }
        if (requestState.executeAfter.isAfter(now)) {
            return;
        }
        executeReencryption(null, toRequest(requestState), now, false);
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

    public void setExtensionSessionService(ExtensionSessionService extensionSessionService) {
        this.extensionSessionService = extensionSessionService;
    }

    public void setRemoteSessionService(RemoteSessionService remoteSessionService) {
        this.remoteSessionService = remoteSessionService;
    }

    public void setOAuthCredentialService(OAuthCredentialService oAuthCredentialService) {
        this.oAuthCredentialService = oAuthCredentialService;
    }

    public void setEnvSourceService(EnvSourceService envSourceService) {
        this.envSourceService = envSourceService;
    }

    public void setSystemOAuthAppSettingsService(SystemOAuthAppSettingsService systemOAuthAppSettingsService) {
        this.systemOAuthAppSettingsService = systemOAuthAppSettingsService;
    }

    public void setSecretManagementPolicyConfig(SecretManagementPolicyConfig secretManagementPolicyConfig) {
        this.secretManagementPolicyConfig = secretManagementPolicyConfig;
    }

    public void setSystemSecretReencryptionRequestRepository(SystemSecretReencryptionRequestRepository systemSecretReencryptionRequestRepository) {
        this.systemSecretReencryptionRequestRepository = systemSecretReencryptionRequestRepository;
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

    private SecretReencryptionFollowUpView runFollowUpActions(SecretReencryptionRequest request) {
        SecretReencryptionRequest effectiveRequest = request == null
                ? new SecretReencryptionRequest(false, false, false, false)
                : request;
        int browserExtensionSessionsRevoked = effectiveRequest.revokeBrowserExtensionSessions() ? extensionSessionService.revokeAllSessionsForAllUsers() : 0;
        int remoteSessionsRevoked = effectiveRequest.revokeRemoteSessions() ? remoteSessionService.invalidateAllSessions() : 0;
        int cachedOAuthAccessTokensCleared = effectiveRequest.clearCachedOAuthAccessTokens() ? oAuthCredentialService.clearAllAccessTokens() : 0;
        return new SecretReencryptionFollowUpView(
                browserExtensionSessionsRevoked,
                remoteSessionsRevoked,
                cachedOAuthAccessTokensCleared);
    }

    private SecretReencryptionResultView executeReencryption(
            AppUser actor,
            SecretReencryptionRequest request,
            Instant now,
            boolean immediate) {
        validateReencryptionReadiness();
        SystemSecretReencryptionRequest requestState = upsertRequestState(actor, request, now, immediate ? now : currentReencryptionRequest() == null ? now : currentReencryptionRequest().executeAfter);
        requestState.status = RequestStatus.RUNNING.name();
        requestState.lastStartedAt = now;
        requestState.lastFailedAt = null;
        requestState.lastErrorMessage = null;
        persistRequestState(requestState);
        try {
            List<SecretReencryptionAreaResultView> areas = new ArrayList<>();
            areas.add(reencryptOAuthCredentials());
            areas.add(reencryptSourceMailboxes());
            areas.add(reencryptDestinationMailboxes());
            areas.add(reencryptUserGmailConfig());
            areas.add(reencryptSystemOAuthSettings());
            areas.add(reencryptAuthSecuritySettings());

            int totalRecordsUpdated = areas.stream().mapToInt(SecretReencryptionAreaResultView::recordsUpdated).sum();
            int totalSecretValuesReencrypted = areas.stream().mapToInt(SecretReencryptionAreaResultView::secretValuesReencrypted).sum();
            SecretReencryptionFollowUpView followUp = runFollowUpActions(request);
            SecretReencryptionVerificationView verification = buildVerification(status());
            requestState.status = RequestStatus.COMPLETED.name();
            requestState.lastCompletedAt = Instant.now();
            requestState.lastResultMessage = verification.passed()
                    ? "Secret re-encryption completed and post-run verification passed."
                    : "Secret re-encryption completed but post-run verification still requires operator attention.";
            requestState.lastVerificationPassed = verification.passed();
            requestState.executeAfter = immediate ? now : requestState.executeAfter;
            persistRequestState(requestState);
            return new SecretReencryptionResultView(
                    RequestStatus.COMPLETED.name(),
                    requestState.lastResultMessage,
                    immediate ? now : requestState.executeAfter,
                    secretEncryptionService.keyVersion(),
                    totalRecordsUpdated,
                    totalSecretValuesReencrypted,
                    areas,
                    followUp,
                    verification);
        } catch (RuntimeException error) {
            requestState.status = RequestStatus.FAILED.name();
            requestState.lastFailedAt = Instant.now();
            requestState.lastErrorMessage = error.getMessage();
            requestState.lastResultMessage = "Secret re-encryption did not complete successfully.";
            requestState.lastVerificationPassed = Boolean.FALSE;
            persistRequestState(requestState);
            throw error;
        }
    }

    private List<SecretReencryptionRequirementView> buildRequirements(
            boolean secureStorageConfigured,
            SecretProviderHealth providerHealth,
            String activeKeyVersion,
            long unavailableKeyRecordCount,
            SystemSecretReencryptionRequest requestState) {
        List<SecretReencryptionRequirementView> requirements = new ArrayList<>();
        requirements.add(new SecretReencryptionRequirementView(
                "secure-storage",
                "Secure secret storage is configured",
                secureStorageConfigured
                        ? "InboxBridge can currently read and write encrypted stored secrets."
                        : "InboxBridge cannot safely rewrite stored secrets until encrypted secret storage is configured.",
                secureStorageConfigured,
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "provider-health",
                "Active secret provider is healthy and writable",
                providerHealth.writable()
                        ? providerHealth.statusMessage()
                        : "InboxBridge must be able to write with the active key path before re-encryption can start.",
                providerHealth.writable(),
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "active-key",
                "An active key version is available",
                activeKeyVersion != null && !activeKeyVersion.isBlank()
                        ? "InboxBridge will target " + activeKeyVersion + "."
                        : "No active key version could be resolved for this deployment.",
                activeKeyVersion != null && !activeKeyVersion.isBlank(),
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "legacy-key-availability",
                "Every stored secret is currently decryptable",
                unavailableKeyRecordCount == 0
                        ? "No stored records reference unavailable key material."
                        : "Some stored records already reference unavailable key material. Restore those keys before re-encrypting.",
                unavailableKeyRecordCount == 0,
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "no-pending-request",
                "No other re-encryption request is pending",
                requestState != null && RequestStatus.PENDING.name().equals(requestState.status)
                        ? "A re-encryption request is already queued for execution after the cooldown window."
                        : "No queued re-encryption request is currently blocking this action.",
                requestState == null || !RequestStatus.PENDING.name().equals(requestState.status),
                false));
        return requirements;
    }

    private void validateReencryptionReadiness() {
        SecretManagementStatusView currentStatus = status();
        currentStatus.reencryptionRequirements().stream()
                .filter(SecretReencryptionRequirementView::blocking)
                .filter(requirement -> !requirement.satisfied())
                .findFirst()
                .ifPresent(requirement -> {
                    throw new IllegalStateException(requirement.detail());
                });
    }

    private SecretReencryptionVerificationView buildVerification(SecretManagementStatusView currentStatus) {
        List<String> messages = new ArrayList<>();
        messages.add(currentStatus.providerWritable()
                ? "The active provider remained writable after the re-encryption run."
                : "The active provider is not currently writable after the re-encryption run.");
        messages.add(currentStatus.nonActiveKeyRecordCount() == 0
                ? "No stored records remain on non-active key versions."
                : currentStatus.nonActiveKeyRecordCount() + " stored records still remain on older key versions.");
        messages.add(currentStatus.unavailableKeyRecordCount() == 0
                ? "No stored records reference unavailable key material."
                : currentStatus.unavailableKeyRecordCount() + " stored records still reference unavailable key material.");
        List<String> operatorSaveItems = new ArrayList<>();
        if (currentStatus.activeKeyVersion() != null && !currentStatus.activeKeyVersion().isBlank()) {
            operatorSaveItems.add("Save the active secret-management target now in use: " + currentStatus.activeKeyVersion() + ".");
        }
        operatorSaveItems.add("Keep the previous key material and provider credentials in a safe place until you finish manual mailbox and OAuth validation.");
        operatorSaveItems.add("Update your operator runbook with the current secret-provider mode (" + currentStatus.mode() + ") and the exact recovery steps for this deployment.");
        boolean passed = currentStatus.providerWritable()
                && currentStatus.nonActiveKeyRecordCount() == 0
                && currentStatus.unavailableKeyRecordCount() == 0;
        return new SecretReencryptionVerificationView(passed, messages, operatorSaveItems);
    }

    private SecretReencryptionRequest effectiveRequest(SecretReencryptionRequest request) {
        return request == null
                ? new SecretReencryptionRequest(false, false, false, false)
                : request;
    }

    private SecretReencryptionRequest toRequest(SystemSecretReencryptionRequest requestState) {
        return new SecretReencryptionRequest(
                requestState.immediateExecutionOverride,
                requestState.revokeBrowserExtensionSessions,
                requestState.revokeRemoteSessions,
                requestState.clearCachedOAuthAccessTokens);
    }

    private void ensureNoPendingRequest() {
        SystemSecretReencryptionRequest requestState = currentReencryptionRequest();
        if (requestState != null && RequestStatus.PENDING.name().equals(requestState.status)) {
            throw new IllegalStateException("A secret re-encryption request is already pending. Wait for it to execute or clear the condition before scheduling another one.");
        }
    }

    private SystemSecretReencryptionRequest upsertRequestState(
            AppUser actor,
            SecretReencryptionRequest request,
            Instant requestedAt,
            Instant executeAfter) {
        SystemSecretReencryptionRequest requestState = currentReencryptionRequest();
        if (requestState == null) {
            requestState = new SystemSecretReencryptionRequest();
            requestState.id = SystemSecretReencryptionRequest.SINGLETON_ID;
        }
        requestState.requestedAt = requestedAt;
        requestState.requestedByUserId = actor == null ? requestState.requestedByUserId : actor.id;
        requestState.executeAfter = executeAfter;
        requestState.immediateExecutionOverride = request.immediateExecutionOverride();
        requestState.revokeBrowserExtensionSessions = request.revokeBrowserExtensionSessions();
        requestState.revokeRemoteSessions = request.revokeRemoteSessions();
        requestState.clearCachedOAuthAccessTokens = request.clearCachedOAuthAccessTokens();
        return requestState;
    }

    private SystemSecretReencryptionRequest currentReencryptionRequest() {
        return systemSecretReencryptionRequestRepository == null
                ? null
                : systemSecretReencryptionRequestRepository.findSingleton().orElse(null);
    }

    private void persistRequestState(SystemSecretReencryptionRequest requestState) {
        if (systemSecretReencryptionRequestRepository != null) {
            systemSecretReencryptionRequestRepository.persist(requestState);
        }
    }

    private SecretReencryptionRequestStatusView toRequestStatusView(SystemSecretReencryptionRequest requestState) {
        if (requestState == null || requestState.status == null || requestState.status.isBlank()) {
            return null;
        }
        return new SecretReencryptionRequestStatusView(
                requestState.status,
                requestState.requestedAt,
                requestState.requestedByUserId,
                requestState.executeAfter,
                requestState.lastStartedAt,
                requestState.lastCompletedAt,
                requestState.lastFailedAt,
                requestState.immediateExecutionOverride,
                requestState.lastErrorMessage != null && !requestState.lastErrorMessage.isBlank()
                        ? requestState.lastErrorMessage
                        : requestState.lastResultMessage,
                Boolean.TRUE.equals(requestState.lastVerificationPassed));
    }

    private Duration reencryptionCooldown() {
        return secretManagementPolicyConfig == null ? Duration.ofHours(12) : secretManagementPolicyConfig.reencryptionCooldown();
    }

    private boolean allowImmediateReencryptOverride() {
        return secretManagementPolicyConfig != null && secretManagementPolicyConfig.allowImmediateReencryptOverride();
    }

    private enum RequestStatus {
        PENDING,
        SCHEDULED,
        RUNNING,
        COMPLETED,
        FAILED
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

    private boolean envManagedMailboxSecretsAllowed() {
        return envSourceService == null || envSourceService.envManagedMailboxSecretsAllowed();
    }

    private long configuredEnvManagedSourceCount() {
        return envSourceService == null ? 0 : envSourceService.configuredSourceCountIgnoringPolicy();
    }

    private boolean envManagedGoogleRefreshTokenConfigured() {
        return systemOAuthAppSettingsService != null && systemOAuthAppSettingsService.envManagedGoogleRefreshTokenConfigured();
    }
}
