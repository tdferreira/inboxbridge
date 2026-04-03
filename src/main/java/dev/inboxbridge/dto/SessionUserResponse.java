package dev.inboxbridge.dto;

public record SessionUserResponse(
        Long id,
        Long currentSessionId,
        String username,
        String role,
        boolean approved,
        boolean mustChangePassword,
        int passkeyCount,
        boolean passwordConfigured,
        boolean deviceLocationCaptured) {
}
