package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionSummaryView(
        int sourceCount,
        int enabledSourceCount,
        int errorSourceCount,
        Instant lastCompletedRunAt,
        ExtensionLastRunSummaryView lastCompletedRun) {
}
