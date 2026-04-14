package dev.inboxbridge.dto;

/**
 * Summarizes deployment-wide trust material revoked or cleared after a
 * re-encryption workflow.
 */
public record SecretReencryptionFollowUpView(
        int browserExtensionSessionsRevoked,
        int remoteSessionsRevoked,
        int cachedOAuthAccessTokensCleared) {
}
