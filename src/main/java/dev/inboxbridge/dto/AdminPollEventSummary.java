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
        long importedBytes,
        int duplicates,
        int spamJunkMessageCount,
        String actorUsername,
        String executionSurface,
        String error) {
}
