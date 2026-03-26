package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import dev.inboxbridge.config.BridgeConfig;
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

    @Inject
    BridgeConfig bridgeConfig;

    @Inject
    SystemPollingSettingRepository repository;

    public EffectivePollingSettings effectiveSettings() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        boolean effectivePollEnabled = setting != null && setting.pollEnabledOverride != null
                ? setting.pollEnabledOverride
                : bridgeConfig.pollEnabled();
        String effectivePollInterval = setting != null && setting.pollIntervalOverride != null && !setting.pollIntervalOverride.isBlank()
                ? setting.pollIntervalOverride
                : bridgeConfig.pollInterval();
        int effectiveFetchWindow = setting != null && setting.fetchWindowOverride != null
                ? setting.fetchWindowOverride
                : bridgeConfig.fetchWindow();
        return new EffectivePollingSettings(
                effectivePollEnabled,
                effectivePollInterval,
                parseDuration(effectivePollInterval),
                effectiveFetchWindow);
    }

    public AdminPollingSettingsView view() {
        SystemPollingSetting setting = repository.findSingleton().orElse(null);
        EffectivePollingSettings effective = effectiveSettings();
        return new AdminPollingSettingsView(
                bridgeConfig.pollEnabled(),
                setting == null ? null : setting.pollEnabledOverride,
                effective.pollEnabled(),
                bridgeConfig.pollInterval(),
                setting == null ? null : setting.pollIntervalOverride,
                effective.pollIntervalText(),
                bridgeConfig.fetchWindow(),
                setting == null ? null : setting.fetchWindowOverride,
                effective.fetchWindow());
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
}
