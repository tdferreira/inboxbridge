package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Exposes the current persisted adaptive throttle state for a source-side or
 * destination-side pacing bucket.
 */
public record SourceThrottleStateView(
        String throttleKey,
        String throttleKind,
        int adaptiveMultiplier,
        Instant nextAllowedAt,
        Instant updatedAt) {
}
