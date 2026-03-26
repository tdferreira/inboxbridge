package dev.inboxbridge.dto;

public record UpdateUserGmailConfigRequest(
        String destinationUser,
        String clientId,
        String clientSecret,
        String refreshToken,
        String redirectUri,
        Boolean createMissingLabels,
        Boolean neverMarkSpam,
        Boolean processForCalendar) {
}
