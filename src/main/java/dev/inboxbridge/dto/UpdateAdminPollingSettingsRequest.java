package dev.inboxbridge.dto;

/**
 * Admin-managed override request for runtime polling controls. Any null or
 * blank field clears the corresponding DB override and falls back to the env
 * default.
 */
public record UpdateAdminPollingSettingsRequest(
        Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride) {
}
