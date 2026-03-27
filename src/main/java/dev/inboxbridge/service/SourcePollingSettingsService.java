package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Optional;

import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.SourcePollingSetting;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Resolves and stores source-specific polling overrides on top of the
 * inherited global or per-user polling settings.
 */
@ApplicationScoped
public class SourcePollingSettingsService {

    @Inject
    SourcePollingSettingRepository repository;

    @Inject
    RuntimeBridgeService runtimeBridgeService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    public Optional<SourcePollingSettingsView> viewForSource(AppUser actor, String sourceId) {
        RuntimeBridge bridge = runtimeBridgeService.findAccessibleForUser(actor, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return Optional.of(toView(bridge, repository.findBySourceId(sourceId).orElse(null)));
    }

    public Optional<SourcePollingSettingsView> viewForSystemSource(String sourceId) {
        RuntimeBridge bridge = runtimeBridgeService.findSystemBridge(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return Optional.of(toView(bridge, repository.findBySourceId(sourceId).orElse(null)));
    }

    public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeBridge bridge) {
        PollingSettingsService.EffectivePollingSettings base = baseSettingsFor(bridge);
        SourcePollingSetting setting = repository.findBySourceId(bridge.id()).orElse(null);
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
    public SourcePollingSettingsView updateForSource(AppUser actor, String sourceId, UpdateSourcePollingSettingsRequest request) {
        RuntimeBridge bridge = runtimeBridgeService.findAccessibleForUser(actor, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return updateForResolvedBridge(bridge, sourceId, request);
    }

    @Transactional
    public SourcePollingSettingsView updateForSystemSource(String sourceId, UpdateSourcePollingSettingsRequest request) {
        RuntimeBridge bridge = runtimeBridgeService.findSystemBridge(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return updateForResolvedBridge(bridge, sourceId, request);
    }

    private SourcePollingSettingsView updateForResolvedBridge(
            RuntimeBridge bridge,
            String sourceId,
            UpdateSourcePollingSettingsRequest request) {
        SourcePollingSetting setting = repository.findBySourceId(sourceId).orElseGet(SourcePollingSetting::new);
        if (setting.id == null) {
            setting.sourceId = sourceId;
            setting.ownerUserId = bridge.ownerUserId();
        }
        setting.pollEnabledOverride = request.pollEnabledOverride();
        setting.pollIntervalOverride = normalizeIntervalOverride(request.pollIntervalOverride());
        setting.fetchWindowOverride = normalizeFetchWindowOverride(request.fetchWindowOverride());
        setting.updatedAt = Instant.now();
        repository.persist(setting);
        return toView(bridge, setting);
    }

    private PollingSettingsService.EffectivePollingSettings baseSettingsFor(RuntimeBridge bridge) {
        if (bridge.ownerUserId() != null) {
            return userPollingSettingsService.effectiveSettingsForUser(bridge.ownerUserId());
        }
        return pollingSettingsService.effectiveSettings();
    }

    private SourcePollingSettingsView toView(RuntimeBridge bridge, SourcePollingSetting setting) {
        PollingSettingsService.EffectivePollingSettings base = baseSettingsFor(bridge);
        PollingSettingsService.EffectivePollingSettings effective = effectiveSettingsFor(bridge);
        return new SourcePollingSettingsView(
                bridge.id(),
                base.pollEnabled(),
                setting == null ? null : setting.pollEnabledOverride,
                effective.pollEnabled(),
                base.pollIntervalText(),
                setting == null ? null : setting.pollIntervalOverride,
                effective.pollIntervalText(),
                base.fetchWindow(),
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
