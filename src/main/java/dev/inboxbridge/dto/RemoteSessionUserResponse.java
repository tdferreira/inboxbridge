package dev.inboxbridge.dto;

public record RemoteSessionUserResponse(
        Long id,
        String username,
        String role,
        boolean canRunUserPoll,
        boolean canRunAllUsersPoll,
        boolean multiUserEnabled,
        boolean deviceLocationCaptured,
        String language) {
}
