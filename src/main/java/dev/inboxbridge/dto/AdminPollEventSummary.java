package dev.inboxbridge.dto;

import java.time.Instant;

public record AdminPollEventSummary(
        String sourceId,
        String trigger,
        String status,
        Instant startedAt,
        Instant finishedAt,
        int fetched,
        int imported,
        int duplicates,
        String error) {
}
