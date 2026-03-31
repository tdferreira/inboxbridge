package dev.inboxbridge.dto;

/**
 * Browser-facing self-registration challenge metadata used to slow automated
 * account requests without introducing an external CAPTCHA dependency.
 */
public record RegistrationChallengeResponse(
        boolean enabled,
        String provider,
        String siteKey,
        AltchaChallengeResponse altcha) {

    public record AltchaChallengeResponse(
            String challengeId,
            String algorithm,
            String challenge,
            String salt,
            String signature,
            long maxNumber) {
    }

    public static RegistrationChallengeResponse disabled() {
        return new RegistrationChallengeResponse(false, null, null, null);
    }
}
