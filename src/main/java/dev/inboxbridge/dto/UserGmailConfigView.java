package dev.inboxbridge.dto;

public record UserGmailConfigView(
        String destinationUser,
        boolean clientIdConfigured,
        boolean clientSecretConfigured,
        boolean refreshTokenConfigured,
        String redirectUri,
        String defaultRedirectUri,
        boolean sharedClientConfigured,
        boolean createMissingLabels,
        boolean neverMarkSpam,
        boolean processForCalendar) {
}
