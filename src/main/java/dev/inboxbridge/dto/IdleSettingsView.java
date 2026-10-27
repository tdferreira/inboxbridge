package dev.inboxbridge.dto;

/** Environment defaults, nullable database overrides and effective IDLE policy. */
public record IdleSettingsView(
        boolean defaultSafetyPollingEnabled, Boolean safetyPollingEnabledOverride, boolean effectiveSafetyPollingEnabled,
        String defaultRenewalInterval, String renewalIntervalOverride, String effectiveRenewalInterval,
        String defaultRenewalTimeout, String renewalTimeoutOverride, String effectiveRenewalTimeout,
        String defaultSafetyPollInterval, String safetyPollIntervalOverride, String effectiveSafetyPollInterval) {
    public IdleSettingsView(boolean defaultSafetyPollingEnabled, Boolean safetyPollingEnabledOverride, boolean effectiveSafetyPollingEnabled,
        String defaultRenewalInterval, String renewalIntervalOverride, String effectiveRenewalInterval,
        String defaultRenewalTimeout, String renewalTimeoutOverride, String effectiveRenewalTimeout) {
        this(defaultSafetyPollingEnabled, safetyPollingEnabledOverride, effectiveSafetyPollingEnabled, defaultRenewalInterval, renewalIntervalOverride, effectiveRenewalInterval, defaultRenewalTimeout, renewalTimeoutOverride, effectiveRenewalTimeout, "PT30M", null, "PT30M");
    }

}
