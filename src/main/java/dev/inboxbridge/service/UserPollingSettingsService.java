package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Optional;

import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.dto.UserPollingSettingsView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserPollingSetting;
import dev.inboxbridge.persistence.UserPollingSettingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Resolves per-user polling overrides on top of the deployment/global poller
 * settings so each user can tune how often their own fetchers run.
 */
@ApplicationScoped
public class UserPollingSettingsService {

    @Inject
    UserPollingSettingRepository repository;

    @Inject
    PollingSettingsService pollingSettingsService;

    public Optional<UserPollingSettingsView> viewForUser(Long userId) {
        return repository.findByUserId(userId).map(setting -> toView(setting, effectiveSettingsForUser(userId)));
    }

    public UserPollingSettingsView defaultView(Long userId) {
        return toView(null, effectiveSettingsForUser(userId));
    }

    public PollingSettingsService.EffectivePollingSettings effectiveSettingsForUser(Long userId) {
        PollingSettingsService.EffectivePollingSettings base = pollingSettingsService.effectiveSettings();
        UserPollingSetting setting = repository.findByUserId(userId).orElse(null);
        boolean pollEnabled = setting != null && setting.pollEnabledOverride != null
                ? setting.pollEnabledOverride
                : base.pollEnabled();
        String pollIntervalText = setting != null && setting.pollIntervalOverride != null && !setting.pollIntervalOverride.isBlank()
                ? setting.pollIntervalOverride
                : base.pollIntervalText();
        int fetchWindow = setting != null && setting.fetchWindowOverride != null
                ? setting.fetchWindowOverride
                : base.fetchWindow();
        return new PollingSettingsService.EffectivePollingSettings(
                pollEnabled,
                pollIntervalText,
                pollingSettingsService.parseDuration(pollIntervalText),
                fetchWindow);
    }

    @Transactional
    public UserPollingSettingsView update(AppUser user, UpdateUserPollingSettingsRequest request) {
        UserPollingSetting setting = repository.findByUserId(user.id).orElseGet(UserPollingSetting::new);
        if (setting.id == null) {
            setting.userId = user.id;
        }
        setting.pollEnabledOverride = request.pollEnabledOverride();
        setting.pollIntervalOverride = normalizeIntervalOverride(request.pollIntervalOverride());
        setting.fetchWindowOverride = normalizeFetchWindowOverride(request.fetchWindowOverride());
        setting.updatedAt = Instant.now();
        repository.persist(setting);
        return toView(setting, effectiveSettingsForUser(user.id));
    }

    private UserPollingSettingsView toView(UserPollingSetting setting, PollingSettingsService.EffectivePollingSettings effective) {
        return new UserPollingSettingsView(
                pollingSettingsService.effectiveSettings().pollEnabled(),
                setting == null ? null : setting.pollEnabledOverride,
                effective.pollEnabled(),
                pollingSettingsService.effectiveSettings().pollIntervalText(),
                setting == null ? null : setting.pollIntervalOverride,
                effective.pollIntervalText(),
                pollingSettingsService.effectiveSettings().fetchWindow(),
                setting == null ? null : setting.fetchWindowOverride,
                effective.fetchWindow());
    }

    private String normalizeIntervalOverride(String override) {
        if (override == null || override.isBlank()) {
            return null;
        }
        String normalized = override.trim();
        pollingSettingsService.parseDuration(normalized);
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
}
