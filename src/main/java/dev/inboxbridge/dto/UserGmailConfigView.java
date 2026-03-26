package dev.inboxbridge.dto;

/**
 * Describes the Gmail destination state for one user.
 *
 * <p>The client-id / client-secret flags represent user-specific overrides
 * stored for that account. Deployment-wide shared Google OAuth app settings
 * are surfaced separately through {@code sharedClientConfigured}.</p>
 */
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
