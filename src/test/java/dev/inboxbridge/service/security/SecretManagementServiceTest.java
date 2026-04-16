package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.dto.SecretManagementMigrationGuideView;
import dev.inboxbridge.dto.SecretManagementRecoveryGuideView;
import dev.inboxbridge.dto.SecretReencryptionResultView;
import dev.inboxbridge.persistence.AppUser;
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
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.auth.UserSessionService;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.mail.EnvSourceService;
import dev.inboxbridge.service.oauth.OAuthCredentialService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.remote.RemoteSessionService;

class SecretManagementServiceTest {

    @Test
    void reportsKeyUsageAcrossSecretBearingAreas() {
        SecretManagementService service = configuredService();

        SecretManagementStatusView view = service.status();

        assertTrue(view.secureStorageConfigured());
        assertEquals("LOCAL", view.mode());
        assertEquals("LOCAL", view.providerId());
        assertTrue(view.providerHealthy());
        assertTrue(view.providerWritable());
        assertEquals("Local secret provider is ready.", view.providerStatusMessage());
        assertEquals(1, view.providerComponents().size());
        assertEquals("local-key", view.providerComponents().getFirst().componentId());
        assertTrue(view.providerComponents().getFirst().healthy());
        assertEquals("LOCAL:v2", view.activeKeyVersion());
        assertEquals("v2", view.activeKeyId());
        assertEquals(4, view.modeAssessments().size());
        assertTrue(view.modeAssessments().stream().anyMatch(assessment ->
                "LOCAL".equals(assessment.mode()) && assessment.current() && assessment.writable()));
        assertTrue(view.modeAssessments().stream().anyMatch(assessment ->
                "OPENBAO_TRANSIT".equals(assessment.mode()) && !assessment.current() && !assessment.writable()));
        assertEquals("local-key-rotation", view.rotationPlan().planId());
        assertTrue(view.rotationPlan().rotationNeeded());
        assertEquals(List.of("v1"), view.configuredLegacyKeyIds());
        assertEquals(5, view.protectedRecordCount());
        assertEquals(3, view.activeKeyRecordCount());
        assertEquals(2, view.nonActiveKeyRecordCount());
        assertEquals(0, view.unavailableKeyRecordCount());
        assertTrue(view.envManagedMailboxSecretsAllowed());
        assertEquals(1, view.configuredEnvManagedSourceCount());
        assertTrue(view.envManagedGoogleRefreshTokenConfigured());
        assertFalse(view.reauthenticationRequired());
        assertTrue(view.reauthenticationSatisfied());
        assertEquals(null, view.reauthenticationExpiresAt());
        assertFalse(view.safeToRetireLegacyKeys());
        assertFalse(view.legacyKeyRetirementReady());
        assertNotNull(view.reencryptionPreview());
        assertEquals("LOCAL:v2", view.reencryptionPreview().activeKeyVersion());
        assertEquals(2, view.reencryptionPreview().totalRecordsPendingUpdate());
        assertEquals(2, view.reencryptionPreview().totalSecretValuesPendingRewrite());
        assertEquals(2, view.reencryptionPreview().totalFullReencryptionCount());
        assertEquals(0, view.reencryptionPreview().totalMetadataRewrapCount());
        assertEquals(2, view.keyUsage().size());
        assertEquals("LOCAL:v2", view.keyUsage().getFirst().keyVersion());
        assertEquals("v1", view.keyUsage().get(1).keyVersion());
        assertTrue(view.keyUsage().get(1).availableForDecryption());
        assertTrue(view.retirementRequirements().stream()
                .anyMatch(requirement -> "rotation-complete".equals(requirement.requirementId()) && !requirement.satisfied()));
    }

    @Test
    void flagsUnavailableKeyReferencesWhenLegacyKeyWasRemoved() {
        SecretManagementService service = configuredService();
        service.localSecretKeyProvider.setTokenEncryptionLegacyKeys("");

        SecretManagementStatusView view = service.status();

        assertEquals(2, view.unavailableKeyRecordCount());
        assertFalse(view.keyUsage().get(1).availableForDecryption());
        assertFalse(view.safeToRetireLegacyKeys());
        assertFalse(view.legacyKeyRetirementReady());
        assertTrue(view.retirementRequirements().stream()
                .anyMatch(requirement -> "no-unavailable-records".equals(requirement.requirementId()) && !requirement.satisfied()));
    }

