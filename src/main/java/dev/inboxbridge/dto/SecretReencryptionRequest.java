package dev.inboxbridge.dto;

/**
 * Optional follow-up actions that can run immediately after stored secrets are
 * re-encrypted under the active provider or key version.
 */
public record SecretReencryptionRequest(
        boolean immediateExecutionOverride,
        boolean revokeBrowserExtensionSessions,
        boolean revokeRemoteSessions,
        boolean clearCachedOAuthAccessTokens) {
}
