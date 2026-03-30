package dev.inboxbridge.dto;

/**
 * Describes effective authentication abuse-protection behavior together with
 * env defaults and optional admin-managed overrides.
 */
public record AuthSecuritySettingsView(
        int defaultLoginFailureThreshold,
        Integer loginFailureThresholdOverride,
        int effectiveLoginFailureThreshold,
        String defaultLoginInitialBlock,
        String loginInitialBlockOverride,
        String effectiveLoginInitialBlock,
        String defaultLoginMaxBlock,
        String loginMaxBlockOverride,
        String effectiveLoginMaxBlock,
        boolean defaultRegistrationChallengeEnabled,
        Boolean registrationChallengeEnabledOverride,
        boolean effectiveRegistrationChallengeEnabled,
        String defaultRegistrationChallengeTtl,
        String registrationChallengeTtlOverride,
        String effectiveRegistrationChallengeTtl) {
}