    @Test
    void reportsLocalModeAsUnavailableWhenSecureStorageIsMissing() {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey("replace-me");
        provider.setTokenEncryptionKeyId("v1");
        service.setLocalSecretKeyProvider(provider);
        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of()));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of()));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of()));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of()));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(null));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(null));

        SecretManagementStatusView view = service.status();

        assertFalse(view.secureStorageConfigured());
        assertEquals("LOCAL", view.mode());
        assertFalse(view.providerHealthy());
        assertFalse(view.providerWritable());
        assertEquals("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.", view.providerStatusMessage());
        assertTrue(view.envManagedMailboxSecretsAllowed());
        assertEquals(0, view.configuredEnvManagedSourceCount());
        assertFalse(view.envManagedGoogleRefreshTokenConfigured());
        assertTrue(view.keyUsage().isEmpty());
        assertEquals(1, view.providerComponents().size());
        assertFalse(view.providerComponents().getFirst().writable());
        assertEquals("provider-not-ready", view.rotationPlan().planId());
        assertTrue(view.reencryptionRequirements().stream()
                .anyMatch(requirement -> "secure-storage".equals(requirement.requirementId())
                        && requirement.configReferences().contains("SECRET_PROVIDER_MODE")
                        && "Review provider diagnostics".equals(requirement.actionLabel())));
        assertFalse(view.legacyKeyRetirementReady());
        assertTrue(view.retirementRequirements().stream()
                .anyMatch(requirement -> "provider-health".equals(requirement.requirementId()) && !requirement.satisfied()));
    }

    @Test
    void statusReportsEnvManagedMailboxPolicyAndConfiguredEnvUsage() {
        SecretManagementService service = configuredService();
        EnvSourceService envSourceService = new EnvSourceService() {
            @Override
            public boolean envManagedMailboxSecretsAllowed() {
                return false;
            }

            @Override
            public long configuredSourceCountIgnoringPolicy() {
                return 2;
            }
        };
        SystemOAuthAppSettingsService systemOAuthAppSettingsService = new SystemOAuthAppSettingsService() {
            @Override
            public boolean envManagedGoogleRefreshTokenConfigured() {
                return true;
            }
        };
        service.setEnvSourceService(envSourceService);
        service.setSystemOAuthAppSettingsService(systemOAuthAppSettingsService);

        SecretManagementStatusView view = service.status();

        assertFalse(view.envManagedMailboxSecretsAllowed());
        assertEquals(2, view.configuredEnvManagedSourceCount());
        assertTrue(view.envManagedGoogleRefreshTokenConfigured());
    }

    @Test
    void reportsTransitModeStatusWhenProviderIsHealthy() {
        SecretManagementService service = configuredService();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setProviderMode("VAULT_TRANSIT");
        resolver.setVaultUrl("https://vault.internal");
        resolver.setVaultToken("token");
        resolver.setVaultMount("transit");
        resolver.setVaultKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));
        service.setSecretProviderResolver(resolver);

        SecretManagementStatusView view = service.status();

        assertTrue(view.secureStorageConfigured());
        assertEquals("VAULT_TRANSIT", view.mode());
        assertEquals("VAULT_TRANSIT", view.providerId());
        assertTrue(view.providerHealthy());
        assertTrue(view.providerWritable());
        assertEquals("VAULT_TRANSIT transit provider is ready.", view.providerStatusMessage());
        assertEquals(1, view.providerComponents().size());
        assertEquals("vault_transit-transit", view.providerComponents().getFirst().componentId());
        assertEquals("provider-migration", view.rotationPlan().planId());
        assertEquals("VAULT_TRANSIT:inboxbridge", view.activeKeyVersion());
        assertEquals("inboxbridge", view.activeKeyId());
        assertEquals(List.of(), view.configuredLegacyKeyIds());
        assertTrue(view.modeAssessments().stream().anyMatch(assessment ->
                "VAULT_TRANSIT".equals(assessment.mode())
                        && assessment.current()
                        && assessment.writable()
                        && "VAULT_TRANSIT:inboxbridge".equals(assessment.activeKeyVersion())));
    }

    @Test
    void statusAssessesFutureSplitKeyModeWhenTransitSecondaryIsConfigured() {
        SecretManagementService service = configuredService();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setProviderMode("LOCAL");
        resolver.setSplitSecondaryMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));
        service.setSecretProviderResolver(resolver);

        SecretManagementStatusView view = service.status();

        assertTrue(view.modeAssessments().stream().anyMatch(assessment ->
                "SPLIT_KEY".equals(assessment.mode())
                        && !assessment.current()
                        && assessment.writable()
                        && "SPLIT_KEY:LOCAL=v2|OPENBAO_TRANSIT=inboxbridge".equals(assessment.activeKeyVersion())));
    }

    @Test
    void reportsSplitKeyModeStatusWhenProviderIsHealthy() {
        SecretManagementService service = configuredService();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));
        resolver.setProviderMode("SPLIT_KEY");
        resolver.setSplitSecondaryMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        service.setSecretProviderResolver(resolver);

        SecretManagementStatusView view = service.status();

        assertTrue(view.secureStorageConfigured());
        assertEquals("SPLIT_KEY", view.mode());
        assertEquals("SPLIT_KEY", view.providerId());
        assertTrue(view.providerHealthy());
        assertTrue(view.providerWritable());
        assertEquals(2, view.providerComponents().size());
        assertEquals("split-secondary", view.providerComponents().get(1).componentId());
        assertTrue(view.providerComponents().get(1).writable());
        assertEquals("provider-migration", view.rotationPlan().planId());
        assertEquals("SPLIT_KEY:LOCAL=v2|OPENBAO_TRANSIT=inboxbridge", view.activeKeyVersion());
        assertEquals("LOCAL:v2 + OPENBAO_TRANSIT:inboxbridge", view.activeKeyId());
    }

    @Test
    void migrationGuideExplainsHowToSwitchToAReadyTransitTarget() {
        SecretManagementService service = configuredService();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setProviderMode("LOCAL");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));
        service.setSecretProviderResolver(resolver);

        SecretManagementMigrationGuideView guide = service.migrationGuide("OPENBAO_TRANSIT", null);

        assertEquals("LOCAL", guide.currentMode());
        assertEquals("OPENBAO_TRANSIT", guide.targetMode());
        assertTrue(guide.targetReady());
        assertFalse(guide.current());
        assertTrue(guide.executionMethod().contains("full stored-secret re-encryption"));
        assertTrue(guide.checks().stream().allMatch(check -> check.satisfied()));
        assertTrue(guide.switchSteps().stream().anyMatch(step -> step.contains("SECRET_PROVIDER_MODE=OPENBAO_TRANSIT")));
    }

    @Test
    void migrationGuideFlagsUnavailableRecordsBeforeProviderSwitch() {
        SecretManagementService service = configuredService();
        service.localSecretKeyProvider.setTokenEncryptionLegacyKeys("");

        SecretManagementMigrationGuideView guide = service.migrationGuide("VAULT_TRANSIT", null);

        assertFalse(guide.targetReady());
        assertTrue(guide.checks().stream().anyMatch(check ->
                "no-unavailable-records".equals(check.checkId()) && !check.satisfied()));
        assertTrue(guide.beforeSwitchSteps().stream().anyMatch(step -> step.contains("cannot currently be decrypted")));
    }

    @Test
    void recoveryGuideExplainsHowToContainAndRollbackAfterFailedRun() {
        SecretManagementService service = configuredService();
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository(failedRequestState()));

        SecretManagementRecoveryGuideView guide = service.recoveryGuide(null);

        assertEquals("FAILED", guide.latestRequestStatus());
        assertEquals("v2", guide.currentTarget().summary());
        assertEquals("v2", guide.latestRequestTarget().summary());
        assertFalse(guide.retryReady());
        assertTrue(guide.retryRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && !requirement.satisfied()));
        assertTrue(guide.rollbackRecommended());
        assertTrue(guide.containmentSteps().stream().anyMatch(step -> step.contains("Do not remove any legacy key material")));
        assertTrue(guide.rollbackSteps().stream().anyMatch(step -> step.contains("revert SECRET_PROVIDER_MODE")));
    }

    @Test
    void recoveryGuideExplainsPostRunWarningsWithoutInventingServerSideRollbackTarget() {
        SecretManagementService service = configuredService();
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository(completedRequestStateWithWarning()));

        SecretManagementRecoveryGuideView guide = service.recoveryGuide(null);

        assertEquals("COMPLETED", guide.latestRequestStatus());
        assertFalse(guide.retryReady());
        assertTrue(guide.retryRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && !requirement.satisfied()));
        assertFalse(guide.latestRequestMessage().isBlank());
        assertTrue(guide.validationSteps().stream().anyMatch(step -> step.contains("Validate mailbox polling")));
        assertTrue(guide.evidenceItems().stream().anyMatch(item -> item.contains("Current active provider mode")));
    }

    @Test
    void blockedRequestRecoveryGuideExplainsTargetDriftAndRequiresReviewBeforeRetry() {
        SecretManagementService service = configuredService(Duration.ofHours(2), false);
        SecretReencryptionResultView scheduled = service.reencryptAllStoredSecrets(
                adminUser(),
                new dev.inboxbridge.dto.SecretReencryptionRequest(false, false, false, false));

        service.localSecretKeyProvider.setTokenEncryptionKeyId("v3");
        service.executeDueReencryptionRequestsAt(scheduled.executeAfter().plusSeconds(1));

        SecretManagementRecoveryGuideView guide = service.recoveryGuide(null);

        assertEquals("BLOCKED", guide.latestRequestStatus());
        assertEquals("v3", guide.currentTarget().summary());
        assertEquals("v2", guide.latestRequestTarget().summary());
        assertFalse(guide.retryReady());
        assertTrue(guide.triggerReason().contains("BLOCKED"));
        assertTrue(guide.containmentSteps().stream().anyMatch(step -> step.contains("queued request target and the current active target")));
        assertTrue(guide.retryRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && !requirement.satisfied()));
    }

    @Test
    void recordRecoveryReviewPersistsAnAuditableSnapshotForTheLatestFailedRequest() {
        SecretManagementService service = configuredService();
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository(failedRequestState()));

        SecretManagementStatusView updatedStatus = service.recordRecoveryReview(adminUser(), null);

        assertNotNull(updatedStatus.latestRecoveryReview());
        assertEquals("admin", updatedStatus.latestRecoveryReview().reviewedByUsername());
        assertEquals("FAILED", updatedStatus.latestRecoveryReview().latestRequestStatus());
        assertEquals(1, updatedStatus.recentRecoveryReviews().size());
        assertTrue(updatedStatus.reencryptionRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && requirement.satisfied()));
    }

    @Test
    void statusBlocksRetryAndRetirementUntilTheLatestFailedRequestHasARecordedRecoveryReview() {
        SecretManagementService service = configuredService();
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository(failedRequestState()));

        SecretManagementStatusView blockedStatus = service.status();

        assertFalse(blockedStatus.reencryptionReady());
        assertFalse(blockedStatus.legacyKeyRetirementReady());
        assertTrue(blockedStatus.reencryptionRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && !requirement.satisfied()));
        assertTrue(blockedStatus.retirementRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && !requirement.satisfied()));

        SecretManagementStatusView acknowledgedStatus = service.recordRecoveryReview(adminUser(), null);

        assertTrue(acknowledgedStatus.reencryptionRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && requirement.satisfied()));
        assertTrue(acknowledgedStatus.retirementRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && requirement.satisfied()));
    }

    @Test
    void recordRetirementReviewPersistsAnAuditableSnapshot() {
        SecretManagementService service = configuredService();

        SecretManagementStatusView updatedStatus = service.recordRetirementReview(adminUser(), null);

        assertNotNull(updatedStatus.latestRetirementReview());
        assertEquals("admin", updatedStatus.latestRetirementReview().reviewedByUsername());
        assertEquals("LOCAL", updatedStatus.latestRetirementReview().providerId());
        assertEquals("LOCAL:v2", updatedStatus.latestRetirementReview().activeKeyVersion());
        assertEquals(1, updatedStatus.latestRetirementReview().blockingRequirementsRemaining());
        assertTrue(updatedStatus.latestRetirementReview().unsatisfiedRequirementIds().contains("rotation-complete"));
        assertEquals(1, updatedStatus.recentRetirementReviews().size());
        assertEquals(updatedStatus.latestRetirementReview().reviewId(), updatedStatus.recentRetirementReviews().getFirst().reviewId());
    }

    @Test
    void verifyRetirementCompletionPersistsVerifiedCleanupWhenLiveStatusMatchesExpectedPostCleanupState() {
        SecretManagementService service = configuredRetirementReadyService();
        service.recordRetirementReview(adminUser(), null);
        SecretManagementStatusView updatedStatus = service.verifyRetirementCompletion(adminUser(), null);

        assertNotNull(updatedStatus.latestRetirementReview());
        assertNotNull(updatedStatus.latestRetirementReview().completion());
        assertEquals("VERIFIED", updatedStatus.latestRetirementReview().completion().status());
        assertTrue(updatedStatus.latestRetirementReview().completion().unsatisfiedCheckIds().isEmpty());
    }

    @Test
    void verifyRetirementCompletionBlocksWhenLegacyCleanupStillHasDriftOrConfiguredLegacyKeys() {
        SecretManagementService service = configuredService();
        service.recordRetirementReview(adminUser(), null);

        SecretManagementStatusView updatedStatus = service.verifyRetirementCompletion(adminUser(), null);

        assertNotNull(updatedStatus.latestRetirementReview());
        assertNotNull(updatedStatus.latestRetirementReview().completion());
        assertEquals("BLOCKED", updatedStatus.latestRetirementReview().completion().status());
        assertTrue(updatedStatus.latestRetirementReview().completion().unsatisfiedCheckIds().contains("legacy-key-config-removed"));
        assertTrue(updatedStatus.latestRetirementReview().completion().unsatisfiedCheckIds().contains("live-retirement-ready"));
    }

    @Test
    void reportsSplitKeyComponentDiagnosticsWhenSecondaryModeIsMissing() {
        SecretManagementService service = configuredService();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setProviderMode("SPLIT_KEY");
        service.setSecretProviderResolver(resolver);

        SecretManagementStatusView view = service.status();

        assertEquals("SPLIT_KEY", view.mode());
        assertEquals(2, view.providerComponents().size());
        assertEquals("split-secondary", view.providerComponents().get(1).componentId());
        assertFalse(view.providerComponents().get(1).healthy());
        assertEquals("provider-not-ready", view.rotationPlan().planId());
        assertTrue(view.providerComponents().get(1).configReferences().contains("SECRET_PROVIDER_SPLIT_SECONDARY_MODE"));
    }

    @Test
    void statusReportsTransitKeyRolloverWhenActiveProviderHasNewerTransitVersion() {
        SecretManagementService service = configuredTransitRolloverService(SecretProviderMode.OPENBAO_TRANSIT, null);

        SecretManagementStatusView view = service.status();

        assertEquals("transit-key-rollover", view.rotationPlan().planId());
        assertTrue(view.rotationPlan().rotationNeeded());
        assertTrue(view.rotationPlan().metadataRewrapSupported());
        assertFalse(view.rotationPlan().requiresFullReencryption());
        assertEquals(2, view.rotationPlan().affectedRecordCount());
        assertNotNull(view.reencryptionPreview());
        assertEquals(2, view.reencryptionPreview().totalRecordsPendingUpdate());
        assertEquals(2, view.reencryptionPreview().totalSecretValuesPendingRewrite());
        assertEquals(0, view.reencryptionPreview().totalFullReencryptionCount());
        assertEquals(2, view.reencryptionPreview().totalMetadataRewrapCount());
        assertFalse(view.safeToRetireLegacyKeys());
    }

    @Test
    void reencryptAllStoredSecretsUsesMetadataRewrapForTransitKeyRollover() {
        StubTransitSecretProvider activeTransitProvider = new StubTransitSecretProvider(true, 2);
        SecretManagementService service = configuredTransitRolloverService(SecretProviderMode.OPENBAO_TRANSIT, activeTransitProvider);

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets();
        SecretManagementStatusView status = service.status();

        assertEquals(2, result.totalRecordsUpdated());
        assertEquals(2, result.totalSecretValuesReencrypted());
        assertEquals(0, result.totalFullReencryptionCount());
        assertEquals(2, result.totalMetadataRewrapCount());
        assertEquals(2, activeTransitProvider.rewrapCalls());
        assertEquals(0, activeTransitProvider.decryptCalls());
        assertEquals("already-current", status.rotationPlan().planId());
        assertTrue(status.safeToRetireLegacyKeys());
        assertTrue(status.legacyKeyRetirementReady());
    }

    @Test
    void reencryptAllStoredSecretsCanMigrateLocalRecordsIntoTransitProvider() {
        SecretManagementService service = configuredService();
        StubTransitSecretProvider transitSecretProvider = new StubTransitSecretProvider(true);
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setTransitSecretProvider(transitSecretProvider);
        resolver.setProviderMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        service.setSecretProviderResolver(resolver);
        SecretEncryptionService transitEncryptionService = new SecretEncryptionService();
        transitEncryptionService.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        transitEncryptionService.setSecretProviderResolver(resolver);
        transitEncryptionService.setTransitSecretProvider(transitSecretProvider);
        service.setSecretEncryptionService(transitEncryptionService);

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets();
        SecretManagementStatusView status = service.status();

        assertEquals("OPENBAO_TRANSIT:inboxbridge", result.activeKeyVersion());
        assertEquals(5, result.totalRecordsUpdated());
        assertEquals(5, result.totalSecretValuesReencrypted());
        assertEquals(5, result.totalFullReencryptionCount());
        assertEquals(0, result.totalMetadataRewrapCount());
        assertEquals(5, status.protectedRecordCount());
        assertEquals(5, status.activeKeyRecordCount());
        assertEquals(0, status.nonActiveKeyRecordCount());
        assertTrue(status.safeToRetireLegacyKeys());
        assertTrue(status.legacyKeyRetirementReady());
    }

    @Test
    void reencryptAllStoredSecretsCanMigrateLocalRecordsIntoSplitKeyProvider() {
        SecretManagementService service = configuredService();
        StubTransitSecretProvider transitSecretProvider = new StubTransitSecretProvider(true);
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setTransitSecretProvider(transitSecretProvider);
        resolver.setProviderMode("SPLIT_KEY");
        resolver.setSplitSecondaryMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        service.setSecretProviderResolver(resolver);
        SecretEncryptionService splitEncryptionService = new SecretEncryptionService();
        splitEncryptionService.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        splitEncryptionService.setSecretProviderResolver(resolver);
        splitEncryptionService.setTransitSecretProvider(transitSecretProvider);
        service.setSecretEncryptionService(splitEncryptionService);

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets();
        SecretManagementStatusView status = service.status();

        assertEquals("SPLIT_KEY:LOCAL=v2|OPENBAO_TRANSIT=inboxbridge", result.activeKeyVersion());
        assertEquals(5, result.totalRecordsUpdated());
        assertEquals(5, result.totalSecretValuesReencrypted());
        assertEquals(5, result.totalFullReencryptionCount());
        assertEquals(0, result.totalMetadataRewrapCount());
        assertEquals(5, status.protectedRecordCount());
        assertEquals(5, status.activeKeyRecordCount());
        assertEquals(0, status.nonActiveKeyRecordCount());
        assertTrue(status.safeToRetireLegacyKeys());
        assertEquals("SPLIT_KEY:LOCAL=v2|OPENBAO_TRANSIT=inboxbridge", status.keyUsage().getFirst().keyVersion());
        assertTrue(status.legacyKeyRetirementReady());
    }

    @Test
    void reencryptAllStoredSecretsRewritesLegacyRecordsUnderActiveKey() {
        SecretManagementService service = configuredService();

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets();
        SecretManagementStatusView status = service.status();

        assertEquals("LOCAL:v2", result.activeKeyVersion());
        assertEquals(2, result.totalRecordsUpdated());
        assertEquals(2, result.totalSecretValuesReencrypted());
        assertEquals(2, result.totalFullReencryptionCount());
        assertEquals(0, result.totalMetadataRewrapCount());
        assertEquals("destination-mailboxes", result.areas().get(2).area());
        assertEquals(1, result.areas().get(2).recordsUpdated());
        assertEquals("system-oauth", result.areas().get(4).area());
        assertEquals(1, result.areas().get(4).recordsUpdated());
        assertEquals(0, result.followUp().browserExtensionSessionsRevoked());
        assertEquals(0, result.followUp().remoteSessionsRevoked());
        assertEquals(0, result.followUp().cachedOAuthAccessTokensCleared());
        assertEquals(5, status.activeKeyRecordCount());
        assertEquals(0, status.nonActiveKeyRecordCount());
        assertTrue(status.safeToRetireLegacyKeys());
        assertTrue(status.legacyKeyRetirementReady());
    }

    @Test
    void reencryptAllStoredSecretsCanRevokeDerivedTrustMaterial() {
        SecretManagementService service = configuredService();
        service.setExtensionSessionService(new StubExtensionSessionService(4));
        service.setRemoteSessionService(new StubRemoteSessionService(3));
        service.setOAuthCredentialService(new StubOAuthCredentialService(2));

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets(new dev.inboxbridge.dto.SecretReencryptionRequest(false, true, true, true));

        assertEquals(4, result.followUp().browserExtensionSessionsRevoked());
        assertEquals(3, result.followUp().remoteSessionsRevoked());
        assertEquals(2, result.followUp().cachedOAuthAccessTokensCleared());
    }

    @Test
    void reencryptAllStoredSecretsSchedulesRequestWhenCooldownIsConfigured() {
        SecretManagementService service = configuredService(Duration.ofHours(12), false);

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets(adminUser(), new dev.inboxbridge.dto.SecretReencryptionRequest(false, true, false, false));
        SecretManagementStatusView status = service.status();

        assertEquals("SCHEDULED", result.operationStatus());
        assertNotNull(result.executeAfter());
        assertEquals("PENDING", status.reencryptionRequest().status());
        assertEquals(1L, status.reencryptionRequest().requestedByUserId());
        assertEquals("LOCAL", status.reencryptionRequest().requestedTarget().mode());
        assertEquals("LOCAL", status.reencryptionRequest().requestedTarget().providerId());
        assertEquals("LOCAL:v2", status.reencryptionRequest().requestedTarget().activeKeyVersion());
        assertEquals("v2", status.reencryptionRequest().requestedTarget().activeKeyId());
        assertTrue(status.reencryptionRequest().approvalRequired());
        assertFalse(status.reencryptionRequest().approvalReady());
        assertNotNull(status.reencryptionRequest().plannedPreview());
        assertEquals(2, status.reencryptionRequest().plannedPreview().totalRecordsPendingUpdate());
        assertEquals(2, status.reencryptionRequest().plannedPreview().totalSecretValuesPendingRewrite());
        assertEquals(0, status.reencryptionRequest().totalRecordsUpdated());
        assertTrue(status.reencryptionReady());
        assertFalse(status.legacyKeyRetirementReady());
        assertTrue(status.retirementRequirements().stream()
                .anyMatch(requirement -> "no-pending-request".equals(requirement.requirementId()) && !requirement.satisfied()));
    }

    @Test
    void reencryptAllStoredSecretsRejectsImmediateOverrideWhenServerPolicyDisablesIt() {
        SecretManagementService service = configuredService(Duration.ofHours(12), false);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.reencryptAllStoredSecrets(adminUser(), new dev.inboxbridge.dto.SecretReencryptionRequest(true, false, false, false)));

        assertTrue(error.getMessage().contains("Immediate secret re-encryption override is disabled"));
    }

    @Test
    void reencryptAllStoredSecretsCanBypassCooldownWhenImmediateOverrideIsAllowed() {
        SecretManagementService service = configuredService(Duration.ofHours(12), true);

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets(adminUser(), new dev.inboxbridge.dto.SecretReencryptionRequest(true, false, false, false));

        assertEquals("COMPLETED", result.operationStatus());
        assertTrue(result.verification().passed());
        assertEquals("COMPLETED", service.status().reencryptionRequest().status());
    }

    @Test
    void executeDueReencryptionRequestsRunsQueuedRequestAfterCooldownExpires() {
        SecretManagementService service = configuredService(Duration.ofHours(2), false);

        SecretReencryptionResultView scheduled = service.reencryptAllStoredSecrets(
                adminUser(),
                new dev.inboxbridge.dto.SecretReencryptionRequest(false, true, true, true));

        service.executeDueReencryptionRequestsAt(scheduled.executeAfter().plusSeconds(1));
        SecretManagementStatusView blockedStatus = service.status();

        assertEquals("PENDING", blockedStatus.reencryptionRequest().status());
        assertTrue(blockedStatus.reencryptionRequest().approvalReady());
        assertNull(blockedStatus.reencryptionRequest().approvedAt());

        UserSession session = new UserSession();
        session.id = 11L;
        session.userId = 1L;
        session.lastSensitiveAuthAt = Instant.now();
        SecretManagementStatusView approvedStatus = service.approveQueuedReencryptionExecution(adminUser(), session);

        assertNotNull(approvedStatus.reencryptionRequest().approvedAt());
        assertEquals(1L, approvedStatus.reencryptionRequest().approvedByUserId());
        assertEquals("admin", approvedStatus.reencryptionRequest().approvedByUsername());
        assertFalse(approvedStatus.reencryptionRequest().approvalReady());

        service.executeDueReencryptionRequestsAt(scheduled.executeAfter().plusSeconds(2));
        SecretManagementStatusView status = service.status();

        assertEquals("COMPLETED", status.reencryptionRequest().status());
        assertEquals(0, status.nonActiveKeyRecordCount());
        assertTrue(status.safeToRetireLegacyKeys());
        assertTrue(status.legacyKeyRetirementReady());
        assertNotNull(status.reencryptionRequest().plannedPreview());
        assertEquals(2, status.reencryptionRequest().plannedPreview().totalRecordsPendingUpdate());
        assertEquals(2, status.reencryptionRequest().totalRecordsUpdated());
        assertEquals(2, status.reencryptionRequest().totalSecretValuesReencrypted());
        assertEquals(2, status.reencryptionRequest().totalFullReencryptionCount());
        assertEquals(0, status.reencryptionRequest().totalMetadataRewrapCount());
        assertTrue(status.reencryptionRequest().verification().passed());
    }

    @Test
    void approveQueuedReencryptionExecutionRejectsApprovalBeforeCooldownExpires() {
        SecretManagementService service = configuredService(Duration.ofHours(2), false);
        SecretReencryptionResultView scheduled = service.reencryptAllStoredSecrets(
                adminUser(),
                new dev.inboxbridge.dto.SecretReencryptionRequest(false, false, false, false));
        UserSession session = new UserSession();
        session.id = 12L;
        session.userId = 1L;
        session.lastSensitiveAuthAt = Instant.now();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.approveQueuedReencryptionExecution(adminUser(), session));

        assertTrue(error.getMessage().contains("cooldown window has not elapsed"));
        assertEquals("PENDING", service.status().reencryptionRequest().status());
        assertNotNull(scheduled.executeAfter());
    }

    @Test
    void staleQueuedReencryptionIsBlockedWhenTheTargetChangesBeforeCooldownExecution() {
        SecretManagementService service = configuredService(Duration.ofHours(2), false);
        SecretReencryptionResultView scheduled = service.reencryptAllStoredSecrets(
                adminUser(),
                new dev.inboxbridge.dto.SecretReencryptionRequest(false, false, false, false));

        service.localSecretKeyProvider.setTokenEncryptionKeyId("v3");

        service.executeDueReencryptionRequestsAt(scheduled.executeAfter().plusSeconds(1));
        SecretManagementStatusView blockedStatus = service.status();

        assertEquals("BLOCKED", blockedStatus.reencryptionRequest().status());
        assertTrue(
                blockedStatus.reencryptionRequest().message().contains("active secret-management target changed"),
                blockedStatus.reencryptionRequest().message());
        assertFalse(blockedStatus.reencryptionReady());
        assertTrue(blockedStatus.reencryptionRequirements().stream()
                .anyMatch(requirement -> "legacy-key-availability".equals(requirement.requirementId()) && !requirement.satisfied()));

        service.localSecretKeyProvider.setTokenEncryptionLegacyKeys(
                "v1:" + base64("0123456789abcdef0123456789abcdef")
                        + ",v2:" + base64("fedcba9876543210fedcba9876543210"));
        SecretManagementStatusView recoveredStatus = service.status();

        assertFalse(recoveredStatus.reencryptionReady());
        assertTrue(recoveredStatus.reencryptionRequirements().stream()
                .anyMatch(requirement -> "latest-recovery-review".equals(requirement.requirementId()) && !requirement.satisfied()));

        SecretManagementStatusView acknowledgedStatus = service.recordRecoveryReview(adminUser(), null);

        assertTrue(acknowledgedStatus.reencryptionReady());

        SecretReencryptionResultView rescheduled = service.reencryptAllStoredSecrets(
                adminUser(),
                new dev.inboxbridge.dto.SecretReencryptionRequest(false, false, false, false));
        SecretManagementStatusView rescheduledStatus = service.status();

        assertEquals("SCHEDULED", rescheduled.operationStatus());
        assertEquals("PENDING", rescheduledStatus.reencryptionRequest().status());
        assertEquals("LOCAL:v3", rescheduled.activeKeyVersion());
    }

    @Test
    void statusRequiresRecentReauthenticationWhenPolicyEnablesIt() {
        SecretManagementService service = configuredService(Duration.ZERO, false, Duration.ofMinutes(10));
        UserSession session = new UserSession();
        session.id = 7L;
        session.userId = 1L;

        SecretManagementStatusView status = service.status(session);

        assertTrue(status.reauthenticationRequired());
        assertFalse(status.reauthenticationSatisfied());
        assertEquals(null, status.reauthenticationExpiresAt());
        assertFalse(status.reencryptionReady());
        assertTrue(status.reencryptionRequirements().stream()
                .anyMatch(requirement -> "recent-reauthentication".equals(requirement.requirementId()) && !requirement.satisfied()));
        assertTrue(status.reencryptionRequirements().stream()
                .anyMatch(requirement -> "recent-reauthentication".equals(requirement.requirementId())
                        && "secret-reencryption-reauthentication".equals(requirement.actionTargetId())
                        && requirement.remediationSteps().stream().anyMatch(step -> step.contains("Verify with current password"))));
    }

    @Test
    void verifyReencryptionPasswordMarksCurrentSessionAsSatisfied() {
        SecretManagementService service = configuredService(Duration.ZERO, false, Duration.ofMinutes(10));
        service.appUserService = new AppUserService() {
            @Override
            public boolean passwordMatches(AppUser user, String rawPassword) {
                return "Current1!".equals(rawPassword);
            }
        };
        service.userSessionService = new UserSessionService() {
            @Override
            public Instant markSensitiveActionAuthenticated(Long sessionId) {
                return Instant.now();
            }
        };
        UserSession session = new UserSession();
        session.id = 8L;
        session.userId = 1L;

        SecretManagementStatusView status = service.verifyReencryptionPassword(adminUser(), session, "Current1!");

        assertTrue(status.reauthenticationRequired());
        assertTrue(status.reauthenticationSatisfied());
        assertNotNull(status.reauthenticationExpiresAt());
        assertNotNull(session.lastSensitiveAuthAt);
    }

    @Test
    void reencryptAllStoredSecretsRejectsMissingRecentReauthentication() {
        SecretManagementService service = configuredService(Duration.ZERO, false, Duration.ofMinutes(10));
        UserSession session = new UserSession();
        session.id = 9L;
        session.userId = 1L;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.reencryptAllStoredSecrets(adminUser(), session, new dev.inboxbridge.dto.SecretReencryptionRequest(false, false, false, false)));

        assertTrue(error.getMessage().contains("Re-authenticate this browser session"));
    }

    @Test
    void finishReencryptionPasskeyVerificationMarksCurrentSessionAsSatisfied() {
        SecretManagementService service = configuredService(Duration.ZERO, false, Duration.ofMinutes(10));
        service.passkeyService = new PasskeyService() {
            @Override
            public PasskeyAuthenticationResult finishAuthentication(dev.inboxbridge.dto.FinishPasskeyCeremonyRequest request) {
                AppUser user = new AppUser();
                user.id = 1L;
                user.username = "admin";
                user.role = AppUser.Role.ADMIN;
                return new PasskeyAuthenticationResult(user, true);
            }
        };
        service.userSessionService = new UserSessionService() {
            @Override
            public Instant markSensitiveActionAuthenticated(Long sessionId) {
                return Instant.now();
            }
        };
        UserSession session = new UserSession();
        session.id = 10L;
        session.userId = 1L;

        SecretManagementStatusView status = service.finishReencryptionPasskeyVerification(
                adminUser(),
                session,
                new dev.inboxbridge.dto.FinishPasskeyCeremonyRequest("ceremony-1", "{\"id\":\"credential\"}"));

        assertTrue(status.reauthenticationSatisfied());
        assertNotNull(session.lastSensitiveAuthAt);
    }

    private SecretManagementService configuredService() {
        return configuredService(Duration.ZERO, false, Duration.ZERO);
    }

    private SecretManagementService configuredTransitRolloverService(SecretProviderMode mode, StubTransitSecretProvider activeTransitProviderOverride) {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        service.setLocalSecretKeyProvider(provider);

        StubTransitSecretProvider staleTransitProvider = new StubTransitSecretProvider(true, 1);
        StubTransitSecretProvider activeTransitProvider = activeTransitProviderOverride == null
                ? new StubTransitSecretProvider(true, 2)
                : activeTransitProviderOverride;

        SecretProviderResolver staleResolver = new SecretProviderResolver();
        staleResolver.setLocalSecretKeyProvider(provider);
        staleResolver.setTransitSecretProvider(staleTransitProvider);
        configureTransitMode(staleResolver, mode);
        SecretEncryptionService staleEncryptionService = new SecretEncryptionService();
        staleEncryptionService.setLocalSecretKeyProvider(provider);
        staleEncryptionService.setSecretProviderResolver(staleResolver);
        staleEncryptionService.setTransitSecretProvider(staleTransitProvider);

        SecretProviderResolver activeResolver = new SecretProviderResolver();
        activeResolver.setLocalSecretKeyProvider(provider);
        activeResolver.setTransitSecretProvider(activeTransitProvider);
        configureTransitMode(activeResolver, mode);
        service.setSecretProviderResolver(activeResolver);
        SecretEncryptionService activeEncryptionService = new SecretEncryptionService();
        activeEncryptionService.setLocalSecretKeyProvider(provider);
        activeEncryptionService.setSecretProviderResolver(activeResolver);
        activeEncryptionService.setTransitSecretProvider(activeTransitProvider);
        service.setSecretEncryptionService(activeEncryptionService);

        OAuthCredential oauthCredential = new OAuthCredential();
        SecretEncryptionService.EncryptedValue oauthRefresh = staleEncryptionService.encrypt("oauth-refresh", "GOOGLE:gmail-destination:refresh");
        oauthCredential.provider = "GOOGLE";
        oauthCredential.subjectKey = "gmail-destination";
        oauthCredential.refreshTokenCiphertext = oauthRefresh.ciphertextBase64();
        oauthCredential.refreshTokenNonce = oauthRefresh.nonceBase64();
        oauthCredential.keyVersion = staleEncryptionService.keyVersion();

        UserMailDestinationConfig destination = new UserMailDestinationConfig();
        SecretEncryptionService.EncryptedValue destinationPassword = staleEncryptionService.encrypt("destination-password", "user-destination:1:password");
        destination.userId = 1L;
        destination.passwordCiphertext = destinationPassword.ciphertextBase64();
        destination.passwordNonce = destinationPassword.nonceBase64();
        destination.keyVersion = staleEncryptionService.keyVersion();

        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of(oauthCredential)));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of()));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of(destination)));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of()));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(null));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(null));
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository());
        service.setSystemSecretRecoveryReviewRepository(new InMemorySystemSecretRecoveryReviewRepository());
        service.setSystemSecretRetirementReviewRepository(new InMemorySystemSecretRetirementReviewRepository());
        service.setSecretManagementPolicyConfig(new dev.inboxbridge.config.SecretManagementPolicyConfig() {
            @Override
            public boolean allowEnvManagedMailboxSecrets() {
                return true;
            }

            @Override
            public Duration reencryptionCooldown() {
                return Duration.ZERO;
            }

            @Override
            public boolean allowImmediateReencryptOverride() {
                return false;
            }

            @Override
            public Duration reauthenticationTtl() {
                return Duration.ZERO;
            }
        });
        service.setExtensionSessionService(new StubExtensionSessionService(0));
        service.setRemoteSessionService(new StubRemoteSessionService(0));
        service.setOAuthCredentialService(new StubOAuthCredentialService(0));
        return service;
    }

    private void configureTransitMode(SecretProviderResolver resolver, SecretProviderMode mode) {
        if (mode == SecretProviderMode.SPLIT_KEY) {
            resolver.setProviderMode("SPLIT_KEY");
            resolver.setSplitSecondaryMode("OPENBAO_TRANSIT");
        } else {
            resolver.setProviderMode(mode.name());
        }
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
    }

    private SecretManagementService configuredService(Duration cooldown, boolean allowImmediateOverride) {
        return configuredService(cooldown, allowImmediateOverride, Duration.ZERO);
    }

    private SecretManagementService configuredService(Duration cooldown, boolean allowImmediateOverride, Duration reauthenticationTtl) {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("v1:" + base64("0123456789abcdef0123456789abcdef"));
        service.setLocalSecretKeyProvider(provider);
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(provider);
        resolver.setProviderMode("LOCAL");
        service.setSecretProviderResolver(resolver);
        SecretEncryptionService secretEncryptionService = new SecretEncryptionService();
        secretEncryptionService.setLocalSecretKeyProvider(provider);
        secretEncryptionService.setSecretProviderResolver(resolver);
        service.setSecretEncryptionService(secretEncryptionService);
        SecretEncryptionService legacySecretEncryptionService = new SecretEncryptionService();
        legacySecretEncryptionService.setTokenEncryptionKey(base64("0123456789abcdef0123456789abcdef"));
        legacySecretEncryptionService.setTokenEncryptionKeyId("v1");

        OAuthCredential oauthCredential = new OAuthCredential();
        SecretEncryptionService.EncryptedValue oauthRefresh = secretEncryptionService.encrypt("oauth-refresh", "GOOGLE:gmail-destination:refresh");
        oauthCredential.provider = "GOOGLE";
        oauthCredential.subjectKey = "gmail-destination";
        oauthCredential.refreshTokenCiphertext = oauthRefresh.ciphertextBase64();
        oauthCredential.refreshTokenNonce = oauthRefresh.nonceBase64();
        oauthCredential.keyVersion = secretEncryptionService.keyVersion();

        UserEmailAccount sourceMailbox = new UserEmailAccount();
        SecretEncryptionService.EncryptedValue sourcePassword = secretEncryptionService.encrypt("source-password", "user-bridge:1:source-a:password");
        sourceMailbox.userId = 1L;
        sourceMailbox.emailAccountId = "source-a";
        sourceMailbox.passwordCiphertext = sourcePassword.ciphertextBase64();
        sourceMailbox.passwordNonce = sourcePassword.nonceBase64();
        sourceMailbox.keyVersion = secretEncryptionService.keyVersion();

        UserMailDestinationConfig destination = new UserMailDestinationConfig();
        SecretEncryptionService.EncryptedValue destinationPassword = legacySecretEncryptionService.encrypt("destination-password", "user-destination:1:password");
        destination.userId = 1L;
        destination.passwordCiphertext = destinationPassword.ciphertextBase64();
        destination.passwordNonce = destinationPassword.nonceBase64();
        destination.keyVersion = "v1";

        UserGmailConfig gmailConfig = new UserGmailConfig();
        SecretEncryptionService.EncryptedValue gmailSecret = secretEncryptionService.encrypt("gmail-secret", "user-gmail:1:client-secret");
        gmailConfig.userId = 1L;
        gmailConfig.clientSecretCiphertext = gmailSecret.ciphertextBase64();
        gmailConfig.clientSecretNonce = gmailSecret.nonceBase64();
        gmailConfig.keyVersion = secretEncryptionService.keyVersion();

        SystemOAuthAppSettings systemOAuth = new SystemOAuthAppSettings();
        SecretEncryptionService.EncryptedValue systemGoogleSecret = legacySecretEncryptionService.encrypt("system-google-secret", "system-oauth:google-client-secret");
        systemOAuth.googleClientSecretCiphertext = systemGoogleSecret.ciphertextBase64();
        systemOAuth.googleClientSecretNonce = systemGoogleSecret.nonceBase64();
        systemOAuth.keyVersion = "v1";

        SystemAuthSecuritySetting authSecurity = new SystemAuthSecuritySetting();
        authSecurity.registrationTurnstileSecretCiphertext = "";
        authSecurity.keyVersion = "LOCAL:v2";

        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of(oauthCredential)));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of(sourceMailbox)));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of(destination)));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of(gmailConfig)));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(systemOAuth));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(authSecurity));
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository());
        service.setSystemSecretRecoveryReviewRepository(new InMemorySystemSecretRecoveryReviewRepository());
        service.setSystemSecretRetirementReviewRepository(new InMemorySystemSecretRetirementReviewRepository());
        service.setSecretManagementPolicyConfig(new dev.inboxbridge.config.SecretManagementPolicyConfig() {
            @Override
            public boolean allowEnvManagedMailboxSecrets() {
                return true;
            }

            @Override
            public java.time.Duration reencryptionCooldown() {
                return cooldown;
            }

            @Override
            public boolean allowImmediateReencryptOverride() {
                return allowImmediateOverride;
            }

            @Override
            public java.time.Duration reauthenticationTtl() {
                return reauthenticationTtl;
            }
        });
        service.setExtensionSessionService(new StubExtensionSessionService(0));
        service.setRemoteSessionService(new StubRemoteSessionService(0));
        service.setOAuthCredentialService(new StubOAuthCredentialService(0));
        service.setEnvSourceService(new EnvSourceService() {
            @Override
            public long configuredSourceCountIgnoringPolicy() {
                return 1;
            }
        });
        service.setSystemOAuthAppSettingsService(new SystemOAuthAppSettingsService() {
            @Override
            public boolean envManagedGoogleRefreshTokenConfigured() {
                return true;
            }
        });
        return service;
    }

    private SecretManagementService configuredRetirementReadyService() {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("");
        service.setLocalSecretKeyProvider(provider);
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(provider);
        resolver.setProviderMode("LOCAL");
        service.setSecretProviderResolver(resolver);
        SecretEncryptionService secretEncryptionService = new SecretEncryptionService();
        secretEncryptionService.setLocalSecretKeyProvider(provider);
        secretEncryptionService.setSecretProviderResolver(resolver);
        service.setSecretEncryptionService(secretEncryptionService);

        OAuthCredential oauthCredential = new OAuthCredential();
        SecretEncryptionService.EncryptedValue oauthRefresh = secretEncryptionService.encrypt("oauth-refresh", "GOOGLE:gmail-destination:refresh");
        oauthCredential.provider = "GOOGLE";
        oauthCredential.subjectKey = "gmail-destination";
        oauthCredential.refreshTokenCiphertext = oauthRefresh.ciphertextBase64();
        oauthCredential.refreshTokenNonce = oauthRefresh.nonceBase64();
        oauthCredential.keyVersion = secretEncryptionService.keyVersion();

        UserEmailAccount sourceMailbox = new UserEmailAccount();
        SecretEncryptionService.EncryptedValue sourcePassword = secretEncryptionService.encrypt("source-password", "user-bridge:1:source-a:password");
        sourceMailbox.userId = 1L;
        sourceMailbox.emailAccountId = "source-a";
        sourceMailbox.passwordCiphertext = sourcePassword.ciphertextBase64();
        sourceMailbox.passwordNonce = sourcePassword.nonceBase64();
        sourceMailbox.keyVersion = secretEncryptionService.keyVersion();

        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of(oauthCredential)));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of(sourceMailbox)));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of()));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of()));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(null));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(null));
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository());
        service.setSystemSecretRetirementReviewRepository(new InMemorySystemSecretRetirementReviewRepository());
        service.setSecretManagementPolicyConfig(new dev.inboxbridge.config.SecretManagementPolicyConfig() {
            @Override
            public boolean allowEnvManagedMailboxSecrets() {
                return true;
            }

            @Override
            public Duration reencryptionCooldown() {
                return Duration.ZERO;
            }

            @Override
            public boolean allowImmediateReencryptOverride() {
                return false;
            }

            @Override
            public Duration reauthenticationTtl() {
                return Duration.ZERO;
            }
        });
        service.setExtensionSessionService(new StubExtensionSessionService(0));
        service.setRemoteSessionService(new StubRemoteSessionService(0));
        service.setOAuthCredentialService(new StubOAuthCredentialService(0));
        service.setEnvSourceService(new EnvSourceService() {
            @Override
            public long configuredSourceCountIgnoringPolicy() {
                return 0;
            }
        });
        service.setSystemOAuthAppSettingsService(new SystemOAuthAppSettingsService() {
            @Override
            public boolean envManagedGoogleRefreshTokenConfigured() {
                return false;
            }
        });
        return service;
    }

    private AppUser adminUser() {
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "admin";
        user.role = AppUser.Role.ADMIN;
        return user;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private dev.inboxbridge.persistence.SystemSecretReencryptionRequest failedRequestState() {
        dev.inboxbridge.persistence.SystemSecretReencryptionRequest request = new dev.inboxbridge.persistence.SystemSecretReencryptionRequest();
        request.status = "FAILED";
        request.requestedAt = Instant.parse("2026-04-15T10:00:00Z");
        request.requestedByUserId = 1L;
        request.requestedMode = "LOCAL";
        request.requestedProviderId = "LOCAL";
        request.requestedActiveKeyVersion = "LOCAL:v2";
        request.requestedActiveKeyId = "v2";
        request.lastStartedAt = Instant.parse("2026-04-15T10:01:00Z");
        request.lastFailedAt = Instant.parse("2026-04-15T10:02:00Z");
        request.lastErrorMessage = "OpenBao transit health check failed.";
        request.lastResultMessage = "Secret re-encryption did not complete successfully.";
        request.lastVerificationPassed = Boolean.FALSE;
        return request;
    }

    private dev.inboxbridge.persistence.SystemSecretReencryptionRequest completedRequestStateWithWarning() {
        dev.inboxbridge.persistence.SystemSecretReencryptionRequest request = new dev.inboxbridge.persistence.SystemSecretReencryptionRequest();
        request.status = "COMPLETED";
        request.requestedAt = Instant.parse("2026-04-15T11:00:00Z");
        request.requestedByUserId = 1L;
        request.requestedMode = "LOCAL";
        request.requestedProviderId = "LOCAL";
        request.requestedActiveKeyVersion = "LOCAL:v2";
        request.requestedActiveKeyId = "v2";
        request.lastStartedAt = Instant.parse("2026-04-15T11:01:00Z");
        request.lastCompletedAt = Instant.parse("2026-04-15T11:02:00Z");
        request.lastResultMessage = "Secret re-encryption completed but post-run verification still requires operator attention.";
        request.lastVerificationPassed = Boolean.FALSE;
        request.lastVerificationJson = "{\"passed\":false,\"messages\":[\"Destination mailbox validation still needs attention.\"],\"operatorSaveItems\":[\"Save the active key version and latest recovery notes.\"]}";
        return request;
    }

    private static final class InMemoryOAuthCredentialRepository extends OAuthCredentialRepository {
        private final List<OAuthCredential> values;

        private InMemoryOAuthCredentialRepository(List<OAuthCredential> values) {
            this.values = values;
        }

        @Override
        public List<OAuthCredential> listAll() {
            return values;
        }

        @Override
        public void persist(OAuthCredential entity) {
        }
    }

    private static final class InMemoryUserEmailAccountRepository extends UserEmailAccountRepository {
        private final List<UserEmailAccount> values;

        private InMemoryUserEmailAccountRepository(List<UserEmailAccount> values) {
            this.values = values;
        }

        @Override
        public List<UserEmailAccount> listAll() {
            return values;
        }

        @Override
        public void persist(UserEmailAccount entity) {
        }
    }

    private static final class InMemoryUserMailDestinationConfigRepository extends UserMailDestinationConfigRepository {
        private final List<UserMailDestinationConfig> values;

        private InMemoryUserMailDestinationConfigRepository(List<UserMailDestinationConfig> values) {
            this.values = values;
        }

        @Override
        public List<UserMailDestinationConfig> listAll() {
            return values;
        }

        @Override
        public void persist(UserMailDestinationConfig entity) {
        }
    }

    private static final class InMemoryUserGmailConfigRepository extends UserGmailConfigRepository {
        private final List<UserGmailConfig> values;

        private InMemoryUserGmailConfigRepository(List<UserGmailConfig> values) {
            this.values = values;
        }

        @Override
        public List<UserGmailConfig> listAll() {
            return values;
        }

        @Override
        public void persist(UserGmailConfig entity) {
        }
    }

    private static final class InMemorySystemOAuthAppSettingsRepository extends SystemOAuthAppSettingsRepository {
        private final SystemOAuthAppSettings value;

        private InMemorySystemOAuthAppSettingsRepository(SystemOAuthAppSettings value) {
            this.value = value;
        }

        @Override
        public Optional<SystemOAuthAppSettings> findSingleton() {
            return Optional.ofNullable(value);
        }

        @Override
        public void persist(SystemOAuthAppSettings entity) {
        }
    }

    private static final class InMemorySystemAuthSecuritySettingRepository extends SystemAuthSecuritySettingRepository {
        private final SystemAuthSecuritySetting value;

        private InMemorySystemAuthSecuritySettingRepository(SystemAuthSecuritySetting value) {
            this.value = value;
        }

        @Override
        public Optional<SystemAuthSecuritySetting> findSingleton() {
            return Optional.ofNullable(value);
        }

        @Override
        public void persist(SystemAuthSecuritySetting entity) {
        }
    }

    private static final class InMemorySystemSecretReencryptionRequestRepository extends dev.inboxbridge.persistence.SystemSecretReencryptionRequestRepository {
        private dev.inboxbridge.persistence.SystemSecretReencryptionRequest value;

        private InMemorySystemSecretReencryptionRequestRepository() {
        }

        private InMemorySystemSecretReencryptionRequestRepository(dev.inboxbridge.persistence.SystemSecretReencryptionRequest value) {
            this.value = value;
        }

        @Override
        public Optional<dev.inboxbridge.persistence.SystemSecretReencryptionRequest> findSingleton() {
            return Optional.ofNullable(value);
        }

        @Override
        public void persist(dev.inboxbridge.persistence.SystemSecretReencryptionRequest entity) {
            value = entity;
        }
    }

    private static final class InMemorySystemSecretRetirementReviewRepository extends dev.inboxbridge.persistence.SystemSecretRetirementReviewRepository {
        private final java.util.List<dev.inboxbridge.persistence.SystemSecretRetirementReview> values = new java.util.ArrayList<>();
        private long nextId = 1L;

        @Override
        public Optional<dev.inboxbridge.persistence.SystemSecretRetirementReview> findLatest() {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((dev.inboxbridge.persistence.SystemSecretRetirementReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .findFirst();
        }

        @Override
        public java.util.List<dev.inboxbridge.persistence.SystemSecretRetirementReview> listRecent(int maxResults) {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((dev.inboxbridge.persistence.SystemSecretRetirementReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .limit(maxResults)
                    .toList();
        }

        @Override
        public void persist(dev.inboxbridge.persistence.SystemSecretRetirementReview entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            values.removeIf(existing -> existing.id.equals(entity.id));
            values.add(entity);
        }
    }

    private static final class InMemorySystemSecretRecoveryReviewRepository extends dev.inboxbridge.persistence.SystemSecretRecoveryReviewRepository {
        private final java.util.List<dev.inboxbridge.persistence.SystemSecretRecoveryReview> values = new java.util.ArrayList<>();
        private long nextId = 1L;

        @Override
        public Optional<dev.inboxbridge.persistence.SystemSecretRecoveryReview> findLatest() {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((dev.inboxbridge.persistence.SystemSecretRecoveryReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .findFirst();
        }

        @Override
        public java.util.List<dev.inboxbridge.persistence.SystemSecretRecoveryReview> listRecent(int maxResults) {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((dev.inboxbridge.persistence.SystemSecretRecoveryReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .limit(maxResults)
                    .toList();
        }

        @Override
        public void persist(dev.inboxbridge.persistence.SystemSecretRecoveryReview entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            values.removeIf(existing -> existing.id.equals(entity.id));
            values.add(entity);
        }
    }

    private static final class StubTransitSecretProvider extends TransitSecretProvider {
        private final boolean healthy;
        private final int latestVersion;
        private int rewrapCalls;
        private int decryptCalls;

        private StubTransitSecretProvider(boolean healthy) {
            this(healthy, 1);
        }

        private StubTransitSecretProvider(boolean healthy, int latestVersion) {
            this.healthy = healthy;
            this.latestVersion = latestVersion;
        }

        @Override
        public SecretProviderHealth health(TransitProviderConfig config) {
            return new SecretProviderHealth(
                    config.mode(),
                    config.providerId(),
                    healthy,
                    healthy,
                    healthy ? config.mode().name() + " transit provider is ready." : "Transit provider unavailable");
        }

        @Override
        public SecretEncryptionService.EncryptedValue encrypt(TransitProviderConfig config, String value, String context) {
            return new SecretEncryptionService.EncryptedValue("vault:v" + latestVersion + ":" + value, "");
        }

        @Override
        public String decrypt(TransitProviderConfig config, String ciphertext, String context) {
            decryptCalls++;
            int separator = ciphertext == null ? -1 : ciphertext.indexOf(':', ciphertext.indexOf(':') + 1);
            return separator >= 0 ? ciphertext.substring(separator + 1) : ciphertext;
        }

        @Override
        public java.util.OptionalInt latestKeyVersion(TransitProviderConfig config) {
            return healthy ? java.util.OptionalInt.of(latestVersion) : java.util.OptionalInt.empty();
        }

        @Override
        public String rewrap(TransitProviderConfig config, String ciphertext, String context) {
            rewrapCalls++;
            String value = decrypt(config, ciphertext, context);
            decryptCalls--;
            return "vault:v" + latestVersion + ":" + value;
        }

        int rewrapCalls() {
            return rewrapCalls;
        }

        int decryptCalls() {
            return decryptCalls;
        }
    }

    private static final class StubExtensionSessionService extends ExtensionSessionService {
        private final int revokedCount;

        private StubExtensionSessionService(int revokedCount) {
            this.revokedCount = revokedCount;
        }

        @Override
        public int revokeAllSessionsForAllUsers() {
            return revokedCount;
        }
    }

    private static final class StubRemoteSessionService extends RemoteSessionService {
        private final int revokedCount;

        private StubRemoteSessionService(int revokedCount) {
            this.revokedCount = revokedCount;
        }

        @Override
        public int invalidateAllSessions() {
            return revokedCount;
        }
    }

    private static final class StubOAuthCredentialService extends OAuthCredentialService {
        private final int clearedCount;

        private StubOAuthCredentialService(int clearedCount) {
            this.clearedCount = clearedCount;
        }

        @Override
        public int clearAllAccessTokens() {
            return clearedCount;
        }
    }

}
