package dev.inboxbridge.dto;

public record SessionUserResponse(
        Long id,
        String username,
        String role,
        boolean approved,
        boolean mustChangePassword,
        int passkeyCount,
        boolean passwordConfigured) {
}
