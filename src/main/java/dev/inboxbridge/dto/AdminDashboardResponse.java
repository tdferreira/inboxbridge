package dev.inboxbridge.dto;

import java.util.List;

public record AdminDashboardResponse(
        AdminOverallSummary overall,
        AdminPollingSettingsView polling,
        AdminDestinationSummary destination,
        List<AdminBridgeSummary> bridges,
        List<AdminPollEventSummary> recentEvents) {
}
