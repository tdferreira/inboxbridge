package dev.inboxbridge.service.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.SecretManagementPolicyConfig;
import dev.inboxbridge.dto.SecretManagementKeyUsageView;
import dev.inboxbridge.dto.SecretManagementRetirementRequirementView;
import dev.inboxbridge.dto.SecretManagementRetirementCompletionView;
import dev.inboxbridge.dto.SecretManagementRetirementReviewView;
import dev.inboxbridge.dto.SecretManagementReportView;
import dev.inboxbridge.dto.SecretManagementRotationPlanView;
import dev.inboxbridge.dto.SecretProviderComponentStatusView;
import dev.inboxbridge.dto.SecretReencryptionPreviewView;
import dev.inboxbridge.dto.SecretReencryptionFollowUpView;
import dev.inboxbridge.dto.SecretReencryptionRequest;
import dev.inboxbridge.dto.SecretReencryptionAreaResultView;
import dev.inboxbridge.dto.SecretReencryptionRequirementView;
import dev.inboxbridge.dto.SecretReencryptionResultView;
import dev.inboxbridge.dto.SecretReencryptionRequestStatusView;
import dev.inboxbridge.dto.SecretReencryptionVerificationView;
import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequest;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequestRepository;
import dev.inboxbridge.persistence.SystemSecretRetirementReview;
import dev.inboxbridge.persistence.SystemSecretRetirementReviewRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.auth.UserSessionService;
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

    @Inject
    SystemSecretRetirementReviewRepository systemSecretRetirementReviewRepository;

    @Inject
    AppUserService appUserService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    PasskeyService passkeyService;

    @Inject
    ObjectMapper objectMapper;

    public SecretManagementStatusView status() {
        return status(null);
    }

    public SecretManagementStatusView status(UserSession currentSession) {
        SecretProviderHealth providerHealth = providerResolver().health();
        List<SecretProviderComponentStatusView> providerComponents = providerResolver().componentStatuses();
        SystemSecretReencryptionRequest requestState = currentReencryptionRequest();
        boolean reauthenticationRequired = reencryptionReauthenticationRequired();
        boolean reauthenticationSatisfied = reencryptionReauthenticationSatisfied(currentSession);
        Instant reauthenticationExpiresAt = reencryptionReauthenticationExpiresAt(currentSession);
        if (!providerHealth.writable()) {
            List<SecretReencryptionRequirementView> requirements = buildRequirements(
                    false,
                    providerHealth,
                    null,
                    0,
                    requestState,
                    reauthenticationRequired,
                    reauthenticationSatisfied);
            List<SecretManagementRetirementRequirementView> retirementRequirements = buildRetirementRequirements(
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
                    providerComponents,
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
                    false,
                    buildRotationPlan(
                            null,
                            null,
                            0,
                            0,
                            0,
                            List.of(),
                            List.of(),
                            false,
                            false),
                    null,
                    List.of(),
                    false,
                    requirements,
                    retirementRequirements,
                    latestRetirementReview(),
                    recentRetirementReviews(),
                    toRequestStatusView(requestState),
                    reencryptionCooldown().toString(),
                    allowImmediateReencryptOverride(),
                    reauthenticationRequired,
                    reauthenticationSatisfied,
                    reauthenticationExpiresAt);
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
        RotationNeedAccumulator metadataRewrapCandidates = collectMetadataRewrapCandidates();
        SecretManagementRotationPlanView rotationPlan = buildRotationPlan(
                activeKeyVersion,
                providerHealth,
                nonActiveKeyRecordCount,
                unavailableKeyRecordCount,
                metadataRewrapCandidates.recordCount,
                metadataRewrapCandidates.areas(),
                keyUsage,
                protectedRecordCount > 0,
                providerHealth.writable());
        SecretReencryptionPreviewView reencryptionPreview = buildReencryptionPreview(activeKeyVersion);
        List<SecretReencryptionRequirementView> requirements = buildRequirements(
                true,
                providerHealth,
                activeKeyVersion,
                unavailableKeyRecordCount,
                requestState,
                reauthenticationRequired,
                reauthenticationSatisfied);
        boolean reencryptionReady = requirements.stream()
                .filter(SecretReencryptionRequirementView::blocking)
                .allMatch(SecretReencryptionRequirementView::satisfied);
        List<SecretManagementRetirementRequirementView> retirementRequirements = buildRetirementRequirements(
                providerHealth,
                rotationPlan,
                unavailableKeyRecordCount,
                requestState);
        boolean legacyKeyRetirementReady = retirementRequirements.stream()
                .filter(SecretManagementRetirementRequirementView::blocking)
                .allMatch(SecretManagementRetirementRequirementView::satisfied);
        SecretManagementRetirementReviewView latestRetirementReview = latestRetirementReview();
        List<SecretManagementRetirementReviewView> recentRetirementReviews = recentRetirementReviews();

        return new SecretManagementStatusView(
                true,
                providerHealth.mode().name(),
                providerHealth.providerId(),
                providerHealth.healthy(),
                providerHealth.writable(),
                providerHealth.statusMessage(),
                providerComponents,
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
                !rotationPlan.rotationNeeded(),
                legacyKeyRetirementReady,
                rotationPlan,
                reencryptionPreview,
                keyUsage,
                reencryptionReady,
                requirements,
                retirementRequirements,
                latestRetirementReview,
                recentRetirementReviews,
                toRequestStatusView(requestState),
                reencryptionCooldown().toString(),
                allowImmediateReencryptOverride(),
                reauthenticationRequired,
                reauthenticationSatisfied,
                reauthenticationExpiresAt);
    }

    public SecretManagementReportView exportReport(UserSession currentSession) {
        return new SecretManagementReportView(Instant.now(), status(currentSession));
    }

    @Transactional
    public SecretManagementStatusView recordRetirementReview(AppUser actor, UserSession currentSession) {
        if (actor == null || actor.id == null) {
            throw new IllegalArgumentException("Missing current user");
        }
        SecretManagementStatusView currentStatus = status(currentSession);
        SystemSecretRetirementReview review = new SystemSecretRetirementReview();
        review.reviewedAt = Instant.now();
        review.reviewedByUserId = actor.id;
        review.reviewedByUsername = actor.username;
        review.providerId = currentStatus.providerId();
        review.activeKeyVersion = currentStatus.activeKeyVersion();
        review.activeKeyId = currentStatus.activeKeyId();
        review.legacyKeyIdsJson = writeJson(currentStatus.configuredLegacyKeyIds());
        review.safeToRetireLegacyKeys = currentStatus.safeToRetireLegacyKeys();
        review.legacyKeyRetirementReady = currentStatus.legacyKeyRetirementReady();
        review.nonActiveKeyRecordCount = currentStatus.nonActiveKeyRecordCount();
        review.unavailableKeyRecordCount = currentStatus.unavailableKeyRecordCount();
        review.latestRequestStatus = currentStatus.reencryptionRequest() == null
                ? null
                : currentStatus.reencryptionRequest().status();
        review.blockingRequirementsRemaining = (int) currentStatus.retirementRequirements().stream()
                .filter(SecretManagementRetirementRequirementView::blocking)
                .filter(requirement -> !requirement.satisfied())
                .count();
        review.unsatisfiedRequirementIdsJson = writeJson(currentStatus.retirementRequirements().stream()
                .filter(requirement -> !requirement.satisfied())
                .map(SecretManagementRetirementRequirementView::requirementId)
                .toList());
        review.statusSnapshotJson = writeJson(currentStatus);
        if (systemSecretRetirementReviewRepository != null) {
            systemSecretRetirementReviewRepository.persist(review);
        }
        return status(currentSession);
    }

    @Transactional
    public SecretManagementStatusView verifyRetirementCompletion(AppUser actor, UserSession currentSession) {
        if (actor == null || actor.id == null) {
            throw new IllegalArgumentException("Missing current user");
        }
        if (systemSecretRetirementReviewRepository == null) {
            throw new IllegalStateException("Retirement review storage is unavailable.");
        }
        SystemSecretRetirementReview review = systemSecretRetirementReviewRepository.findLatest()
                .orElseThrow(() -> new IllegalStateException(
                        "Record a legacy-key retirement review snapshot before verifying post-cleanup completion."));
        SecretManagementStatusView currentStatus = status(currentSession);
        List<String> unsatisfiedChecks = new ArrayList<>();
        if (!currentStatus.legacyKeyRetirementReady()) {
            unsatisfiedChecks.add("live-retirement-ready");
        }
        if (!currentStatus.safeToRetireLegacyKeys()) {
            unsatisfiedChecks.add("live-safe-to-retire");
        }
        if (!currentStatus.configuredLegacyKeyIds().isEmpty()) {
            unsatisfiedChecks.add("legacy-key-config-removed");
        }
        if (!equalsNullable(review.providerId, currentStatus.providerId())
                || !equalsNullable(review.activeKeyVersion, currentStatus.activeKeyVersion())
                || !equalsNullable(review.activeKeyId, currentStatus.activeKeyId())) {
            unsatisfiedChecks.add("active-target-unchanged");
        }
        review.completionVerifiedAt = Instant.now();
        review.completionVerifiedByUserId = actor.id;
        review.completionVerifiedByUsername = actor.username;
        review.completionStatus = unsatisfiedChecks.isEmpty() ? "VERIFIED" : "BLOCKED";
        review.completionMessage = unsatisfiedChecks.isEmpty()
                ? "Legacy-key cleanup was verified against the latest recorded retirement review."
                : "Post-cleanup verification found remaining drift or blockers. Restore the expected active target or finish the remaining retirement steps before marking cleanup complete.";
        review.completionUnsatisfiedCheckIdsJson = writeJson(unsatisfiedChecks);
        review.completionSnapshotJson = writeJson(currentStatus);
        systemSecretRetirementReviewRepository.persist(review);
        return status(currentSession);
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets() {
        return reencryptAllStoredSecrets(null, null, new SecretReencryptionRequest(false, false, false, false));
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets(SecretReencryptionRequest request) {
        return reencryptAllStoredSecrets(null, null, request);
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets(AppUser actor, SecretReencryptionRequest request) {
        return reencryptAllStoredSecrets(actor, null, request);
    }

    @Transactional
    public SecretReencryptionResultView reencryptAllStoredSecrets(AppUser actor, UserSession currentSession, SecretReencryptionRequest request) {
        SecretReencryptionRequest effectiveRequest = effectiveRequest(request);
        ensureNoPendingRequest();
        validateReencryptionReadiness(currentSession, true);
        SecretReencryptionPreviewView requestPreview = buildReencryptionPreview(providerResolver().activeKeyVersion());
        if (effectiveRequest.immediateExecutionOverride() && !allowImmediateReencryptOverride()) {
            throw new IllegalStateException(
                    "Immediate secret re-encryption override is disabled by server policy. Wait for the configured cooldown window or enable the testing override on the server first.");
        }
        Duration cooldown = reencryptionCooldown();
        Instant now = Instant.now();
        if (!effectiveRequest.immediateExecutionOverride() && !cooldown.isZero() && !cooldown.isNegative()) {
            Instant executeAfter = now.plus(cooldown);
            SystemSecretReencryptionRequest requestState = upsertRequestState(actor, effectiveRequest, requestPreview, now, executeAfter);
            requestState.status = RequestStatus.PENDING.name();
            requestState.lastErrorMessage = null;
            requestState.lastResultMessage = "Secret re-encryption is queued and will execute after the cooldown window.";
            requestState.lastVerificationPassed = null;
            clearLastExecutionSnapshot(requestState);
            persistRequestState(requestState);
            return new SecretReencryptionResultView(
                    RequestStatus.SCHEDULED.name(),
                    "Secret re-encryption was scheduled after the configured cooldown window.",
                    executeAfter,
                    providerResolver().activeKeyVersion(),
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    new SecretReencryptionFollowUpView(0, 0, 0),
                    buildVerification(status(currentSession)));
        }
        return executeReencryption(actor, currentSession, effectiveRequest, requestPreview, now, true);
    }

    @Transactional
    public SecretManagementStatusView verifyReencryptionPassword(AppUser actor, UserSession currentSession, String password) {
        requireReauthenticationCapableSession(currentSession);
        if (actor == null || actor.id == null) {
            throw new IllegalArgumentException("Missing current user");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Enter the current password to verify this sensitive action.");
        }
        if (appUserService == null || !appUserService.passwordMatches(actor, password)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        Instant verifiedAt = userSessionService.markSensitiveActionAuthenticated(currentSession.id);
        currentSession.lastSensitiveAuthAt = verifiedAt;
        return status(currentSession);
    }

    @Transactional
    public StartPasskeyCeremonyResponse startReencryptionPasskeyVerification(AppUser actor, UserSession currentSession) {
        requireReauthenticationCapableSession(currentSession);
        if (actor == null || actor.id == null) {
            throw new IllegalArgumentException("Missing current user");
        }
        if (passkeyService == null) {
            throw new IllegalStateException("Passkey verification is unavailable.");
        }
        return passkeyService.startAuthenticationForUser(actor, true);
    }

    @Transactional
    public SecretManagementStatusView finishReencryptionPasskeyVerification(
            AppUser actor,
            UserSession currentSession,
            FinishPasskeyCeremonyRequest request) {
        requireReauthenticationCapableSession(currentSession);
        if (actor == null || actor.id == null) {
            throw new IllegalArgumentException("Missing current user");
        }
        if (passkeyService == null) {
            throw new IllegalStateException("Passkey verification is unavailable.");
        }
        PasskeyService.PasskeyAuthenticationResult result = passkeyService.finishAuthentication(request);
        if (result.user() == null || !actor.id.equals(result.user().id)) {
            throw new IllegalArgumentException("Passkey verification failed for this browser session.");
        }
        Instant verifiedAt = userSessionService.markSensitiveActionAuthenticated(currentSession.id);
        currentSession.lastSensitiveAuthAt = verifiedAt;
        return status(currentSession);
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
            executeReencryption(null, null, toRequest(requestState), readRequestPreview(requestState), now, false);
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

    public void setSystemSecretRetirementReviewRepository(SystemSecretRetirementReviewRepository systemSecretRetirementReviewRepository) {
        this.systemSecretRetirementReviewRepository = systemSecretRetirementReviewRepository;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    private SecretReencryptionPreviewView buildReencryptionPreview(String activeKeyVersion) {
        if (activeKeyVersion == null || activeKeyVersion.isBlank()) {
            return null;
        }
        List<SecretReencryptionAreaResultView> areas = List.of(
                previewOAuthCredentials(),
                previewSourceMailboxes(),
                previewDestinationMailboxes(),
                previewUserGmailConfig(),
                previewSystemOAuthSettings(),
                previewAuthSecuritySettings());
        int totalRecordsPendingUpdate = areas.stream().mapToInt(SecretReencryptionAreaResultView::recordsUpdated).sum();
        int totalSecretValuesPendingRewrite = areas.stream().mapToInt(SecretReencryptionAreaResultView::secretValuesReencrypted).sum();
        int totalFullReencryptionCount = areas.stream().mapToInt(SecretReencryptionAreaResultView::fullReencryptionCount).sum();
        int totalMetadataRewrapCount = areas.stream().mapToInt(SecretReencryptionAreaResultView::metadataRewrapCount).sum();
        return new SecretReencryptionPreviewView(
                activeKeyVersion,
                totalRecordsPendingUpdate,
                totalSecretValuesPendingRewrite,
                totalFullReencryptionCount,
                totalMetadataRewrapCount,
                areas);
    }

    private SecretReencryptionAreaResultView previewOAuthCredentials() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (OAuthCredential credential : oAuthCredentialRepository.listAll()) {
            AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
            updatedFields.add(plannedRewrite(
                    credential.refreshTokenCiphertext,
                    credential.refreshTokenNonce,
                    credential.keyVersion));
            updatedFields.add(plannedRewrite(
                    credential.accessTokenCiphertext,
                    credential.accessTokenNonce,
                    credential.keyVersion));
            if (updatedFields.secretValuesReencrypted > 0) {
                recordsUpdated++;
                secretValuesReencrypted += updatedFields.secretValuesReencrypted;
                fullReencryptionCount += updatedFields.fullReencryptionCount;
                metadataRewrapCount += updatedFields.metadataRewrapCount;
            }
        }
        return new SecretReencryptionAreaResultView("oauth-credentials", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView previewSourceMailboxes() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (UserEmailAccount account : userEmailAccountRepository.listAll()) {
            AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
            updatedFields.add(plannedRewrite(
                    account.passwordCiphertext,
                    account.passwordNonce,
                    account.keyVersion));
            updatedFields.add(plannedRewrite(
                    account.oauthRefreshTokenCiphertext,
                    account.oauthRefreshTokenNonce,
                    account.keyVersion));
            if (updatedFields.secretValuesReencrypted > 0) {
                recordsUpdated++;
                secretValuesReencrypted += updatedFields.secretValuesReencrypted;
                fullReencryptionCount += updatedFields.fullReencryptionCount;
                metadataRewrapCount += updatedFields.metadataRewrapCount;
            }
        }
        return new SecretReencryptionAreaResultView("source-mailboxes", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView previewDestinationMailboxes() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (UserMailDestinationConfig config : userMailDestinationConfigRepository.listAll()) {
            RewriteOutcome updatedFields = plannedRewrite(
                    config.passwordCiphertext,
                    config.passwordNonce,
                    config.keyVersion);
            if (updatedFields.updated()) {
                recordsUpdated++;
                secretValuesReencrypted += 1;
                fullReencryptionCount += updatedFields.metadataRewrap() ? 0 : 1;
                metadataRewrapCount += updatedFields.metadataRewrap() ? 1 : 0;
            }
        }
        return new SecretReencryptionAreaResultView("destination-mailboxes", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView previewUserGmailConfig() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (UserGmailConfig config : userGmailConfigRepository.listAll()) {
            AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
            updatedFields.add(plannedRewrite(
                    config.clientIdCiphertext,
                    config.clientIdNonce,
                    config.keyVersion));
            updatedFields.add(plannedRewrite(
                    config.clientSecretCiphertext,
                    config.clientSecretNonce,
                    config.keyVersion));
            updatedFields.add(plannedRewrite(
                    config.refreshTokenCiphertext,
                    config.refreshTokenNonce,
                    config.keyVersion));
            if (updatedFields.secretValuesReencrypted > 0) {
                recordsUpdated++;
                secretValuesReencrypted += updatedFields.secretValuesReencrypted;
                fullReencryptionCount += updatedFields.fullReencryptionCount;
                metadataRewrapCount += updatedFields.metadataRewrapCount;
            }
        }
        return new SecretReencryptionAreaResultView("gmail-user-config", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView previewSystemOAuthSettings() {
        SystemOAuthAppSettings settings = systemOAuthAppSettingsRepository.findSingleton().orElse(null);
        if (settings == null) {
            return new SecretReencryptionAreaResultView("system-oauth", 0, 0, 0, 0);
        }
        AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
        updatedFields.add(plannedRewrite(
                settings.googleClientIdCiphertext,
                settings.googleClientIdNonce,
                settings.keyVersion));
        updatedFields.add(plannedRewrite(
                settings.googleClientSecretCiphertext,
                settings.googleClientSecretNonce,
                settings.keyVersion));
        updatedFields.add(plannedRewrite(
                settings.googleRefreshTokenCiphertext,
                settings.googleRefreshTokenNonce,
                settings.keyVersion));
        updatedFields.add(plannedRewrite(
                settings.microsoftClientIdCiphertext,
                settings.microsoftClientIdNonce,
                settings.keyVersion));
        updatedFields.add(plannedRewrite(
                settings.microsoftClientSecretCiphertext,
                settings.microsoftClientSecretNonce,
                settings.keyVersion));
        if (updatedFields.secretValuesReencrypted > 0) {
            return new SecretReencryptionAreaResultView(
                    "system-oauth",
                    1,
                    updatedFields.secretValuesReencrypted,
                    updatedFields.fullReencryptionCount,
                    updatedFields.metadataRewrapCount);
        }
        return new SecretReencryptionAreaResultView("system-oauth", 0, 0, 0, 0);
    }

    private SecretReencryptionAreaResultView previewAuthSecuritySettings() {
        SystemAuthSecuritySetting settings = systemAuthSecuritySettingRepository.findSingleton().orElse(null);
        if (settings == null) {
            return new SecretReencryptionAreaResultView("auth-security", 0, 0, 0, 0);
        }
        AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
        updatedFields.add(plannedRewrite(
                settings.registrationTurnstileSecretCiphertext,
                settings.registrationTurnstileSecretNonce,
                settings.keyVersion));
        updatedFields.add(plannedRewrite(
                settings.registrationHcaptchaSecretCiphertext,
                settings.registrationHcaptchaSecretNonce,
                settings.keyVersion));
        updatedFields.add(plannedRewrite(
                settings.geoIpIpinfoTokenCiphertext,
                settings.geoIpIpinfoTokenNonce,
                settings.keyVersion));
        if (updatedFields.secretValuesReencrypted > 0) {
            return new SecretReencryptionAreaResultView(
                    "auth-security",
                    1,
                    updatedFields.secretValuesReencrypted,
                    updatedFields.fullReencryptionCount,
                    updatedFields.metadataRewrapCount);
        }
        return new SecretReencryptionAreaResultView("auth-security", 0, 0, 0, 0);
    }

    private SecretReencryptionAreaResultView reencryptOAuthCredentials() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (OAuthCredential credential : oAuthCredentialRepository.listAll()) {
            AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
            updatedFields.add(rewriteSecret(
                    credential.refreshTokenCiphertext,
                    credential.refreshTokenNonce,
                    credential.keyVersion,
                    value -> {
                        credential.refreshTokenCiphertext = value.ciphertextBase64();
                        credential.refreshTokenNonce = value.nonceBase64();
                    },
                    credential.provider + ":" + credential.subjectKey + ":refresh"));
            updatedFields.add(rewriteSecret(
                    credential.accessTokenCiphertext,
                    credential.accessTokenNonce,
                    credential.keyVersion,
                    value -> {
                        credential.accessTokenCiphertext = value.ciphertextBase64();
                        credential.accessTokenNonce = value.nonceBase64();
                    },
                    credential.provider + ":" + credential.subjectKey + ":access"));
            if (updatedFields.secretValuesReencrypted > 0) {
                credential.keyVersion = secretEncryptionService.keyVersion();
                credential.updatedAt = Instant.now();
                oAuthCredentialRepository.persist(credential);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields.secretValuesReencrypted;
                fullReencryptionCount += updatedFields.fullReencryptionCount;
                metadataRewrapCount += updatedFields.metadataRewrapCount;
            }
        }
        return new SecretReencryptionAreaResultView("oauth-credentials", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView reencryptSourceMailboxes() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (UserEmailAccount account : userEmailAccountRepository.listAll()) {
            AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
            updatedFields.add(rewriteSecret(
                    account.passwordCiphertext,
                    account.passwordNonce,
                    account.keyVersion,
                    value -> {
                        account.passwordCiphertext = value.ciphertextBase64();
                        account.passwordNonce = value.nonceBase64();
                    },
                    "user-bridge:" + account.userId + ":" + account.emailAccountId + ":password"));
            updatedFields.add(rewriteSecret(
                    account.oauthRefreshTokenCiphertext,
                    account.oauthRefreshTokenNonce,
                    account.keyVersion,
                    value -> {
                        account.oauthRefreshTokenCiphertext = value.ciphertextBase64();
                        account.oauthRefreshTokenNonce = value.nonceBase64();
                    },
                    "user-bridge:" + account.userId + ":" + account.emailAccountId + ":oauth-refresh-token"));
            if (updatedFields.secretValuesReencrypted > 0) {
                account.keyVersion = secretEncryptionService.keyVersion();
                account.updatedAt = Instant.now();
                userEmailAccountRepository.persist(account);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields.secretValuesReencrypted;
                fullReencryptionCount += updatedFields.fullReencryptionCount;
                metadataRewrapCount += updatedFields.metadataRewrapCount;
            }
        }
        return new SecretReencryptionAreaResultView("source-mailboxes", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView reencryptDestinationMailboxes() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (UserMailDestinationConfig config : userMailDestinationConfigRepository.listAll()) {
            RewriteOutcome updatedFields = rewriteSecret(
                    config.passwordCiphertext,
                    config.passwordNonce,
                    config.keyVersion,
                    value -> {
                        config.passwordCiphertext = value.ciphertextBase64();
                        config.passwordNonce = value.nonceBase64();
                    },
                    "user-destination:" + config.userId + ":password");
            if (updatedFields.updated()) {
                config.keyVersion = secretEncryptionService.keyVersion();
                config.updatedAt = Instant.now();
                userMailDestinationConfigRepository.persist(config);
                recordsUpdated++;
                secretValuesReencrypted += 1;
                fullReencryptionCount += updatedFields.metadataRewrap() ? 0 : 1;
                metadataRewrapCount += updatedFields.metadataRewrap() ? 1 : 0;
            }
        }
        return new SecretReencryptionAreaResultView("destination-mailboxes", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView reencryptUserGmailConfig() {
        int recordsUpdated = 0;
        int secretValuesReencrypted = 0;
        int fullReencryptionCount = 0;
        int metadataRewrapCount = 0;
        for (UserGmailConfig config : userGmailConfigRepository.listAll()) {
            AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
            updatedFields.add(rewriteSecret(
                    config.clientIdCiphertext,
                    config.clientIdNonce,
                    config.keyVersion,
                    value -> {
                        config.clientIdCiphertext = value.ciphertextBase64();
                        config.clientIdNonce = value.nonceBase64();
                    },
                    "user-gmail:" + config.userId + ":client-id"));
            updatedFields.add(rewriteSecret(
                    config.clientSecretCiphertext,
                    config.clientSecretNonce,
                    config.keyVersion,
                    value -> {
                        config.clientSecretCiphertext = value.ciphertextBase64();
                        config.clientSecretNonce = value.nonceBase64();
                    },
                    "user-gmail:" + config.userId + ":client-secret"));
            updatedFields.add(rewriteSecret(
                    config.refreshTokenCiphertext,
                    config.refreshTokenNonce,
                    config.keyVersion,
                    value -> {
                        config.refreshTokenCiphertext = value.ciphertextBase64();
                        config.refreshTokenNonce = value.nonceBase64();
                    },
                    "user-gmail:" + config.userId + ":refresh-token"));
            if (updatedFields.secretValuesReencrypted > 0) {
                config.keyVersion = secretEncryptionService.keyVersion();
                config.updatedAt = Instant.now();
                userGmailConfigRepository.persist(config);
                recordsUpdated++;
                secretValuesReencrypted += updatedFields.secretValuesReencrypted;
                fullReencryptionCount += updatedFields.fullReencryptionCount;
                metadataRewrapCount += updatedFields.metadataRewrapCount;
            }
        }
        return new SecretReencryptionAreaResultView("gmail-user-config", recordsUpdated, secretValuesReencrypted, fullReencryptionCount, metadataRewrapCount);
    }

    private SecretReencryptionAreaResultView reencryptSystemOAuthSettings() {
        SystemOAuthAppSettings settings = systemOAuthAppSettingsRepository.findSingleton().orElse(null);
        if (settings == null) {
            return new SecretReencryptionAreaResultView("system-oauth", 0, 0, 0, 0);
        }
        AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
        updatedFields.add(rewriteSecret(
                settings.googleClientIdCiphertext,
                settings.googleClientIdNonce,
                settings.keyVersion,
                value -> {
                    settings.googleClientIdCiphertext = value.ciphertextBase64();
                    settings.googleClientIdNonce = value.nonceBase64();
                },
                "system-oauth:google-client-id"));
        updatedFields.add(rewriteSecret(
                settings.googleClientSecretCiphertext,
                settings.googleClientSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.googleClientSecretCiphertext = value.ciphertextBase64();
                    settings.googleClientSecretNonce = value.nonceBase64();
                },
                "system-oauth:google-client-secret"));
        updatedFields.add(rewriteSecret(
                settings.googleRefreshTokenCiphertext,
                settings.googleRefreshTokenNonce,
                settings.keyVersion,
                value -> {
                    settings.googleRefreshTokenCiphertext = value.ciphertextBase64();
                    settings.googleRefreshTokenNonce = value.nonceBase64();
                },
                "system-oauth:google-refresh-token"));
        updatedFields.add(rewriteSecret(
                settings.microsoftClientIdCiphertext,
                settings.microsoftClientIdNonce,
                settings.keyVersion,
                value -> {
                    settings.microsoftClientIdCiphertext = value.ciphertextBase64();
                    settings.microsoftClientIdNonce = value.nonceBase64();
                },
                "system-oauth:microsoft-client-id"));
        updatedFields.add(rewriteSecret(
                settings.microsoftClientSecretCiphertext,
                settings.microsoftClientSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.microsoftClientSecretCiphertext = value.ciphertextBase64();
                    settings.microsoftClientSecretNonce = value.nonceBase64();
                },
                "system-oauth:microsoft-client-secret"));
        if (updatedFields.secretValuesReencrypted > 0) {
            settings.keyVersion = secretEncryptionService.keyVersion();
            settings.updatedAt = Instant.now();
            systemOAuthAppSettingsRepository.persist(settings);
            return new SecretReencryptionAreaResultView(
                    "system-oauth",
                    1,
                    updatedFields.secretValuesReencrypted,
                    updatedFields.fullReencryptionCount,
                    updatedFields.metadataRewrapCount);
        }
        return new SecretReencryptionAreaResultView("system-oauth", 0, 0, 0, 0);
    }

    private SecretReencryptionAreaResultView reencryptAuthSecuritySettings() {
        SystemAuthSecuritySetting settings = systemAuthSecuritySettingRepository.findSingleton().orElse(null);
        if (settings == null) {
            return new SecretReencryptionAreaResultView("auth-security", 0, 0, 0, 0);
        }
        AreaRewriteAccumulator updatedFields = new AreaRewriteAccumulator();
        updatedFields.add(rewriteSecret(
                settings.registrationTurnstileSecretCiphertext,
                settings.registrationTurnstileSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.registrationTurnstileSecretCiphertext = value.ciphertextBase64();
                    settings.registrationTurnstileSecretNonce = value.nonceBase64();
                },
                "system-auth-security:registration-turnstile-secret"));
        updatedFields.add(rewriteSecret(
                settings.registrationHcaptchaSecretCiphertext,
                settings.registrationHcaptchaSecretNonce,
                settings.keyVersion,
                value -> {
                    settings.registrationHcaptchaSecretCiphertext = value.ciphertextBase64();
                    settings.registrationHcaptchaSecretNonce = value.nonceBase64();
                },
                "system-auth-security:registration-hcaptcha-secret"));
        updatedFields.add(rewriteSecret(
                settings.geoIpIpinfoTokenCiphertext,
                settings.geoIpIpinfoTokenNonce,
                settings.keyVersion,
                value -> {
                    settings.geoIpIpinfoTokenCiphertext = value.ciphertextBase64();
                    settings.geoIpIpinfoTokenNonce = value.nonceBase64();
                },
                "system-auth-security:geo-ip-ipinfo-token"));
        if (updatedFields.secretValuesReencrypted > 0) {
            settings.keyVersion = secretEncryptionService.keyVersion();
            settings.updatedAt = Instant.now();
            systemAuthSecuritySettingRepository.persist(settings);
            return new SecretReencryptionAreaResultView(
                    "auth-security",
                    1,
                    updatedFields.secretValuesReencrypted,
                    updatedFields.fullReencryptionCount,
                    updatedFields.metadataRewrapCount);
        }
        return new SecretReencryptionAreaResultView("auth-security", 0, 0, 0, 0);
    }

    private RewriteOutcome rewriteSecret(
            String ciphertext,
            String nonce,
            String keyVersion,
            java.util.function.Consumer<SecretEncryptionService.EncryptedValue> saveEncrypted,
            String context) {
        RewriteOutcome outcome = plannedRewrite(ciphertext, nonce, keyVersion);
        if (!outcome.updated()) {
            return outcome;
        }
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.reencryptToActive(ciphertext, nonce, keyVersion, context);
        saveEncrypted.accept(encrypted);
        return outcome;
    }

    private RewriteOutcome plannedRewrite(String ciphertext, String nonce, String keyVersion) {
        if (ciphertext == null || ciphertext.isBlank() || keyVersion == null || keyVersion.isBlank()) {
            return RewriteOutcome.SKIPPED;
        }
        boolean metadataRewrapCandidate = secretEncryptionService.canMetadataRewrapToActive(ciphertext, nonce, keyVersion);
        if (!metadataRewrapCandidate && (nonce == null || nonce.isBlank())) {
            return RewriteOutcome.SKIPPED;
        }
        if (!metadataRewrapCandidate && secretEncryptionService.keyVersion().equals(keyVersion)) {
            return RewriteOutcome.SKIPPED;
        }
        return metadataRewrapCandidate ? RewriteOutcome.METADATA_REWRAP : RewriteOutcome.FULL_REENCRYPTION;
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
            UserSession currentSession,
            SecretReencryptionRequest request,
            SecretReencryptionPreviewView requestPreview,
            Instant now,
            boolean immediate) {
        validateReencryptionReadiness(currentSession, immediate);
        SystemSecretReencryptionRequest requestState = upsertRequestState(
                actor,
                request,
                requestPreview,
                now,
                immediate ? now : currentReencryptionRequest() == null ? now : currentReencryptionRequest().executeAfter);
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
            int totalFullReencryptionCount = areas.stream().mapToInt(SecretReencryptionAreaResultView::fullReencryptionCount).sum();
            int totalMetadataRewrapCount = areas.stream().mapToInt(SecretReencryptionAreaResultView::metadataRewrapCount).sum();
            SecretReencryptionFollowUpView followUp = runFollowUpActions(request);
            SecretReencryptionVerificationView verification = buildVerification(status(currentSession));
            requestState.status = RequestStatus.COMPLETED.name();
            requestState.lastCompletedAt = Instant.now();
            requestState.lastResultMessage = verification.passed()
                    ? "Secret re-encryption completed and post-run verification passed."
                    : "Secret re-encryption completed but post-run verification still requires operator attention.";
            requestState.lastVerificationPassed = verification.passed();
            requestState.executeAfter = immediate ? now : requestState.executeAfter;
            requestState.lastTotalRecordsUpdated = totalRecordsUpdated;
            requestState.lastTotalSecretValuesReencrypted = totalSecretValuesReencrypted;
            requestState.lastTotalFullReencryptionCount = totalFullReencryptionCount;
            requestState.lastTotalMetadataRewrapCount = totalMetadataRewrapCount;
            requestState.lastAreaResultsJson = writeJson(areas);
            requestState.lastFollowUpJson = writeJson(followUp);
            requestState.lastVerificationJson = writeJson(verification);
            persistRequestState(requestState);
            return new SecretReencryptionResultView(
                    RequestStatus.COMPLETED.name(),
                    requestState.lastResultMessage,
                    immediate ? now : requestState.executeAfter,
                    secretEncryptionService.keyVersion(),
                    totalRecordsUpdated,
                    totalSecretValuesReencrypted,
                    totalFullReencryptionCount,
                    totalMetadataRewrapCount,
                    areas,
                    followUp,
                    verification);
        } catch (RuntimeException error) {
            requestState.status = RequestStatus.FAILED.name();
            requestState.lastFailedAt = Instant.now();
            requestState.lastErrorMessage = error.getMessage();
            requestState.lastResultMessage = "Secret re-encryption did not complete successfully.";
            requestState.lastVerificationPassed = Boolean.FALSE;
            clearLastExecutionSnapshot(requestState);
            persistRequestState(requestState);
            throw error;
        }
    }

    private List<SecretReencryptionRequirementView> buildRequirements(
            boolean secureStorageConfigured,
            SecretProviderHealth providerHealth,
            String activeKeyVersion,
            long unavailableKeyRecordCount,
            SystemSecretReencryptionRequest requestState,
            boolean reauthenticationRequired,
            boolean reauthenticationSatisfied) {
        List<SecretReencryptionRequirementView> requirements = new ArrayList<>();
        requirements.add(new SecretReencryptionRequirementView(
                "secure-storage",
                "Secure secret storage is configured",
                secureStorageConfigured
                        ? "InboxBridge can currently read and write encrypted stored secrets."
                        : "InboxBridge cannot safely rewrite stored secrets until encrypted secret storage is configured.",
                secureStorageConfigured
                        ? List.of(
                                "Keep the currently active secret-management provider configured until the migration and follow-up verification are complete.")
                        : List.of(
                                "Choose a new secret-management mode before continuing: local key mode, external transit / KMS mode, or split-key mode.",
                                "For local mode, configure SECURITY_TOKEN_ENCRYPTION_KEY and optionally SECURITY_TOKEN_ENCRYPTION_KEY_ID plus SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS.",
                                "For external transit mode, configure SECRET_PROVIDER_MODE together with the provider-specific Vault / OpenBao / transit connection properties and verify the provider is reachable from the InboxBridge server.",
                                "For split-key mode, configure the local key fragment plus the secondary transit provider so both trust domains are available before starting re-encryption."), 
                secureStorageConfigured
                        ? List.of("SECURITY_TOKEN_ENCRYPTION_KEY", "SECURITY_TOKEN_ENCRYPTION_KEY_ID", "SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS")
                        : List.of(
                                "SECURITY_TOKEN_ENCRYPTION_KEY",
                                "SECURITY_TOKEN_ENCRYPTION_KEY_ID",
                                "SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS",
                                "SECRET_PROVIDER_MODE",
                                "VAULT_* / OPENBAO_* / split-key provider settings"),
                "secret-management-provider-diagnostics",
                "Review provider diagnostics",
                secureStorageConfigured,
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "provider-health",
                "Active secret provider is healthy and writable",
                providerHealth.writable()
                        ? providerHealth.statusMessage()
                        : "InboxBridge must be able to write with the active key path before re-encryption can start.",
                providerHealth.writable()
                        ? List.of(
                                "The active provider reported itself healthy during the latest backend verification.")
                        : List.of(
                                "Verify the active provider endpoint, credentials, mount / key path, and TLS trust from the InboxBridge server.",
                                "After updating the provider configuration, refresh this page and confirm the provider becomes healthy and writable before requesting re-encryption."),
                providerHealth.mode() == SecretProviderMode.LOCAL
                        ? List.of("SECURITY_TOKEN_ENCRYPTION_KEY", "SECURITY_TOKEN_ENCRYPTION_KEY_ID", "SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS")
                        : List.of("SECRET_PROVIDER_MODE", providerHealth.providerId() + " connection settings"),
                "secret-management-provider-diagnostics",
                "Review provider diagnostics",
                providerHealth.writable(),
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "active-key",
                "An active key version is available",
                activeKeyVersion != null && !activeKeyVersion.isBlank()
                        ? "InboxBridge will target " + activeKeyVersion + "."
                        : "No active key version could be resolved for this deployment.",
                activeKeyVersion != null && !activeKeyVersion.isBlank()
                        ? List.of(
                                "Confirm that the target shown in Current key status is the new key or provider version you intend to migrate to.")
                        : List.of(
                                "Configure the new active key identifier before continuing so InboxBridge knows which key version must become authoritative.",
                                "For local mode, define SECURITY_TOKEN_ENCRYPTION_KEY_ID alongside the active key material.",
                                "For transit or split-key mode, confirm the provider key path resolves to the intended target version."),
                List.of("SECURITY_TOKEN_ENCRYPTION_KEY_ID", "provider key / transit path settings"),
                "secret-reencryption-key-status",
                "Review current key status",
                activeKeyVersion != null && !activeKeyVersion.isBlank(),
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "legacy-key-availability",
                "Every stored secret is currently decryptable",
                unavailableKeyRecordCount == 0
                        ? "No stored records reference unavailable key material."
                        : "Some stored records already reference unavailable key material. Restore those keys before re-encrypting.",
                unavailableKeyRecordCount == 0
                        ? List.of(
                                "Keep the currently configured legacy keys available until the post-run verification confirms no records still depend on them.")
                        : List.of(
                                "Restore every missing legacy key or provider credential that still protects encrypted records.",
                                "Do not start re-encryption until the unavailable-record counter returns to zero, otherwise some secrets will remain unrecoverable."),
                List.of("SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS", "legacy transit / provider credentials"),
                "secret-management-key-usage",
                "Review key usage",
                unavailableKeyRecordCount == 0,
                true));
        requirements.add(new SecretReencryptionRequirementView(
                "no-pending-request",
                "No other re-encryption request is pending",
                requestState != null && RequestStatus.PENDING.name().equals(requestState.status)
                        ? "A re-encryption request is already queued for execution after the cooldown window."
                        : "No queued re-encryption request is currently blocking this action.",
                requestState != null && RequestStatus.PENDING.name().equals(requestState.status)
                        ? List.of(
                                "Wait for the queued request to complete or fail before scheduling another migration.",
                                "If you are testing the workflow, ask an operator to review the cooldown policy instead of submitting duplicate requests.")
                        : List.of(
                                "No existing cooldown-window request is blocking a new re-encryption run."),
                List.of("SECURITY_SECRET_REENCRYPTION_COOLDOWN"),
                requestState != null && RequestStatus.PENDING.name().equals(requestState.status)
                        ? "secret-reencryption-pending-request"
                        : null,
                requestState != null && RequestStatus.PENDING.name().equals(requestState.status)
                        ? "Review queued request"
                        : null,
                requestState == null || !RequestStatus.PENDING.name().equals(requestState.status),
                false));
        requirements.add(new SecretReencryptionRequirementView(
                "recent-reauthentication",
                "This browser session was recently re-authenticated for sensitive actions",
                !reauthenticationRequired
                        ? "No extra step-up verification window is required by server policy."
                        : reauthenticationSatisfied
                                ? "This browser session was recently re-verified and can perform secret re-encryption."
                                : "Re-authenticate this browser session with the current password or a passkey before re-encrypting stored secrets.",
                !reauthenticationRequired
                        ? List.of(
                                "This deployment currently does not require an extra step-up verification window for secret re-encryption.")
                        : reauthenticationSatisfied
                                ? List.of(
                                        "This browser session is already within the server-side re-authentication window for sensitive secret-management actions.")
                                : List.of(
                                        "Use Verify with current password or Verify with passkey in this dialog before confirming re-encryption.",
                                        "If the verification window expires before you submit the request, repeat the verification step in this same browser session."),
                reauthenticationRequired
                        ? List.of("inboxbridge.security.secret-management.reauthentication-ttl")
                        : List.of(),
                reauthenticationRequired ? "secret-reencryption-reauthentication" : null,
                reauthenticationRequired ? "Open session verification" : null,
                !reauthenticationRequired || reauthenticationSatisfied,
                reauthenticationRequired));
        return requirements;
    }

    private List<SecretManagementRetirementRequirementView> buildRetirementRequirements(
            SecretProviderHealth providerHealth,
            SecretManagementRotationPlanView rotationPlan,
            long unavailableKeyRecordCount,
            SystemSecretReencryptionRequest requestState) {
        List<SecretManagementRetirementRequirementView> requirements = new ArrayList<>();
        boolean providerReady = providerHealth != null && providerHealth.writable();
        requirements.add(new SecretManagementRetirementRequirementView(
                "provider-health",
                "Active secret provider is healthy and writable",
                providerReady
                        ? "The active secret provider is healthy and can still read and write protected values."
                        : "InboxBridge cannot verify the active secret provider as healthy and writable right now.",
                providerReady
                        ? List.of("Keep the current active provider configuration available while you finish the retirement procedure.")
                        : List.of(
                                "Fix the active provider configuration first.",
                                "Do not remove any legacy key material until the provider diagnostics report a healthy, writable target again."),
                List.of("SECRET_PROVIDER_MODE", "active provider / key settings"),
                "secret-management-provider-diagnostics",
                "Review provider diagnostics",
                providerReady,
                true));
        boolean noPendingRequest = requestState == null || !RequestStatus.PENDING.name().equals(requestState.status);
        requirements.add(new SecretManagementRetirementRequirementView(
                "no-pending-request",
                "No secret re-encryption request is still pending",
                noPendingRequest
                        ? "No queued secret re-encryption request is still waiting for the cooldown window to finish."
                        : "A secret re-encryption request is still queued and has not executed yet.",
                noPendingRequest
                        ? List.of("No queued rotation run is still outstanding.")
                        : List.of(
                                "Wait for the queued request to complete before removing any legacy key material.",
                                "Re-open the latest status after the queued run finishes so you can verify the final key-usage snapshot."),
                List.of("SECURITY_SECRET_REENCRYPTION_COOLDOWN"),
                noPendingRequest ? null : "secret-management-section",
                noPendingRequest ? null : "Review latest request",
                noPendingRequest,
                true));
        boolean noUnavailableRecords = unavailableKeyRecordCount == 0;
        requirements.add(new SecretManagementRetirementRequirementView(
                "no-unavailable-records",
                "Every stored secret is still decryptable before retirement",
                noUnavailableRecords
                        ? "No stored records reference unavailable key material."
                        : unavailableKeyRecordCount + " stored records still reference unavailable key material.",
                noUnavailableRecords
                        ? List.of("The current deployment can still decrypt every stored secret it knows about.")
                        : List.of(
                                "Restore every missing legacy key or provider credential before retiring any older material.",
                                "Do not remove more key material until the unavailable-record counter returns to zero."),
                List.of("SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS", "legacy transit / provider credentials"),
                "secret-management-key-usage",
                "Review key usage",
                noUnavailableRecords,
                true));
        boolean noRotationNeeded = rotationPlan != null && !rotationPlan.rotationNeeded();
        requirements.add(new SecretManagementRetirementRequirementView(
                "rotation-complete",
                "No encrypted records still depend on a legacy rotation target",
                noRotationNeeded
                        ? "InboxBridge does not currently see any encrypted records that still need rotation or rewrap."
                        : rotationPlan == null
                                ? "InboxBridge cannot verify rotation status yet."
                                : rotationPlan.summary(),
                noRotationNeeded
                        ? List.of("The current key-usage summary shows no records left on non-active targets.")
                        : List.of(
                                "Finish the pending re-encryption or metadata rewrap first.",
                                "Only retire older key material after the rotation plan reports that all stored secrets already match the active target."),
                List.of("active provider / key settings"),
                "secret-management-rotation-plan",
                "Review rotation plan",
                noRotationNeeded,
                true));
        boolean latestVerificationPassed = requestState == null || requestState.lastVerificationPassed == null || requestState.lastVerificationPassed;
        requirements.add(new SecretManagementRetirementRequirementView(
                "latest-verification",
                "The latest persisted secret-management verification did not report a failure",
                latestVerificationPassed
                        ? "No persisted secret-management verification is currently reporting a failed outcome."
                        : "The latest persisted secret-management verification reported a failed or incomplete result.",
                latestVerificationPassed
                        ? List.of(
                                "Keep the latest exported report and your operator notes alongside the active key version before you remove any old material.")
                        : List.of(
                                "Review the latest secret-management request result and fix the reported verification issues first.",
                                "Do not retire legacy material until a refreshed status or a newer completed run no longer reports a failed verification."),
                List.of(),
                "secret-management-section",
                "Review latest request",
                latestVerificationPassed,
                true));
        return requirements;
    }

    private void validateReencryptionReadiness(UserSession currentSession, boolean requireReauthentication) {
        SecretManagementStatusView currentStatus = status(currentSession);
        currentStatus.reencryptionRequirements().stream()
                .filter(requirement -> requirement.blocking()
                        && (requireReauthentication || !"recent-reauthentication".equals(requirement.requirementId())))
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
                && currentStatus.unavailableKeyRecordCount() == 0
                && (currentStatus.rotationPlan() == null || !currentStatus.rotationPlan().rotationNeeded());
        return new SecretReencryptionVerificationView(passed, messages, operatorSaveItems);
    }

    private SecretManagementRotationPlanView buildRotationPlan(
            String activeKeyVersion,
            SecretProviderHealth providerHealth,
            long nonActiveKeyRecordCount,
            long unavailableKeyRecordCount,
            long metadataRewrapRecordCount,
            List<String> metadataRewrapAreas,
            List<SecretManagementKeyUsageView> keyUsage,
            boolean protectedRecordsPresent,
            boolean providerWritable) {
        if (providerHealth == null || !providerWritable || activeKeyVersion == null || activeKeyVersion.isBlank()) {
            return new SecretManagementRotationPlanView(
                    "provider-not-ready",
                    "Rotation target is not ready yet",
                    "InboxBridge cannot safely plan encryption-layer rotation until the active secret-management provider is healthy and writable.",
                    "Fix the active provider configuration first, then refresh the secret-management status before running any rotation operation.",
                    activeKeyVersion,
                    0,
                    unavailableKeyRecordCount,
                    List.of(),
                    false,
                    false,
                    false);
        }
        if (!protectedRecordsPresent) {
            return new SecretManagementRotationPlanView(
                    "no-records",
                    "No encrypted records need rotation yet",
                    "InboxBridge has no stored encrypted records, so there is nothing to rotate at the encryption layer right now.",
                    "Store or update UI-managed secrets normally. Rotation planning will become relevant once encrypted records exist.",
                    activeKeyVersion,
                    0,
                    0,
                    List.of(),
                    false,
                    false,
                    false);
        }
        if (unavailableKeyRecordCount > 0) {
            return new SecretManagementRotationPlanView(
                    "recover-legacy-keys",
                    "Legacy key recovery is required before rotation",
                    unavailableKeyRecordCount + " stored records still reference unavailable key material, so a new rotation run would leave those secrets unrecoverable.",
                    "Restore every missing legacy key or provider credential first. Only then run full re-encryption toward the active target.",
                    activeKeyVersion,
                    nonActiveKeyRecordCount,
                    unavailableKeyRecordCount,
                    impactedAreas(keyUsage, false),
                    true,
                    true,
                    false);
        }
        if (nonActiveKeyRecordCount == 0 && metadataRewrapRecordCount > 0) {
            String planId = providerHealth.mode() == SecretProviderMode.SPLIT_KEY
                    ? "split-key-envelope-rewrap"
                    : "transit-key-rollover";
            String title = providerHealth.mode() == SecretProviderMode.SPLIT_KEY
                    ? "Split-key envelope rewrap is pending"
                    : "Transit key rollover rewrap is pending";
            return new SecretManagementRotationPlanView(
                    planId,
                    title,
                    metadataRewrapRecordCount + " stored records already use the active target metadata but still carry older transit-provider key versions inside the ciphertext envelope.",
                    "Run metadata rewrap so InboxBridge can refresh the outer transit ciphertext to the current provider key version without rewriting plaintext, then validate the provider before retiring older provider-side key versions.",
                    activeKeyVersion,
                    metadataRewrapRecordCount,
                    0,
                    metadataRewrapAreas,
                    true,
                    false,
                    true);
        }
        if (nonActiveKeyRecordCount == 0) {
            return new SecretManagementRotationPlanView(
                    "already-current",
                    "Stored secrets already match the active target",
                    "Every stored encrypted secret already uses the current active provider and key version.",
                    "No encryption-layer rotation is needed right now. Keep the current legacy material only until you have completed your normal validation and retirement checks.",
                    activeKeyVersion,
                    0,
                    0,
                    List.of(),
                    false,
                    false,
                    false);
        }

        List<String> impactedAreas = impactedAreas(keyUsage, false);
        StoredSecretKeyReference activeReference = StoredSecretKeyReference.parse(activeKeyVersion);
        boolean sameProviderOnly = keyUsage.stream()
                .filter(usage -> !usage.active())
                .allMatch(usage -> StoredSecretKeyReference.parse(usage.keyVersion()).providerId().equals(activeReference.providerId()));
        String planId;
        String title;
        if (sameProviderOnly) {
            planId = switch (providerHealth.mode()) {
                case LOCAL -> "local-key-rotation";
                case OPENBAO_TRANSIT, VAULT_TRANSIT -> "transit-key-rotation";
                case SPLIT_KEY -> "split-key-rotation";
            };
            title = switch (providerHealth.mode()) {
                case LOCAL -> "Local-key rotation is pending";
                case OPENBAO_TRANSIT, VAULT_TRANSIT -> "Transit key migration is pending";
                case SPLIT_KEY -> "Split-key envelope rotation is pending";
            };
        } else {
            planId = "provider-migration";
            title = "Provider migration is pending";
        }
        return new SecretManagementRotationPlanView(
                planId,
                title,
                nonActiveKeyRecordCount + " stored records still depend on older or different encryption targets and must be rewritten to " + activeKeyVersion + ".",
                "Keep legacy keys and provider credentials available, run full re-encryption, then validate mailbox, destination, and OAuth flows before retiring the previous secret path.",
                activeKeyVersion,
                nonActiveKeyRecordCount,
                0,
                impactedAreas,
                true,
                true,
                false);
    }

    private List<String> impactedAreas(List<SecretManagementKeyUsageView> keyUsage, boolean includeActive) {
        return keyUsage.stream()
                .filter(usage -> includeActive || !usage.active())
                .flatMap(usage -> List.of(usage.areas().split(",")).stream())
                .map(String::trim)
                .filter(area -> !area.isBlank())
                .distinct()
                .toList();
    }

    private RotationNeedAccumulator collectMetadataRewrapCandidates() {
        RotationNeedAccumulator accumulator = new RotationNeedAccumulator();
        for (OAuthCredential credential : oAuthCredentialRepository.listAll()) {
            if (isMetadataRewrapCandidate(credential.refreshTokenCiphertext, credential.refreshTokenNonce, credential.keyVersion)
                    || isMetadataRewrapCandidate(credential.accessTokenCiphertext, credential.accessTokenNonce, credential.keyVersion)) {
                accumulator.add("oauth-credentials");
            }
        }
        for (UserEmailAccount account : userEmailAccountRepository.listAll()) {
            if (isMetadataRewrapCandidate(account.passwordCiphertext, account.passwordNonce, account.keyVersion)
                    || isMetadataRewrapCandidate(account.oauthRefreshTokenCiphertext, account.oauthRefreshTokenNonce, account.keyVersion)) {
                accumulator.add("source-mailboxes");
            }
        }
        for (UserMailDestinationConfig config : userMailDestinationConfigRepository.listAll()) {
            if (isMetadataRewrapCandidate(config.passwordCiphertext, config.passwordNonce, config.keyVersion)) {
                accumulator.add("destination-mailboxes");
            }
        }
        for (UserGmailConfig config : userGmailConfigRepository.listAll()) {
            if (isMetadataRewrapCandidate(config.clientIdCiphertext, config.clientIdNonce, config.keyVersion)
                    || isMetadataRewrapCandidate(config.clientSecretCiphertext, config.clientSecretNonce, config.keyVersion)
                    || isMetadataRewrapCandidate(config.refreshTokenCiphertext, config.refreshTokenNonce, config.keyVersion)) {
                accumulator.add("gmail-user-config");
            }
        }
        SystemOAuthAppSettings systemOAuth = systemOAuthAppSettingsRepository.findSingleton().orElse(null);
        if (systemOAuth != null
                && (isMetadataRewrapCandidate(systemOAuth.googleClientIdCiphertext, systemOAuth.googleClientIdNonce, systemOAuth.keyVersion)
                        || isMetadataRewrapCandidate(systemOAuth.googleClientSecretCiphertext, systemOAuth.googleClientSecretNonce, systemOAuth.keyVersion)
                        || isMetadataRewrapCandidate(systemOAuth.googleRefreshTokenCiphertext, systemOAuth.googleRefreshTokenNonce, systemOAuth.keyVersion)
                        || isMetadataRewrapCandidate(systemOAuth.microsoftClientIdCiphertext, systemOAuth.microsoftClientIdNonce, systemOAuth.keyVersion)
                        || isMetadataRewrapCandidate(systemOAuth.microsoftClientSecretCiphertext, systemOAuth.microsoftClientSecretNonce, systemOAuth.keyVersion))) {
            accumulator.add("system-oauth");
        }
        SystemAuthSecuritySetting authSecurity = systemAuthSecuritySettingRepository.findSingleton().orElse(null);
        if (authSecurity != null
                && (isMetadataRewrapCandidate(authSecurity.registrationTurnstileSecretCiphertext, authSecurity.registrationTurnstileSecretNonce, authSecurity.keyVersion)
                        || isMetadataRewrapCandidate(authSecurity.registrationHcaptchaSecretCiphertext, authSecurity.registrationHcaptchaSecretNonce, authSecurity.keyVersion)
                        || isMetadataRewrapCandidate(authSecurity.geoIpIpinfoTokenCiphertext, authSecurity.geoIpIpinfoTokenNonce, authSecurity.keyVersion))) {
            accumulator.add("auth-security");
        }
        return accumulator;
    }

    private boolean isMetadataRewrapCandidate(String ciphertext, String nonce, String keyVersion) {
        return secretEncryptionService.canMetadataRewrapToActive(ciphertext, nonce, keyVersion);
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
            SecretReencryptionPreviewView requestPreview,
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
        requestState.requestPreviewJson = writeJson(requestPreview);
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
        SecretReencryptionPreviewView plannedPreview = readRequestPreview(requestState);
        List<SecretReencryptionAreaResultView> areas = readAreaResults(requestState);
        SecretReencryptionFollowUpView followUp = readLastFollowUp(requestState);
        SecretReencryptionVerificationView verification = readLastVerification(requestState);
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
                Boolean.TRUE.equals(requestState.lastVerificationPassed),
                plannedPreview,
                requestState.lastTotalRecordsUpdated,
                requestState.lastTotalSecretValuesReencrypted,
                requestState.lastTotalFullReencryptionCount,
                requestState.lastTotalMetadataRewrapCount,
                areas,
                followUp,
                verification);
    }

    private SecretReencryptionPreviewView readRequestPreview(SystemSecretReencryptionRequest requestState) {
        return readJson(requestState == null ? null : requestState.requestPreviewJson, SecretReencryptionPreviewView.class);
    }

    private List<SecretReencryptionAreaResultView> readAreaResults(SystemSecretReencryptionRequest requestState) {
        List<SecretReencryptionAreaResultView> values = readJson(
                requestState == null ? null : requestState.lastAreaResultsJson,
                new TypeReference<List<SecretReencryptionAreaResultView>>() {
                });
        return values == null ? List.of() : values;
    }

    private SecretReencryptionFollowUpView readLastFollowUp(SystemSecretReencryptionRequest requestState) {
        SecretReencryptionFollowUpView followUp = readJson(
                requestState == null ? null : requestState.lastFollowUpJson,
                SecretReencryptionFollowUpView.class);
        return followUp == null ? new SecretReencryptionFollowUpView(0, 0, 0) : followUp;
    }

    private SecretReencryptionVerificationView readLastVerification(SystemSecretReencryptionRequest requestState) {
        return readJson(requestState == null ? null : requestState.lastVerificationJson, SecretReencryptionVerificationView.class);
    }

    private void clearLastExecutionSnapshot(SystemSecretReencryptionRequest requestState) {
        requestState.lastTotalRecordsUpdated = 0;
        requestState.lastTotalSecretValuesReencrypted = 0;
        requestState.lastTotalFullReencryptionCount = 0;
        requestState.lastTotalMetadataRewrapCount = 0;
        requestState.lastAreaResultsJson = null;
        requestState.lastFollowUpJson = null;
        requestState.lastVerificationJson = null;
    }

    private SecretManagementRetirementReviewView latestRetirementReview() {
        if (systemSecretRetirementReviewRepository == null) {
            return null;
        }
        Optional<SystemSecretRetirementReview> latest = systemSecretRetirementReviewRepository.findLatest();
        return latest.map(this::toRetirementReviewView).orElse(null);
    }

    private List<SecretManagementRetirementReviewView> recentRetirementReviews() {
        if (systemSecretRetirementReviewRepository == null) {
            return List.of();
        }
        return systemSecretRetirementReviewRepository.listRecent(5).stream()
                .map(this::toRetirementReviewView)
                .toList();
    }

    private SecretManagementRetirementReviewView toRetirementReviewView(SystemSecretRetirementReview review) {
        List<String> configuredLegacyKeyIds = readJson(
                review == null ? null : review.legacyKeyIdsJson,
                new TypeReference<List<String>>() {
                });
        List<String> unsatisfiedRequirementIds = readJson(
                review == null ? null : review.unsatisfiedRequirementIdsJson,
                new TypeReference<List<String>>() {
                });
        List<String> completionUnsatisfiedCheckIds = readJson(
                review == null ? null : review.completionUnsatisfiedCheckIdsJson,
                new TypeReference<List<String>>() {
                });
        return new SecretManagementRetirementReviewView(
                review.id,
                review.reviewedAt,
                review.reviewedByUserId,
                review.reviewedByUsername,
                review.providerId,
                review.activeKeyVersion,
                review.activeKeyId,
                configuredLegacyKeyIds == null ? List.of() : configuredLegacyKeyIds,
                review.safeToRetireLegacyKeys,
                review.legacyKeyRetirementReady,
                review.nonActiveKeyRecordCount,
                review.unavailableKeyRecordCount,
                review.latestRequestStatus,
                review.blockingRequirementsRemaining,
                unsatisfiedRequirementIds == null ? List.of() : unsatisfiedRequirementIds,
                review.completionVerifiedAt == null && (review.completionStatus == null || review.completionStatus.isBlank())
                        ? null
                        : new SecretManagementRetirementCompletionView(
                                review.completionVerifiedAt,
                                review.completionVerifiedByUserId,
                                review.completionVerifiedByUsername,
                                review.completionStatus,
                                review.completionMessage,
                                completionUnsatisfiedCheckIds == null ? List.of() : completionUnsatisfiedCheckIds));
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper().writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to persist secret re-encryption snapshot data.", error);
        }
    }

    private <T> T readJson(String rawValue, Class<T> type) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return mapper().readValue(rawValue, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to read persisted secret re-encryption snapshot data.", error);
        }
    }

    private <T> T readJson(String rawValue, TypeReference<T> type) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return mapper().readValue(rawValue, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to read persisted secret re-encryption snapshot data.", error);
        }
    }

    private Duration reencryptionCooldown() {
        return secretManagementPolicyConfig == null ? Duration.ofHours(12) : secretManagementPolicyConfig.reencryptionCooldown();
    }

    private Duration reencryptionReauthenticationTtl() {
        return secretManagementPolicyConfig == null ? Duration.ofMinutes(10) : secretManagementPolicyConfig.reauthenticationTtl();
    }

    private boolean allowImmediateReencryptOverride() {
        return secretManagementPolicyConfig != null && secretManagementPolicyConfig.allowImmediateReencryptOverride();
    }

    private boolean reencryptionReauthenticationRequired() {
        Duration ttl = reencryptionReauthenticationTtl();
        return !ttl.isZero() && !ttl.isNegative();
    }

    private Instant reencryptionReauthenticationExpiresAt(UserSession currentSession) {
        if (currentSession == null || currentSession.lastSensitiveAuthAt == null || !reencryptionReauthenticationRequired()) {
            return null;
        }
        return currentSession.lastSensitiveAuthAt.plus(reencryptionReauthenticationTtl());
    }

    private boolean reencryptionReauthenticationSatisfied(UserSession currentSession) {
        if (!reencryptionReauthenticationRequired()) {
            return true;
        }
        Instant expiresAt = reencryptionReauthenticationExpiresAt(currentSession);
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    private void requireReauthenticationCapableSession(UserSession currentSession) {
        if (currentSession == null || currentSession.id == null) {
            throw new IllegalArgumentException("Sensitive secret-management actions require a current browser session.");
        }
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

    private static final class RotationNeedAccumulator {
        private long recordCount;
        private final LinkedHashSet<String> areas = new LinkedHashSet<>();

        private void add(String area) {
            recordCount++;
            areas.add(area);
        }

        private List<String> areas() {
            return List.copyOf(areas);
        }
    }

    private static final class AreaRewriteAccumulator {
        private int secretValuesReencrypted;
        private int fullReencryptionCount;
        private int metadataRewrapCount;

        private void add(RewriteOutcome outcome) {
            if (!outcome.updated()) {
                return;
            }
            secretValuesReencrypted++;
            if (outcome.metadataRewrap()) {
                metadataRewrapCount++;
            } else {
                fullReencryptionCount++;
            }
        }
    }

    private record RewriteOutcome(boolean updated, boolean metadataRewrap) {
        private static final RewriteOutcome SKIPPED = new RewriteOutcome(false, false);
        private static final RewriteOutcome FULL_REENCRYPTION = new RewriteOutcome(true, false);
        private static final RewriteOutcome METADATA_REWRAP = new RewriteOutcome(true, true);
    }

    private SecretProviderResolver providerResolver() {
        if (secretProviderResolver == null) {
            SecretProviderResolver resolver = new SecretProviderResolver();
            resolver.setLocalSecretKeyProvider(localSecretKeyProvider);
            secretProviderResolver = resolver;
        }
        return secretProviderResolver;
    }

    private ObjectMapper mapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper().findAndRegisterModules();
        }
        return objectMapper;
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
