package dev.inboxbridge.dto;

import java.util.List;

public record AdminUserConfigurationResponse(
        UserSummaryResponse user,
        UserGmailConfigView gmailConfig,
        List<UserBridgeView> bridges) {
}
