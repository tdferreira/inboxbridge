package dev.inboxbridge.dto;

import java.util.List;

public record AccountSessionsResponse(
        List<AccountSessionView> recentLogins,
        List<AccountSessionView> activeSessions) {
}
