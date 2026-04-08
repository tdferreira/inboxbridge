package dev.inboxbridge.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AuthSecuritySettingsView;
import dev.inboxbridge.dto.UpdateAuthSecuritySettingsRequest;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.service.SecretEncryptionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthSecuritySettingsService {

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    SystemAuthSecuritySettingRepository repository;

    @Inject
    SecretEncryptionService secretEncryptionService;

    public void setConfig(InboxBridgeConfig inboxBridgeConfig) {
        this.inboxBridgeConfig = inboxBridgeConfig;
    }

    public void setRepository(SystemAuthSecuritySettingRepository repository) {
        this.repository = repository;
    }

    public void setSecretEncryptionService(SecretEncryptionService secretEncryptionService) {
        this.secretEncryptionService = secretEncryptionService;
    }

    public EffectiveAuthSecuritySettings effectiveSettings() {
        SystemAuthSecuritySetting setting = repository.findSingleton().orElse(null);
        return resolveEffectiveSettings(setting);
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
                effective.registrationChallengeTtl().toString(),
                normalizeRegistrationCaptchaProviderOverride(inboxBridgeConfig.security().auth().registrationChallengeProvider(), "Registration CAPTCHA provider"),
                setting == null ? null : setting.registrationChallengeProviderOverride,
                effective.registrationChallengeProvider(),
                availableRegistrationCaptchaProviders(),
                defaultTurnstileSiteKey(),
                setting == null ? null : setting.registrationTurnstileSiteKeyOverride,
                registrationTurnstileConfigured(setting),
                defaultHcaptchaSiteKey(),
                setting == null ? null : setting.registrationHcaptchaSiteKeyOverride,
                registrationHcaptchaConfigured(setting),
                inboxBridgeConfig.security().auth().geoIp().enabled(),
                setting == null ? null : setting.geoIpEnabledOverride,
                effective.geoIpEnabled(),
                normalizeGeoIpProviderOverride(inboxBridgeConfig.security().auth().geoIp().primaryProvider(), "Primary Geo-IP provider"),
                setting == null ? null : setting.geoIpPrimaryProviderOverride,
                effective.geoIpPrimaryProvider(),
                normalizeGeoIpProviderChainOverride(inboxBridgeConfig.security().auth().geoIp().fallbackProviders()),
                setting == null ? null : setting.geoIpFallbackProvidersOverride,
                effective.geoIpFallbackProviders(),
                inboxBridgeConfig.security().auth().geoIp().cacheTtl().toString(),
                setting == null ? null : setting.geoIpCacheTtlOverride,
                effective.geoIpCacheTtl().toString(),
                inboxBridgeConfig.security().auth().geoIp().providerCooldown().toString(),
                setting == null ? null : setting.geoIpProviderCooldownOverride,
                effective.geoIpProviderCooldown().toString(),
                inboxBridgeConfig.security().auth().geoIp().requestTimeout().toString(),
                setting == null ? null : setting.geoIpRequestTimeoutOverride,
                effective.geoIpRequestTimeout().toString(),
                availableGeoIpProviders(),
                geoIpIpinfoTokenConfigured(setting),
                secretEncryptionService.isConfigured());
    }

    @Transactional
    public AuthSecuritySettingsView update(UpdateAuthSecuritySettingsRequest request) {
        if (!secretEncryptionService.isConfigured()
                && (isProvided(request.geoIpIpinfoToken())
                        || isProvided(request.registrationTurnstileSecret())
                        || isProvided(request.registrationHcaptchaSecret()))) {
            throw new IllegalStateException("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
        }
        SystemAuthSecuritySetting setting = repository.findSingleton().orElseGet(SystemAuthSecuritySetting::new);
        if (setting.id == null) {
            setting.id = SystemAuthSecuritySetting.SINGLETON_ID;
        }
        setting.keyVersion = currentKeyVersion(setting);
        setting.loginFailureThresholdOverride = normalizeThresholdOverride(request.loginFailureThresholdOverride());
        setting.loginInitialBlockOverride = normalizePositiveDurationOverride(request.loginInitialBlockOverride(), "Initial login block");
        setting.loginMaxBlockOverride = normalizePositiveDurationOverride(request.loginMaxBlockOverride(), "Maximum login block");
        setting.registrationChallengeEnabledOverride = request.registrationChallengeEnabledOverride();
        setting.registrationChallengeTtlOverride = normalizePositiveDurationOverride(
                request.registrationChallengeTtlOverride(),
                "Registration challenge TTL");
        setting.registrationChallengeProviderOverride = normalizeRegistrationCaptchaProviderOverride(
                request.registrationChallengeProviderOverride(),
                "Registration CAPTCHA provider");
        setting.registrationTurnstileSiteKeyOverride = normalizeOptionalText(request.registrationTurnstileSiteKeyOverride());
        setting.registrationHcaptchaSiteKeyOverride = normalizeOptionalText(request.registrationHcaptchaSiteKeyOverride());
        applyRegistrationTurnstileSecret(request.registrationTurnstileSecret(), setting);
        applyRegistrationHcaptchaSecret(request.registrationHcaptchaSecret(), setting);
        setting.geoIpEnabledOverride = request.geoIpEnabledOverride();
        setting.geoIpPrimaryProviderOverride = normalizeGeoIpProviderOverride(request.geoIpPrimaryProviderOverride(), "Primary Geo-IP provider");
        setting.geoIpFallbackProvidersOverride = normalizeGeoIpProviderChainOverride(request.geoIpFallbackProvidersOverride());
        setting.geoIpCacheTtlOverride = normalizePositiveDurationOverride(request.geoIpCacheTtlOverride(), "Geo-IP cache TTL");
        setting.geoIpProviderCooldownOverride = normalizePositiveDurationOverride(request.geoIpProviderCooldownOverride(), "Geo-IP provider cooldown");
        setting.geoIpRequestTimeoutOverride = normalizePositiveDurationOverride(request.geoIpRequestTimeoutOverride(), "Geo-IP request timeout");
        applyGeoIpIpinfoToken(request.geoIpIpinfoToken(), setting);
        validateConstraints(setting);
        setting.updatedAt = Instant.now();
        repository.persist(setting);
        return view();
    }

    private EffectiveAuthSecuritySettings resolveEffectiveSettings(SystemAuthSecuritySetting setting) {
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
                        : inboxBridgeConfig.security().auth().registrationChallengeTtl(),
                setting != null && setting.registrationChallengeProviderOverride != null && !setting.registrationChallengeProviderOverride.isBlank()
                        ? normalizeRegistrationCaptchaProviderOverride(setting.registrationChallengeProviderOverride, "Registration CAPTCHA provider")
                        : normalizeRegistrationCaptchaProviderOverride(inboxBridgeConfig.security().auth().registrationChallengeProvider(), "Registration CAPTCHA provider"),
                effectiveTurnstileSiteKey(setting),
                effectiveTurnstileSecret(setting),
                effectiveHcaptchaSiteKey(setting),
                effectiveHcaptchaSecret(setting),
                setting != null && setting.geoIpEnabledOverride != null
                        ? setting.geoIpEnabledOverride
                        : inboxBridgeConfig.security().auth().geoIp().enabled(),
                setting != null && setting.geoIpPrimaryProviderOverride != null && !setting.geoIpPrimaryProviderOverride.isBlank()
                        ? normalizeGeoIpProviderOverride(setting.geoIpPrimaryProviderOverride, "Primary Geo-IP provider")
                        : normalizeGeoIpProviderOverride(inboxBridgeConfig.security().auth().geoIp().primaryProvider(), "Primary Geo-IP provider"),
                setting != null && setting.geoIpFallbackProvidersOverride != null && !setting.geoIpFallbackProvidersOverride.isBlank()
                        ? normalizeGeoIpProviderChainOverride(setting.geoIpFallbackProvidersOverride)
                        : normalizeGeoIpProviderChainOverride(inboxBridgeConfig.security().auth().geoIp().fallbackProviders()),
                setting != null && setting.geoIpCacheTtlOverride != null && !setting.geoIpCacheTtlOverride.isBlank()
                        ? parsePositiveDuration(setting.geoIpCacheTtlOverride, "Geo-IP cache TTL")
                        : inboxBridgeConfig.security().auth().geoIp().cacheTtl(),
                setting != null && setting.geoIpProviderCooldownOverride != null && !setting.geoIpProviderCooldownOverride.isBlank()
                        ? parsePositiveDuration(setting.geoIpProviderCooldownOverride, "Geo-IP provider cooldown")
                        : inboxBridgeConfig.security().auth().geoIp().providerCooldown(),
                setting != null && setting.geoIpRequestTimeoutOverride != null && !setting.geoIpRequestTimeoutOverride.isBlank()
                        ? parsePositiveDuration(setting.geoIpRequestTimeoutOverride, "Geo-IP request timeout")
                        : inboxBridgeConfig.security().auth().geoIp().requestTimeout(),
                effectiveGeoIpIpinfoToken(setting));
    }

    private void validateConstraints(SystemAuthSecuritySetting setting) {
        EffectiveAuthSecuritySettings effective = resolveEffectiveSettings(setting);
        if (effective.loginMaxBlock().compareTo(effective.loginInitialBlock()) < 0) {
            throw new IllegalArgumentException("Maximum login block must be greater than or equal to the initial login block");
        }
        if (effective.geoIpPrimaryProvider() != null
                && effective.geoIpFallbackProviders() != null
                && Arrays.stream(effective.geoIpFallbackProviders().split(","))
                        .map(String::trim)
                        .anyMatch(effective.geoIpPrimaryProvider()::equalsIgnoreCase)) {
            throw new IllegalArgumentException("Geo-IP fallback providers must not repeat the primary provider");
        }
        validateRegistrationCaptchaProviderRequirements(effective);
        validateGeoIpProviderRequirements(effective);
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

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRegistrationCaptchaProviderOverride(String override, String label) {
        if (override == null || override.isBlank()) {
            return null;
        }
        String normalized = override.trim().toUpperCase(Locale.ROOT);
        try {
            RegistrationChallengeService.RegistrationCaptchaProvider provider = RegistrationChallengeService.RegistrationCaptchaProvider.valueOf(normalized);
            return provider.name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " must be one of: " + availableRegistrationCaptchaProviders(), e);
        }
    }

    private String normalizeGeoIpProviderOverride(String override, String label) {
        if (override == null || override.isBlank()) {
            return null;
        }
        String normalized = override.trim().toUpperCase(Locale.ROOT);
        try {
            GeoIpLocationService.GeoIpProvider provider = GeoIpLocationService.GeoIpProvider.valueOf(normalized);
            if (provider == GeoIpLocationService.GeoIpProvider.NONE) {
                throw new IllegalArgumentException(label + " must be a real provider");
            }
            return provider.name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " must be one of: " + availableGeoIpProviders(), e);
        }
    }

    private String normalizeGeoIpProviderChainOverride(String override) {
        if (override == null || override.isBlank()) {
            return null;
        }
        Set<String> providers = new LinkedHashSet<>();
        for (String rawProvider : override.split(",")) {
            String normalized = normalizeGeoIpProviderOverride(rawProvider, "Geo-IP fallback provider");
            if (normalized != null) {
                providers.add(normalized);
            }
        }
        if (providers.isEmpty()) {
            return null;
        }
        return String.join(",", providers);
    }

    private void applyRegistrationTurnstileSecret(String value, SystemAuthSecuritySetting setting) {
        applySecret(
                value,
                setting,
                "system-auth-security:registration-turnstile-secret",
                encrypted -> {
                    setting.registrationTurnstileSecretCiphertext = encrypted.ciphertextBase64();
                    setting.registrationTurnstileSecretNonce = encrypted.nonceBase64();
                },
                () -> {
                    setting.registrationTurnstileSecretCiphertext = null;
                    setting.registrationTurnstileSecretNonce = null;
                });
    }

    private void applyRegistrationHcaptchaSecret(String value, SystemAuthSecuritySetting setting) {
        applySecret(
                value,
                setting,
                "system-auth-security:registration-hcaptcha-secret",
                encrypted -> {
                    setting.registrationHcaptchaSecretCiphertext = encrypted.ciphertextBase64();
                    setting.registrationHcaptchaSecretNonce = encrypted.nonceBase64();
                },
                () -> {
                    setting.registrationHcaptchaSecretCiphertext = null;
                    setting.registrationHcaptchaSecretNonce = null;
                });
    }

    private void applyGeoIpIpinfoToken(String value, SystemAuthSecuritySetting setting) {
        applySecret(
                value,
                setting,
                "system-auth-security:geo-ip-ipinfo-token",
                encrypted -> {
                    setting.geoIpIpinfoTokenCiphertext = encrypted.ciphertextBase64();
                    setting.geoIpIpinfoTokenNonce = encrypted.nonceBase64();
                },
                () -> {
                    setting.geoIpIpinfoTokenCiphertext = null;
                    setting.geoIpIpinfoTokenNonce = null;
                });
    }

    private void applySecret(
            String value,
            SystemAuthSecuritySetting setting,
            String context,
            java.util.function.Consumer<SecretEncryptionService.EncryptedValue> saveEncrypted,
            Runnable clearSecret) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            clearSecret.run();
            return;
        }
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(normalized, context);
        saveEncrypted.accept(encrypted);
    }

    private void validateRegistrationCaptchaProviderRequirements(EffectiveAuthSecuritySettings effective) {
        if (!effective.registrationChallengeEnabled()) {
            return;
        }
        RegistrationChallengeService.RegistrationCaptchaProvider provider = RegistrationChallengeService.RegistrationCaptchaProvider
                .valueOf(effective.registrationChallengeProvider().trim().toUpperCase(Locale.ROOT));
        switch (provider) {
            case ALTCHA -> {
                return;
            }
            case TURNSTILE -> {
                if (!isConfiguredValue(effective.registrationTurnstileSiteKey()) || !isConfiguredValue(effective.registrationTurnstileSecret())) {
                    throw new IllegalArgumentException("TURNSTILE requires both a site key and secret before it can be enabled");
                }
            }
            case HCAPTCHA -> {
                if (!isConfiguredValue(effective.registrationHcaptchaSiteKey()) || !isConfiguredValue(effective.registrationHcaptchaSecret())) {
                    throw new IllegalArgumentException("HCAPTCHA requires both a site key and secret before it can be enabled");
                }
            }
        }
    }

    private void validateGeoIpProviderRequirements(EffectiveAuthSecuritySettings effective) {
        if (!effective.geoIpEnabled()) {
            return;
        }
        validateGeoIpProviderConfigured(effective.geoIpPrimaryProvider(), effective.geoIpIpinfoToken());
        if (effective.geoIpFallbackProviders() != null && !effective.geoIpFallbackProviders().isBlank()) {
            for (String provider : effective.geoIpFallbackProviders().split(",")) {
                validateGeoIpProviderConfigured(provider.trim(), effective.geoIpIpinfoToken());
            }
        }
    }

    private void validateGeoIpProviderConfigured(String rawProvider, String ipinfoToken) {
        if (rawProvider == null || rawProvider.isBlank()) {
            return;
        }
        GeoIpLocationService.GeoIpProvider provider = GeoIpLocationService.GeoIpProvider.valueOf(rawProvider.trim().toUpperCase(Locale.ROOT));
        if (provider == GeoIpLocationService.GeoIpProvider.IPINFO_LITE
                && (ipinfoToken == null || ipinfoToken.trim().isBlank() || "replace-me".equalsIgnoreCase(ipinfoToken.trim()))) {
            throw new IllegalArgumentException("IPINFO_LITE requires an IPinfo token before it can be enabled");
        }
    }

    private String availableRegistrationCaptchaProviders() {
        return Arrays.stream(RegistrationChallengeService.RegistrationCaptchaProvider.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String availableGeoIpProviders() {
        return Arrays.stream(GeoIpLocationService.GeoIpProvider.values())
                .filter(provider -> provider != GeoIpLocationService.GeoIpProvider.NONE)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String defaultTurnstileSiteKey() {
        return normalizeConfiguredOptional(inboxBridgeConfig.security().auth().registrationCaptcha().turnstile().siteKey().orElse(""));
    }

    private String defaultTurnstileSecret() {
        return normalizeConfiguredOptional(inboxBridgeConfig.security().auth().registrationCaptcha().turnstile().secret().orElse(""));
    }

    private String defaultHcaptchaSiteKey() {
        return normalizeConfiguredOptional(inboxBridgeConfig.security().auth().registrationCaptcha().hcaptcha().siteKey().orElse(""));
    }

    private String defaultHcaptchaSecret() {
        return normalizeConfiguredOptional(inboxBridgeConfig.security().auth().registrationCaptcha().hcaptcha().secret().orElse(""));
    }

    private String normalizeConfiguredOptional(String value) {
        return isConfiguredValue(value) ? value.trim() : "";
    }

    private String effectiveTurnstileSiteKey(SystemAuthSecuritySetting setting) {
        return isConfiguredValue(setting == null ? null : setting.registrationTurnstileSiteKeyOverride)
                ? setting.registrationTurnstileSiteKeyOverride.trim()
                : defaultTurnstileSiteKey();
    }

    private String effectiveTurnstileSecret(SystemAuthSecuritySetting setting) {
        String stored = decrypt(
                setting == null ? null : setting.registrationTurnstileSecretCiphertext,
                setting == null ? null : setting.registrationTurnstileSecretNonce,
                setting == null ? null : setting.keyVersion,
                "system-auth-security:registration-turnstile-secret");
        return isConfiguredValue(stored) ? stored : defaultTurnstileSecret();
    }

    private String effectiveHcaptchaSiteKey(SystemAuthSecuritySetting setting) {
        return isConfiguredValue(setting == null ? null : setting.registrationHcaptchaSiteKeyOverride)
                ? setting.registrationHcaptchaSiteKeyOverride.trim()
                : defaultHcaptchaSiteKey();
    }

    private String effectiveHcaptchaSecret(SystemAuthSecuritySetting setting) {
        String stored = decrypt(
                setting == null ? null : setting.registrationHcaptchaSecretCiphertext,
                setting == null ? null : setting.registrationHcaptchaSecretNonce,
                setting == null ? null : setting.keyVersion,
                "system-auth-security:registration-hcaptcha-secret");
        return isConfiguredValue(stored) ? stored : defaultHcaptchaSecret();
    }

    private boolean registrationTurnstileConfigured(SystemAuthSecuritySetting setting) {
        return isConfiguredValue(effectiveTurnstileSiteKey(setting)) && isConfiguredValue(effectiveTurnstileSecret(setting));
    }

    private boolean registrationHcaptchaConfigured(SystemAuthSecuritySetting setting) {
        return isConfiguredValue(effectiveHcaptchaSiteKey(setting)) && isConfiguredValue(effectiveHcaptchaSecret(setting));
    }

    private String effectiveGeoIpIpinfoToken(SystemAuthSecuritySetting setting) {
        String stored = decrypt(
                setting == null ? null : setting.geoIpIpinfoTokenCiphertext,
                setting == null ? null : setting.geoIpIpinfoTokenNonce,
                setting == null ? null : setting.keyVersion,
                "system-auth-security:geo-ip-ipinfo-token");
        return isConfiguredValue(stored) ? stored : inboxBridgeConfig.security().auth().geoIp().ipinfoToken().orElse("");
    }

    private boolean geoIpIpinfoTokenConfigured(SystemAuthSecuritySetting setting) {
        return isConfiguredValue(effectiveGeoIpIpinfoToken(setting));
    }

    private String decrypt(String ciphertext, String nonce, String keyVersion, String context) {
        if (ciphertext == null || nonce == null || keyVersion == null || !secretEncryptionService.isConfigured()) {
            return "";
        }
        return secretEncryptionService.decrypt(ciphertext, nonce, keyVersion, context);
    }

    private boolean isConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"replace-me".equalsIgnoreCase(value.trim());
    }

    private boolean isProvided(String value) {
        return value != null;
    }

    private String currentKeyVersion(SystemAuthSecuritySetting setting) {
        if (secretEncryptionService.isConfigured()) {
            return secretEncryptionService.keyVersion();
        }
        return setting == null || setting.keyVersion == null || setting.keyVersion.isBlank()
                ? "plain"
                : setting.keyVersion;
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
            Duration registrationChallengeTtl,
            String registrationChallengeProvider,
            String registrationTurnstileSiteKey,
            String registrationTurnstileSecret,
            String registrationHcaptchaSiteKey,
            String registrationHcaptchaSecret,
            boolean geoIpEnabled,
            String geoIpPrimaryProvider,
            String geoIpFallbackProviders,
            Duration geoIpCacheTtl,
            Duration geoIpProviderCooldown,
            Duration geoIpRequestTimeout,
            String geoIpIpinfoToken) {
    }
}
