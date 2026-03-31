package dev.inboxbridge.dto;

import java.util.List;

/**
 * Public, non-sensitive authentication UI options needed before sign-in.
 */
public record AuthUiOptionsResponse(
        boolean multiUserEnabled,
        boolean bootstrapLoginPrefillEnabled,
        boolean microsoftOAuthAvailable,
        boolean googleOAuthAvailable,
        boolean registrationChallengeEnabled,
        String registrationChallengeProvider,
        List<String> sourceOAuthProviders) {
}
