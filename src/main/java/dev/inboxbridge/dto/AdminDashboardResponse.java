package dev.inboxbridge.dto;

import java.util.List;

public record AdminDashboardResponse(
        AdminOverallSummary overall,
        AdminDestinationSummary destination,
        List<AdminBridgeSummary> bridges,
        List<AdminPollEventSummary> recentEvents) {
}
