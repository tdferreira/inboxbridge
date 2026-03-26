package dev.inboxbridge.dto;

public record UserSummaryResponse(
        Long id,
        String username,
        String role,
        boolean active,
        boolean approved,
        boolean mustChangePassword,
        boolean passwordConfigured,
        boolean gmailConfigured,
        int bridgeCount,
        int passkeyCount) {
}
