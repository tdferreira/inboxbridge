package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Surfaces the latest scheduled or completed secret re-encryption request so
 * operators can see whether a cooldown is active and whether the last run
 * verified successfully.
 */
public record SecretReencryptionRequestStatusView(
        String status,
        Instant requestedAt,
        Long requestedByUserId,
        Instant executeAfter,
        Instant lastStartedAt,
        Instant lastCompletedAt,
        Instant lastFailedAt,
        boolean immediateExecutionOverrideUsed,
        String message,
        boolean verificationPassed) {
}
