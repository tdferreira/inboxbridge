package dev.inboxbridge.dto;

/**
 * Admin-managed override request for authentication abuse-protection controls.
 * Null or blank values clear the DB override and fall back to the env default.
 */
public record UpdateAuthSecuritySettingsRequest(
        Integer loginFailureThresholdOverride,
        String loginInitialBlockOverride,
        String loginMaxBlockOverride,
        Boolean registrationChallengeEnabledOverride,
        String registrationChallengeTtlOverride) {
}
