package dev.inboxbridge.dto;

/**
 * Browser-facing self-registration challenge metadata used to slow automated
 * account requests without introducing an external CAPTCHA dependency.
 */
public record RegistrationChallengeResponse(
        boolean enabled,
        String challengeId,
        String prompt) {

    public static RegistrationChallengeResponse disabled() {
        return new RegistrationChallengeResponse(false, null, null);
    }
}
