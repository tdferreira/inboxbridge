package dev.inboxbridge.dto;

/**
 * Updates per-source polling overrides for one mail fetcher.
 */
public record UpdateSourcePollingSettingsRequest(
        Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride) {
}
