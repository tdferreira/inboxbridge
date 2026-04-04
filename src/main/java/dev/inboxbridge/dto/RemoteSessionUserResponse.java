package dev.inboxbridge.dto;

public record RemoteSessionUserResponse(
        Long id,
        Long currentSessionId,
        String username,
        String role,
        boolean canRunUserPoll,
        boolean canRunAllUsersPoll,
        boolean multiUserEnabled,
        boolean deviceLocationCaptured,
        String language,
        String timezoneMode,
        String timezone) {
}
