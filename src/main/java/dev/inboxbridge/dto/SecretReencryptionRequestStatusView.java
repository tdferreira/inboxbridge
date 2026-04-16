package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.List;

/**
 * Surfaces the latest scheduled or completed secret re-encryption request so
 * operators can see whether a cooldown is active and whether the last run
 * verified successfully.
 */
public record SecretReencryptionRequestStatusView(
        String requestFingerprint,
        String status,
        Instant requestedAt,
        Long requestedByUserId,
        SecretReencryptionTargetView requestedTarget,
        Instant executeAfter,
        boolean approvalRequired,
        boolean approvalReady,
        Instant approvedAt,
        Long approvedByUserId,
        String approvedByUsername,
        Instant lastStartedAt,
        Instant lastCompletedAt,
        Instant lastFailedAt,
        boolean immediateExecutionOverrideUsed,
        String message,
        boolean verificationPassed,
        SecretReencryptionPreviewView plannedPreview,
        int totalRecordsUpdated,
        int totalSecretValuesReencrypted,
        int totalFullReencryptionCount,
        int totalMetadataRewrapCount,
        List<SecretReencryptionAreaResultView> areas,
        SecretReencryptionFollowUpView followUp,
        SecretReencryptionVerificationView verification) {
}
