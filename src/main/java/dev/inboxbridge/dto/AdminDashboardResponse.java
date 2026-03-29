package dev.inboxbridge.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminDashboardResponse(
        AdminOverallSummary overall,
        GlobalPollingStatsView stats,
        AdminPollingSettingsView polling,
        AdminDestinationSummary destination,
                @JsonProperty("emailAccounts") List<AdminEmailAccountSummary> bridges,
                List<AdminPollEventSummary> recentEvents) {

        @JsonProperty("bridges")
        public List<AdminEmailAccountSummary> legacyBridges() {
                return bridges;
        }
}
