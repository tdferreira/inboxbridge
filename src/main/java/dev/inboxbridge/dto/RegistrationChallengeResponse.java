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
            AltchaChallengeParametersResponse parameters,
            String signature) {
    }

    public record AltchaChallengeParametersResponse(
            String algorithm,
            String nonce,
            String salt,
            int cost,
            int keyLength,
            String keyPrefix,
            Long expiresAt) {
    }

    public static RegistrationChallengeResponse disabled() {
        return new RegistrationChallengeResponse(false, null, null, null);
    }
}
