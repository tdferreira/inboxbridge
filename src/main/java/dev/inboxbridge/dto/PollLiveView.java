package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.List;

public record PollLiveView(
        boolean running,
        String runId,
        String state,
        String trigger,
        String ownerUsername,
        boolean viewerCanControl,
        String activeSourceId,
        Instant startedAt,
        Instant updatedAt,
        List<PollLiveSourceView> sources) {
}
