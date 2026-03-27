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
        int effectiveFetchWindow) {
}
