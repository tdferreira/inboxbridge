package dev.inboxbridge.dto;

import java.util.List;

public record AdminUserConfigurationResponse(
        UserSummaryResponse user,
        UserGmailConfigView gmailConfig,
        UserPollingSettingsView pollingSettings,
        List<UserBridgeView> bridges,
        List<PasskeyView> passkeys) {
}
