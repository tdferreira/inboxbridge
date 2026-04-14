package dev.inboxbridge.dto;

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
        List<SecretManagementKeyUsageView> keyUsage) {
}
