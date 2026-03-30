package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AuthSecuritySettingsView;
import dev.inboxbridge.dto.UpdateAuthSecuritySettingsRequest;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthSecuritySettingsService {

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    SystemAuthSecuritySettingRepository repository;

    public EffectiveAuthSecuritySettings effectiveSettings() {
        SystemAuthSecuritySetting setting = repository.findSingleton().orElse(null);
        return new EffectiveAuthSecuritySettings(
                setting != null && setting.loginFailureThresholdOverride != null
                        ? setting.loginFailureThresholdOverride
                        : inboxBridgeConfig.security().auth().loginFailureThreshold(),
                setting != null && setting.loginInitialBlockOverride != null && !setting.loginInitialBlockOverride.isBlank()
                        ? parsePositiveDuration(setting.loginInitialBlockOverride, "Initial login block")
                        : inboxBridgeConfig.security().auth().loginInitialBlock(),
                setting != null && setting.loginMaxBlockOverride != null && !setting.loginMaxBlockOverride.isBlank()
                        ? parsePositiveDuration(setting.loginMaxBlockOverride, "Maximum login block")
                        : inboxBridgeConfig.security().auth().loginMaxBlock(),
                setting != null && setting.registrationChallengeEnabledOverride != null
                        ? setting.registrationChallengeEnabledOverride
                        : inboxBridgeConfig.security().auth().registrationChallengeEnabled(),
                setting != null && setting.registrationChallengeTtlOverride != null && !setting.registrationChallengeTtlOverride.isBlank()
                        ? parsePositiveDuration(setting.registrationChallengeTtlOverride, "Registration challenge TTL")
                        : inboxBridgeConfig.security().auth().registrationChallengeTtl());
    }

    public AuthSecuritySettingsView view() {
        SystemAuthSecuritySetting setting = repository.findSingleton().orElse(null);
        EffectiveAuthSecuritySettings effective = effectiveSettings();
        return new AuthSecuritySettingsView(
                inboxBridgeConfig.security().auth().loginFailureThreshold(),
                setting == null ? null : setting.loginFailureThresholdOverride,
                effective.loginFailureThreshold(),
                inboxBridgeConfig.security().auth().loginInitialBlock().toString(),
                setting == null ? null : setting.loginInitialBlockOverride,
                effective.loginInitialBlock().toString(),
                inboxBridgeConfig.security().auth().loginMaxBlock().toString(),
                setting == null ? null : setting.loginMaxBlockOverride,
                effective.loginMaxBlock().toString(),
                inboxBridgeConfig.security().auth().registrationChallengeEnabled(),
                setting == null ? null : setting.registrationChallengeEnabledOverride,
                effective.registrationChallengeEnabled(),
                inboxBridgeConfig.security().auth().registrationChallengeTtl().toString(),
                setting == null ? null : setting.registrationChallengeTtlOverride,
                effective.registrationChallengeTtl().toString());
    }

    @Transactional
    public AuthSecuritySettingsView update(UpdateAuthSecuritySettingsRequest request) {
        SystemAuthSecuritySetting setting = repository.findSingleton().orElseGet(SystemAuthSecuritySetting::new);
        if (setting.id == null) {
            setting.id = SystemAuthSecuritySetting.SINGLETON_ID;
        }
        setting.loginFailureThresholdOverride = normalizeThresholdOverride(request.loginFailureThresholdOverride());
        setting.loginInitialBlockOverride = normalizePositiveDurationOverride(request.loginInitialBlockOverride(), "Initial login block");
        setting.loginMaxBlockOverride = normalizePositiveDurationOverride(request.loginMaxBlockOverride(), "Maximum login block");
        setting.registrationChallengeEnabledOverride = request.registrationChallengeEnabledOverride();
        setting.registrationChallengeTtlOverride = normalizePositiveDurationOverride(
                request.registrationChallengeTtlOverride(),
                "Registration challenge TTL");
        Duration effectiveInitial = setting.loginInitialBlockOverride == null
                ? inboxBridgeConfig.security().auth().loginInitialBlock()
                : parsePositiveDuration(setting.loginInitialBlockOverride, "Initial login block");
        Duration effectiveMax = setting.loginMaxBlockOverride == null
                ? inboxBridgeConfig.security().auth().loginMaxBlock()
                : parsePositiveDuration(setting.loginMaxBlockOverride, "Maximum login block");
        if (effectiveMax.compareTo(effectiveInitial) < 0) {
            throw new IllegalArgumentException("Maximum login block must be greater than or equal to the initial login block");
        }
        setting.updatedAt = Instant.now();
        repository.persist(setting);
        return view();
    }

    private Integer normalizeThresholdOverride(Integer override) {
        if (override == null) {
            return null;
        }
        if (override < 1 || override > 50) {
            throw new IllegalArgumentException("Failed login threshold must be between 1 and 50 attempts");
        }
        return override;
    }

    private String normalizePositiveDurationOverride(String override, String label) {
        if (override == null || override.isBlank()) {
            return null;
        }
        return parsePositiveDuration(override.trim(), label).toString();
    }

    Duration parsePositiveDuration(String rawValue, String fieldLabel) {
        Duration duration = parseDurationValue(rawValue, fieldLabel);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldLabel + " must be greater than zero");
        }
        return duration;
    }

    private Duration parseDurationValue(String rawValue, String fieldLabel) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        String normalized = rawValue.trim();
        if (normalized.startsWith("P") || normalized.startsWith("p")) {
            return Duration.parse(normalized.toUpperCase(Locale.ROOT));
        }
        return parseShorthandDuration(normalized);
    }

    private Duration parseShorthandDuration(String value) {
        if (value.length() < 2) {
            throw new IllegalArgumentException("Unsupported duration format. Use values like 30s, 5m, 1h, 1d, or ISO-8601 such as PT5M");
        }
        char unit = Character.toLowerCase(value.charAt(value.length() - 1));
        long amount;
        try {
            amount = Long.parseLong(value.substring(0, value.length() - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported duration format. Use values like 30s, 5m, 1h, 1d, or ISO-8601 such as PT5M", e);
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit. Use s, m, h, d, or ISO-8601 values");
        };
    }

    public record EffectiveAuthSecuritySettings(
            int loginFailureThreshold,
            Duration loginInitialBlock,
            Duration loginMaxBlock,
            boolean registrationChallengeEnabled,
            Duration registrationChallengeTtl) {
    }
}
