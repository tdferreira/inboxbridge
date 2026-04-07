package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.persistence.SystemPollingSetting;
import dev.inboxbridge.persistence.SystemPollingSettingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Resolves system-wide polling behavior by combining environment defaults with
 * optional admin-ui overrides stored in PostgreSQL.
 */
@ApplicationScoped
public class PollingSettingsService {

    public static final Duration MIN_POLL_INTERVAL = Duration.ofSeconds(5);
    public static final int DEFAULT_MANUAL_TRIGGER_LIMIT_COUNT = 5;
    public static final int DEFAULT_MANUAL_TRIGGER_LIMIT_WINDOW_SECONDS = 60;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    SystemPollingSettingRepository repository;

    @Transactional
    public EffectivePollingSettings effectiveSettings() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        boolean effectivePollEnabled = setting != null && setting.pollEnabledOverride != null
                ? setting.pollEnabledOverride
                : inboxBridgeConfig.pollEnabled();
        String effectivePollInterval = setting != null && setting.pollIntervalOverride != null && !setting.pollIntervalOverride.isBlank()
                ? setting.pollIntervalOverride
                : inboxBridgeConfig.pollInterval();
        int effectiveFetchWindow = setting != null && setting.fetchWindowOverride != null
                ? setting.fetchWindowOverride
                : inboxBridgeConfig.fetchWindow();
        return new EffectivePollingSettings(
                effectivePollEnabled,
                effectivePollInterval,
                parseDuration(effectivePollInterval),
                effectiveFetchWindow);
    }

    @Transactional
    public ManualPollRateLimit effectiveManualPollRateLimit() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        int effectiveCount = setting != null && setting.manualTriggerLimitCountOverride != null
                ? setting.manualTriggerLimitCountOverride
                : DEFAULT_MANUAL_TRIGGER_LIMIT_COUNT;
        int effectiveWindowSeconds = setting != null && setting.manualTriggerLimitWindowSecondsOverride != null
                ? setting.manualTriggerLimitWindowSecondsOverride
                : DEFAULT_MANUAL_TRIGGER_LIMIT_WINDOW_SECONDS;
        return new ManualPollRateLimit(
                effectiveCount,
                Duration.ofSeconds(effectiveWindowSeconds),
                effectiveWindowSeconds);
    }

    @Transactional
    public EffectiveThrottleSettings effectiveThrottleSettings() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        Duration effectiveSourceHostMinSpacing = setting != null && setting.sourceHostMinSpacingOverride != null && !setting.sourceHostMinSpacingOverride.isBlank()
                ? parsePositiveDuration(setting.sourceHostMinSpacingOverride, "Source host minimum spacing")
                : inboxBridgeConfig.sourceHostMinSpacing();
        int effectiveSourceHostMaxConcurrency = setting != null && setting.sourceHostMaxConcurrencyOverride != null
                ? setting.sourceHostMaxConcurrencyOverride
                : inboxBridgeConfig.sourceHostMaxConcurrency();
        Duration effectiveDestinationProviderMinSpacing = setting != null && setting.destinationProviderMinSpacingOverride != null
                && !setting.destinationProviderMinSpacingOverride.isBlank()
                        ? parsePositiveDuration(setting.destinationProviderMinSpacingOverride, "Destination provider minimum spacing")
                        : inboxBridgeConfig.destinationProviderMinSpacing();
        int effectiveDestinationProviderMaxConcurrency = setting != null && setting.destinationProviderMaxConcurrencyOverride != null
                ? setting.destinationProviderMaxConcurrencyOverride
                : inboxBridgeConfig.destinationProviderMaxConcurrency();
        Duration effectiveThrottleLeaseTtl = setting != null && setting.throttleLeaseTtlOverride != null && !setting.throttleLeaseTtlOverride.isBlank()
                ? parsePositiveDuration(setting.throttleLeaseTtlOverride, "Throttle lease TTL")
                : inboxBridgeConfig.throttleLeaseTtl();
        int effectiveAdaptiveThrottleMaxMultiplier = setting != null && setting.adaptiveThrottleMaxMultiplierOverride != null
                ? setting.adaptiveThrottleMaxMultiplierOverride
                : inboxBridgeConfig.adaptiveThrottleMaxMultiplier();
        double effectiveSuccessJitterRatio = setting != null && setting.successJitterRatioOverride != null
                ? setting.successJitterRatioOverride
                : inboxBridgeConfig.successJitterRatio();
        Duration effectiveMaxSuccessJitter = setting != null && setting.maxSuccessJitterOverride != null && !setting.maxSuccessJitterOverride.isBlank()
                ? parseNonNegativeDuration(setting.maxSuccessJitterOverride, "Maximum success jitter")
                : inboxBridgeConfig.maxSuccessJitter();
        return new EffectiveThrottleSettings(
                effectiveSourceHostMinSpacing,
                effectiveSourceHostMaxConcurrency,
                effectiveDestinationProviderMinSpacing,
                effectiveDestinationProviderMaxConcurrency,
                effectiveThrottleLeaseTtl,
                effectiveAdaptiveThrottleMaxMultiplier,
                effectiveSuccessJitterRatio,
                effectiveMaxSuccessJitter);
    }

    public AdminPollingSettingsView view() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        EffectivePollingSettings effective = effectiveSettings();
        EffectiveThrottleSettings effectiveThrottle = effectiveThrottleSettings();
        return new AdminPollingSettingsView(
                inboxBridgeConfig.pollEnabled(),
                setting == null ? null : setting.pollEnabledOverride,
                effective.pollEnabled(),
                inboxBridgeConfig.pollInterval(),
                setting == null ? null : setting.pollIntervalOverride,
                effective.pollIntervalText(),
                inboxBridgeConfig.fetchWindow(),
                setting == null ? null : setting.fetchWindowOverride,
                effective.fetchWindow(),
                DEFAULT_MANUAL_TRIGGER_LIMIT_COUNT,
                setting == null ? null : setting.manualTriggerLimitCountOverride,
                effectiveManualPollRateLimit().maxRuns(),
                DEFAULT_MANUAL_TRIGGER_LIMIT_WINDOW_SECONDS,
                setting == null ? null : setting.manualTriggerLimitWindowSecondsOverride,
                effectiveManualPollRateLimit().windowSeconds(),
                inboxBridgeConfig.sourceHostMinSpacing().toString(),
                setting == null ? null : setting.sourceHostMinSpacingOverride,
                effectiveThrottle.sourceHostMinSpacing().toString(),
                inboxBridgeConfig.sourceHostMaxConcurrency(),
                setting == null ? null : setting.sourceHostMaxConcurrencyOverride,
                effectiveThrottle.sourceHostMaxConcurrency(),
                inboxBridgeConfig.destinationProviderMinSpacing().toString(),
                setting == null ? null : setting.destinationProviderMinSpacingOverride,
                effectiveThrottle.destinationProviderMinSpacing().toString(),
                inboxBridgeConfig.destinationProviderMaxConcurrency(),
                setting == null ? null : setting.destinationProviderMaxConcurrencyOverride,
                effectiveThrottle.destinationProviderMaxConcurrency(),
                inboxBridgeConfig.throttleLeaseTtl().toString(),
                setting == null ? null : setting.throttleLeaseTtlOverride,
                effectiveThrottle.throttleLeaseTtl().toString(),
                inboxBridgeConfig.adaptiveThrottleMaxMultiplier(),
                setting == null ? null : setting.adaptiveThrottleMaxMultiplierOverride,
                effectiveThrottle.adaptiveThrottleMaxMultiplier(),
                inboxBridgeConfig.successJitterRatio(),
                setting == null ? null : setting.successJitterRatioOverride,
                effectiveThrottle.successJitterRatio(),
                inboxBridgeConfig.maxSuccessJitter().toString(),
                setting == null ? null : setting.maxSuccessJitterOverride,
                effectiveThrottle.maxSuccessJitter().toString());
    }

    @Transactional
    public AdminPollingSettingsView update(UpdateAdminPollingSettingsRequest request) {
        SystemPollingSetting setting = repository.findSingleton().orElseGet(SystemPollingSetting::new);
        if (setting.id == null) {
            setting.id = SystemPollingSetting.SINGLETON_ID;
        }
        setting.pollEnabledOverride = request.pollEnabledOverride();
        setting.pollIntervalOverride = normalizeIntervalOverride(request.pollIntervalOverride());
        setting.fetchWindowOverride = normalizeFetchWindowOverride(request.fetchWindowOverride());
        setting.manualTriggerLimitCountOverride = normalizeManualTriggerLimitCountOverride(request.manualTriggerLimitCountOverride());
        setting.manualTriggerLimitWindowSecondsOverride = normalizeManualTriggerLimitWindowSecondsOverride(
                request.manualTriggerLimitWindowSecondsOverride());
        setting.sourceHostMinSpacingOverride = normalizePositiveDurationOverride(
                request.sourceHostMinSpacingOverride(),
                "Source host minimum spacing");
        setting.sourceHostMaxConcurrencyOverride = normalizePositiveIntegerOverride(
                request.sourceHostMaxConcurrencyOverride(),
                1,
                100,
                "Source host max concurrency");
        setting.destinationProviderMinSpacingOverride = normalizePositiveDurationOverride(
                request.destinationProviderMinSpacingOverride(),
                "Destination provider minimum spacing");
        setting.destinationProviderMaxConcurrencyOverride = normalizePositiveIntegerOverride(
                request.destinationProviderMaxConcurrencyOverride(),
                1,
                100,
                "Destination provider max concurrency");
        setting.throttleLeaseTtlOverride = normalizePositiveDurationOverride(
                request.throttleLeaseTtlOverride(),
                "Throttle lease TTL");
        setting.adaptiveThrottleMaxMultiplierOverride = normalizePositiveIntegerOverride(
                request.adaptiveThrottleMaxMultiplierOverride(),
                1,
                100,
                "Adaptive throttle max multiplier");
        setting.successJitterRatioOverride = normalizeSuccessJitterRatioOverride(request.successJitterRatioOverride());
        setting.maxSuccessJitterOverride = normalizeNonNegativeDurationOverride(
                request.maxSuccessJitterOverride(),
                "Maximum success jitter");
        setting.updatedAt = Instant.now();
        repository.persist(setting);
        return view();
    }

    private String normalizeIntervalOverride(String override) {
        if (override == null || override.isBlank()) {
            return null;
        }
        String normalized = override.trim();
        parseDuration(normalized);
        return normalized;
    }

    private Integer normalizeFetchWindowOverride(Integer override) {
        if (override == null) {
            return null;
        }
        if (override < 1 || override > 500) {
            throw new IllegalArgumentException("Fetch window override must be between 1 and 500 messages");
        }
        return override;
    }

    private Integer normalizeManualTriggerLimitCountOverride(Integer override) {
        if (override == null) {
            return null;
        }
        if (override < 1 || override > 100) {
            throw new IllegalArgumentException("Manual poll limit must be between 1 and 100 runs");
        }
        return override;
    }

    private Integer normalizeManualTriggerLimitWindowSecondsOverride(Integer override) {
        if (override == null) {
            return null;
        }
        if (override < 10 || override > 3600) {
            throw new IllegalArgumentException("Manual poll rate-limit window must be between 10 and 3600 seconds");
        }
        return override;
    }

    private String normalizePositiveDurationOverride(String override, String fieldLabel) {
        if (override == null || override.isBlank()) {
            return null;
        }
        return parsePositiveDuration(override.trim(), fieldLabel).toString();
    }

    private String normalizeNonNegativeDurationOverride(String override, String fieldLabel) {
        if (override == null || override.isBlank()) {
            return null;
        }
        return parseNonNegativeDuration(override.trim(), fieldLabel).toString();
    }

    private Integer normalizePositiveIntegerOverride(Integer override, int min, int max, String fieldLabel) {
        if (override == null) {
            return null;
        }
        if (override < min || override > max) {
            throw new IllegalArgumentException(fieldLabel + " must be between " + min + " and " + max);
        }
        return override;
    }

    private Double normalizeSuccessJitterRatioOverride(Double override) {
        if (override == null) {
            return null;
        }
        if (override < 0d || override > 1d) {
            throw new IllegalArgumentException("Success jitter ratio must be between 0 and 1");
        }
        return override;
    }

    Duration parsePositiveDuration(String rawValue, String fieldLabel) {
        Duration duration = parseDurationValue(rawValue, fieldLabel);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldLabel + " must be greater than zero");
        }
        return duration;
    }

    Duration parseNonNegativeDuration(String rawValue, String fieldLabel) {
        Duration duration = parseDurationValue(rawValue, fieldLabel);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(fieldLabel + " must not be negative");
        }
        return duration;
    }

    public Duration parseDuration(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Poll interval is required");
        }
        String normalized = rawValue.trim();
        Duration duration = parseDurationValue(normalized, "Poll interval");
        if (duration.compareTo(MIN_POLL_INTERVAL) < 0) {
            throw new IllegalArgumentException("Poll interval must be at least 5 seconds");
        }
        return duration;
    }

    private Duration parseDurationValue(String rawValue, String fieldLabel) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        String normalized = rawValue.trim();
        Duration duration;
        if (normalized.startsWith("P") || normalized.startsWith("p")) {
            duration = Duration.parse(normalized.toUpperCase(Locale.ROOT));
        } else {
            duration = parseShorthandDuration(normalized);
        }
        return duration;
    }

    private Duration parseShorthandDuration(String value) {
        if (value.length() < 2) {
            throw new IllegalArgumentException("Unsupported poll interval format. Use values like 30s, 5m, 1h, 1d, or ISO-8601 such as PT5M");
        }
        char unit = Character.toLowerCase(value.charAt(value.length() - 1));
        long amount;
        try {
            amount = Long.parseLong(value.substring(0, value.length() - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported poll interval format. Use values like 30s, 5m, 1h, 1d, or ISO-8601 such as PT5M", e);
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported poll interval unit. Use s, m, h, d, or ISO-8601 values");
        };
    }

    /**
     * Effective resolved system polling settings.
     */
    public record EffectivePollingSettings(
            boolean pollEnabled,
            String pollIntervalText,
            Duration pollInterval,
            int fetchWindow) {
    }

    /**
     * Effective resolved manual trigger throttling settings.
     */
    public record ManualPollRateLimit(
            int maxRuns,
            Duration window,
            int windowSeconds) {
    }

    /**
     * Effective resolved runtime throttling and jitter settings.
     */
    public record EffectiveThrottleSettings(
            Duration sourceHostMinSpacing,
            int sourceHostMaxConcurrency,
            Duration destinationProviderMinSpacing,
            int destinationProviderMaxConcurrency,
            Duration throttleLeaseTtl,
            int adaptiveThrottleMaxMultiplier,
            double successJitterRatio,
            Duration maxSuccessJitter) {
    }
}
