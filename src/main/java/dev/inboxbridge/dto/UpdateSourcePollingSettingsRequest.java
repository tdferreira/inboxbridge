package dev.inboxbridge.dto;

/**
 * Updates per-source polling overrides for one mail fetcher.
 */
public record UpdateSourcePollingSettingsRequest(
        Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride,
        Boolean idleSafetyPollingEnabledOverride,
        String idleSafetyPollIntervalOverride,
        String idleRenewalIntervalOverride) {
    public UpdateSourcePollingSettingsRequest(Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride,
        Boolean idleSafetyPollingEnabledOverride,
        String idleSafetyPollIntervalOverride) {
        this(pollEnabledOverride, pollIntervalOverride, fetchWindowOverride, idleSafetyPollingEnabledOverride, idleSafetyPollIntervalOverride, null);
    }

    public UpdateSourcePollingSettingsRequest(Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride,
        Boolean idleSafetyPollingEnabledOverride) {
        this(pollEnabledOverride, pollIntervalOverride, fetchWindowOverride, idleSafetyPollingEnabledOverride, null);
    }

    public UpdateSourcePollingSettingsRequest(Boolean pollEnabledOverride,
        String pollIntervalOverride,
        Integer fetchWindowOverride) {
        this(pollEnabledOverride, pollIntervalOverride, fetchWindowOverride, null);
    }
}
