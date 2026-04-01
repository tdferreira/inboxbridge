package dev.inboxbridge.dto;

import java.time.Instant;

public record PollLiveSourceView(
        String sourceId,
        String ownerUsername,
        String label,
        String state,
        boolean actionable,
        int position,
        int attempt,
        int fetched,
        int imported,
        int duplicates,
        String error,
        Instant startedAt,
        Instant finishedAt) {
}
