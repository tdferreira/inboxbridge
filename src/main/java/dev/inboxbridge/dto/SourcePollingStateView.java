package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Exposes the current scheduler/backoff state for one source.
 */
public record SourcePollingStateView(
        Instant nextPollAt,
        Instant cooldownUntil,
        int consecutiveFailures,
        String lastFailureReason,
        Instant lastFailureAt,
        Instant lastSuccessAt) {
}
