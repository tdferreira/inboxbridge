package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Read-only health snapshot for one IMAP IDLE watcher instance.
 */
public record SourceIdleWatchView(
        String folderName,
        String status,
        Instant lastConnectedAt,
        Instant disconnectedSince) {
}
