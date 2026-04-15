package dev.inboxbridge.dto;

import java.util.List;

/**
 * Summarizes the next operator-facing encryption-layer rotation action implied
 * by the currently configured active provider and the stored ciphertext usage.
 */
public record SecretManagementRotationPlanView(
        String planId,
        String title,
        String summary,
        String recommendedAction,
        String targetKeyVersion,
        long affectedRecordCount,
        long unavailableRecordCount,
        List<String> impactedAreas,
        boolean rotationNeeded,
        boolean requiresFullReencryption,
        boolean metadataRewrapSupported) {
}
