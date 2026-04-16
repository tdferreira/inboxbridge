package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Captures an operator-reviewed snapshot of a failed or warning-state
 * secret-management run so later retries and retirement decisions can be tied
 * back to an acknowledged recovery plan.
 */
public record SecretManagementRecoveryReviewView(
        Long reviewId,
        Instant reviewedAt,
        Long reviewedByUserId,
        String reviewedByUsername,
        String requestFingerprint,
        String latestRequestStatus,
        String latestRequestMessage,
        boolean verificationPassed,
        boolean rollbackRecommended,
        String mode,
        String providerId,
        String activeKeyVersion,
        boolean providerWritable,
        long unavailableKeyRecordCount) {
}
