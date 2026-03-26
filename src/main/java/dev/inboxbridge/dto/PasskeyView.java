package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Non-sensitive passkey summary returned to the browser and to admin views.
 */
public record PasskeyView(
        Long id,
        String label,
        boolean discoverable,
        boolean backupEligible,
        boolean backedUp,
        Instant createdAt,
        Instant lastUsedAt) {
}
