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
        int effectiveFetchWindow) {
}
