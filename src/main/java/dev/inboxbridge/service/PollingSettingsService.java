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

    public AdminPollingSettingsView view() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        EffectivePollingSettings effective = effectiveSettings();
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
                effectiveManualPollRateLimit().windowSeconds());
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

    Duration parseDuration(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Poll interval is required");
        }
        String normalized = rawValue.trim();
        Duration duration;
        if (normalized.startsWith("P") || normalized.startsWith("p")) {
            duration = Duration.parse(normalized.toUpperCase(Locale.ROOT));
        } else {
            duration = parseShorthandDuration(normalized);
        }
        if (duration.compareTo(MIN_POLL_INTERVAL) < 0) {
            throw new IllegalArgumentException("Poll interval must be at least 5 seconds");
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
}
