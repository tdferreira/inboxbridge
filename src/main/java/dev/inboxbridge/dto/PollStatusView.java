package dev.inboxbridge.dto;

import java.time.Instant;

public record PollStatusView(
        boolean running,
        String activeSourceId,
        String trigger,
        Instant startedAt) {
}
