package dev.inboxbridge.dto;

/**
 * Describes the effective polling behavior together with the env defaults and
 * any admin-managed overrides currently stored in PostgreSQL.
 */
public record AdminPollingSettingsView(
        boolean defaultPollEnabled,
        Boolean pollEnabledOverride,
        boolean effectivePollEnabled,
        String defaultPollInterval,
        String pollIntervalOverride,
        String effectivePollInterval,
        int defaultFetchWindow,
        Integer fetchWindowOverride,
        int effectiveFetchWindow,
        int defaultManualTriggerLimitCount,
        Integer manualTriggerLimitCountOverride,
        int effectiveManualTriggerLimitCount,
        int defaultManualTriggerLimitWindowSeconds,
        Integer manualTriggerLimitWindowSecondsOverride,
        int effectiveManualTriggerLimitWindowSeconds,
        String defaultSourceHostMinSpacing,
        String sourceHostMinSpacingOverride,
        String effectiveSourceHostMinSpacing,
        int defaultSourceHostMaxConcurrency,
        Integer sourceHostMaxConcurrencyOverride,
        int effectiveSourceHostMaxConcurrency,
        String defaultDestinationProviderMinSpacing,
        String destinationProviderMinSpacingOverride,
        String effectiveDestinationProviderMinSpacing,
        int defaultDestinationProviderMaxConcurrency,
        Integer destinationProviderMaxConcurrencyOverride,
        int effectiveDestinationProviderMaxConcurrency,
        String defaultThrottleLeaseTtl,
        String throttleLeaseTtlOverride,
        String effectiveThrottleLeaseTtl,
        int defaultAdaptiveThrottleMaxMultiplier,
        Integer adaptiveThrottleMaxMultiplierOverride,
        int effectiveAdaptiveThrottleMaxMultiplier,
        double defaultSuccessJitterRatio,
        Double successJitterRatioOverride,
        double effectiveSuccessJitterRatio,
        String defaultMaxSuccessJitter,
        String maxSuccessJitterOverride,
        String effectiveMaxSuccessJitter) {
}
