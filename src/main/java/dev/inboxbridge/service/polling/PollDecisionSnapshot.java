package dev.inboxbridge.service.polling;

import java.time.Instant;

/**
 * Snapshot of the cooldown and adaptive-throttle decisions observed during one
 * persisted source poll event.
 */
public record PollDecisionSnapshot(
        String failureCategory,
        Long cooldownBackoffMillis,
        Instant cooldownUntil,
        Long sourceThrottleWaitMillis,
        Integer sourceThrottleMultiplierAfter,
        Instant sourceThrottleNextAllowedAt,
        Long destinationThrottleWaitMillis,
        Integer destinationThrottleMultiplierAfter,
        Instant destinationThrottleNextAllowedAt) {

    public static PollDecisionSnapshot empty() {
        return new PollDecisionSnapshot(null, null, null, null, null, null, null, null, null);
    }
}
