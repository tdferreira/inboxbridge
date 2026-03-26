package dev.inboxbridge.dto;

/**
 * Updates per-user polling overrides for the authenticated account.
 */
public record UpdateUserPollingSettingsRequest(
        Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride) {
}
