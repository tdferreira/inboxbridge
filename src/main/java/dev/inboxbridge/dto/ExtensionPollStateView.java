package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionPollStateView(
        boolean running,
        String state,
        boolean canRun,
        String activeSourceId,
        Instant startedAt,
        Instant updatedAt) {
}
