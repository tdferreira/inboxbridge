package dev.inboxbridge.dto;

/**
 * Describes the authenticated user's resolved polling settings, including the
 * global defaults, optional user overrides, and the resulting effective values.
 */
public record UserPollingSettingsView(
        boolean defaultPollEnabled,
        Boolean pollEnabledOverride,
        boolean effectivePollEnabled,
        String defaultPollInterval,
        String pollIntervalOverride,
        String effectivePollInterval,
        int defaultFetchWindow,
        Integer fetchWindowOverride,
        int effectiveFetchWindow) {
}
