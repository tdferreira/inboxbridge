package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.List;

/**
 * Captures an operator-reviewed snapshot of the legacy-key retirement state so
 * later audits can tie retirement decisions back to a concrete backend status.
 */
public record SecretManagementRetirementReviewView(
        Long reviewId,
        Instant reviewedAt,
        Long reviewedByUserId,
        String reviewedByUsername,
        String providerId,
        String activeKeyVersion,
        String activeKeyId,
        List<String> configuredLegacyKeyIds,
        boolean safeToRetireLegacyKeys,
        boolean legacyKeyRetirementReady,
        long nonActiveKeyRecordCount,
        long unavailableKeyRecordCount,
        String latestRequestStatus,
        int blockingRequirementsRemaining,
        List<String> unsatisfiedRequirementIds,
        SecretManagementRetirementCompletionView completion) {
}
