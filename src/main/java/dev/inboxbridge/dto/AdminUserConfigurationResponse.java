package dev.inboxbridge.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminUserConfigurationResponse(
        UserSummaryResponse user,
        UserMailDestinationView destinationConfig,
        UserPollingSettingsView pollingSettings,
        UserPollingStatsView pollingStats,
        @JsonProperty("emailAccounts") List<UserEmailAccountView> emailAccounts,
                List<PasskeyView> passkeys) {

        @JsonProperty("bridges")
        public List<UserEmailAccountView> legacyBridges() {
                return emailAccounts;
        }
}
