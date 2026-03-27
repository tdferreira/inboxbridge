package dev.inboxbridge.dto;

/**
 * Describes the Gmail account connection state for one user.
 *
 * <p>The client-id / client-secret flags represent user-specific overrides
 * stored for that account. Deployment-wide shared Google OAuth app settings
 * are surfaced separately through {@code sharedClientConfigured}. The
 * {@code destinationUser} field is the Gmail API user id value, which is
 * usually {@code me} and therefore not necessarily the literal mailbox
 * address.</p>
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
