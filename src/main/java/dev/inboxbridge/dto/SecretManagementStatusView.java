package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.List;

/**
 * Describes the currently active secret-management mode together with
 * deployment-level rotation readiness information.
 */
public record SecretManagementStatusView(
        boolean secureStorageConfigured,
        String mode,
        String providerId,
        boolean providerHealthy,
        boolean providerWritable,
        String providerStatusMessage,
        List<SecretProviderComponentStatusView> providerComponents,
        String activeKeyVersion,
        String activeKeyId,
        List<String> configuredLegacyKeyIds,
        long protectedRecordCount,
        long activeKeyRecordCount,
        long nonActiveKeyRecordCount,
        long unavailableKeyRecordCount,
        boolean envManagedMailboxSecretsAllowed,
        long configuredEnvManagedSourceCount,
        boolean envManagedGoogleRefreshTokenConfigured,
        boolean safeToRetireLegacyKeys,
        boolean legacyKeyRetirementReady,
        SecretManagementRotationPlanView rotationPlan,
        SecretReencryptionPreviewView reencryptionPreview,
        List<SecretManagementKeyUsageView> keyUsage,
        boolean reencryptionReady,
        List<SecretReencryptionRequirementView> reencryptionRequirements,
        List<SecretManagementRetirementRequirementView> retirementRequirements,
        SecretReencryptionRequestStatusView reencryptionRequest,
        String reencryptionCooldown,
        boolean immediateReencryptionOverrideAllowed,
        boolean reauthenticationRequired,
        boolean reauthenticationSatisfied,
        Instant reauthenticationExpiresAt) {
}
