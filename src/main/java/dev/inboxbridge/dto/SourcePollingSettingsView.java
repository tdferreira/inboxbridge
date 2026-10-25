package dev.inboxbridge.dto;

/**
 * Describes the resolved polling settings for one specific source, including
 * the inherited base values and optional source-level overrides.
 */
public record SourcePollingSettingsView(
        String sourceId,
        boolean basePollEnabled,
        Boolean pollEnabledOverride,
        boolean effectivePollEnabled,
        String basePollInterval,
        String pollIntervalOverride,
        String effectivePollInterval,
        int baseFetchWindow,
        Integer fetchWindowOverride,
        int effectiveFetchWindow,
        boolean baseIdleSafetyPollingEnabled,
        Boolean idleSafetyPollingEnabledOverride,
        boolean effectiveIdleSafetyPollingEnabled,
        String baseIdleSafetyPollInterval, String idleSafetyPollIntervalOverride, String effectiveIdleSafetyPollInterval,
        String baseIdleRenewalInterval, String idleRenewalIntervalOverride, String effectiveIdleRenewalInterval) {
    public SourcePollingSettingsView(String sourceId,
        boolean basePollEnabled,
        Boolean pollEnabledOverride,
        boolean effectivePollEnabled,
        String basePollInterval,
        String pollIntervalOverride,
        String effectivePollInterval,
        int baseFetchWindow,
        Integer fetchWindowOverride,
        int effectiveFetchWindow,
        boolean baseIdleSafetyPollingEnabled,
        Boolean idleSafetyPollingEnabledOverride,
        boolean effectiveIdleSafetyPollingEnabled,
        String baseIdleSafetyPollInterval, String idleSafetyPollIntervalOverride, String effectiveIdleSafetyPollInterval) {
        this(sourceId, basePollEnabled, pollEnabledOverride, effectivePollEnabled, basePollInterval, pollIntervalOverride, effectivePollInterval, baseFetchWindow, fetchWindowOverride, effectiveFetchWindow, baseIdleSafetyPollingEnabled, idleSafetyPollingEnabledOverride, effectiveIdleSafetyPollingEnabled, baseIdleSafetyPollInterval, idleSafetyPollIntervalOverride, effectiveIdleSafetyPollInterval, "PT25M", null, "PT25M");
    }

    public SourcePollingSettingsView(String sourceId,
        boolean basePollEnabled,
        Boolean pollEnabledOverride,
        boolean effectivePollEnabled,
        String basePollInterval,
        String pollIntervalOverride,
        String effectivePollInterval,
        int baseFetchWindow,
        Integer fetchWindowOverride,
        int effectiveFetchWindow,
        boolean baseIdleSafetyPollingEnabled,
        Boolean idleSafetyPollingEnabledOverride,
        boolean effectiveIdleSafetyPollingEnabled) {
        this(sourceId, basePollEnabled, pollEnabledOverride, effectivePollEnabled, basePollInterval, pollIntervalOverride, effectivePollInterval, baseFetchWindow, fetchWindowOverride, effectiveFetchWindow, baseIdleSafetyPollingEnabled, idleSafetyPollingEnabledOverride, effectiveIdleSafetyPollingEnabled, "PT30M", null, "PT30M");
    }

    public SourcePollingSettingsView(String sourceId,
        boolean basePollEnabled,
        Boolean pollEnabledOverride,
        boolean effectivePollEnabled,
        String basePollInterval,
        String pollIntervalOverride,
        String effectivePollInterval,
        int baseFetchWindow,
        Integer fetchWindowOverride,
        int effectiveFetchWindow) {
        this(sourceId, basePollEnabled, pollEnabledOverride, effectivePollEnabled, basePollInterval, pollIntervalOverride, effectivePollInterval, baseFetchWindow, fetchWindowOverride, effectiveFetchWindow, true, null, true);
    }
}
