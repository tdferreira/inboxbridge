package dev.inboxbridge.dto;

/**
 * Starts a one-time browser-session handoff so an extension can finish sign-in
 * on the normal InboxBridge origin and later redeem extension-scoped tokens.
 */
public record ExtensionBrowserAuthStartRequest(
        String codeChallenge,
        String codeChallengeMethod,
        String label,
        String browserFamily,
        String extensionVersion) {
}
