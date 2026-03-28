package dev.inboxbridge.dto;

/**
 * Public, non-sensitive authentication UI options needed before sign-in.
 */
public record AuthUiOptionsResponse(
        boolean multiUserEnabled,
        boolean microsoftOAuthAvailable) {
}
