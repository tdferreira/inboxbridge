package dev.inboxbridge.service.polling;

import java.time.Instant;
import java.util.Optional;

import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.SourcePollingSetting;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.user.UserPollingSettingsService;
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
    jakarta.enterprise.event.Event<dev.inboxbridge.service.mail.SourceMailboxConfigurationChanged> mailboxChanged;

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    public Optional<SourcePollingSettingsView> viewForSource(AppUser actor, String sourceId) {
        RuntimeEmailAccount emailAccount = runtimeEmailAccountService.findAccessibleForUser(actor, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return Optional.of(toView(emailAccount, repository.findBySourceId(sourceId).orElse(null)));
    }

    public Optional<SourcePollingSettingsView> viewForSystemSource(String sourceId) {
        RuntimeEmailAccount emailAccount = runtimeEmailAccountService.findSystemBridge(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return Optional.of(toView(emailAccount, repository.findBySourceId(sourceId).orElse(null)));
    }

    @Transactional
    public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount emailAccount) {
        PollingSettingsService.EffectivePollingSettings base = baseSettingsFor(emailAccount);
        SourcePollingSetting setting = repository.findBySourceId(emailAccount.id()).orElse(null);
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
        RuntimeEmailAccount emailAccount = runtimeEmailAccountService.findAccessibleForUser(actor, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return updateForResolvedEmailAccount(emailAccount, sourceId, request);
    }

    @Transactional
    public SourcePollingSettingsView updateForSystemSource(String sourceId, UpdateSourcePollingSettingsRequest request) {
        RuntimeEmailAccount emailAccount = runtimeEmailAccountService.findSystemBridge(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        return updateForResolvedEmailAccount(emailAccount, sourceId, request);
    }

    private SourcePollingSettingsView updateForResolvedEmailAccount(
            RuntimeEmailAccount emailAccount,
            String sourceId,
            UpdateSourcePollingSettingsRequest request) {
        SourcePollingSetting setting = repository.findBySourceId(sourceId).orElseGet(SourcePollingSetting::new);
        if (setting.id == null) {
            setting.sourceId = sourceId;
            setting.ownerUserId = emailAccount.ownerUserId();
        }
        String safety = request.idleSafetyPollIntervalOverride();
        safety = safety == null || safety.isBlank() ? null
                : pollingSettingsService.parsePositiveDuration(safety, "IDLE safety interval").toString();
        var idle = pollingSettingsService.effectiveIdleSettings();
        String renewal = request.idleRenewalIntervalOverride();
        renewal = renewal == null || renewal.isBlank() ? null
                : pollingSettingsService.parsePositiveDuration(renewal, "IDLE renewal interval").toString();
        java.time.Duration effectiveRenewal = renewal == null ? idle.renewalInterval() : java.time.Duration.parse(renewal);
        PollingSettingsService.validateIdleRenewal(effectiveRenewal, idle.renewalTimeout());
        PollingSettingsService.validateIdleSafetyInterval(safety == null ? pollingSettingsService.effectiveIdleSafetyPollInterval() : java.time.Duration.parse(safety), effectiveRenewal, idle.renewalTimeout());
        setting.idleRenewalIntervalOverride = renewal;
        setting.idleSafetyPollIntervalOverride = safety;
        setting.idleSafetyPollingEnabledOverride = request.idleSafetyPollingEnabledOverride();
        setting.pollEnabledOverride = request.pollEnabledOverride();
        setting.pollIntervalOverride = normalizeIntervalOverride(request.pollIntervalOverride());
        setting.fetchWindowOverride = normalizeFetchWindowOverride(request.fetchWindowOverride());
        setting.updatedAt = Instant.now();
        repository.persist(setting);
        if (mailboxChanged != null) mailboxChanged.fire(new dev.inboxbridge.service.mail.SourceMailboxConfigurationChanged(sourceId));
        return toView(emailAccount, setting);
    }

    private PollingSettingsService.EffectivePollingSettings baseSettingsFor(RuntimeEmailAccount emailAccount) {
        if (emailAccount.ownerUserId() != null) {
            return userPollingSettingsService.effectiveSettingsForUser(emailAccount.ownerUserId());
        }
        return pollingSettingsService.effectiveSettings();
    }

    private SourcePollingSettingsView toView(RuntimeEmailAccount emailAccount, SourcePollingSetting setting) {
        PollingSettingsService.EffectivePollingSettings base = baseSettingsFor(emailAccount);
        PollingSettingsService.EffectivePollingSettings effective = effectiveSettingsFor(emailAccount);
        return new SourcePollingSettingsView(
                emailAccount.id(),
                base.pollEnabled(),
                setting == null ? null : setting.pollEnabledOverride,
                effective.pollEnabled(),
                base.pollIntervalText(),
                setting == null ? null : setting.pollIntervalOverride,
                effective.pollIntervalText(),
                base.fetchWindow(),
                setting == null ? null : setting.fetchWindowOverride,
                effective.fetchWindow(),
                pollingSettingsService.effectiveIdleSettings().safetyPollingEnabled(),
                setting == null ? null : setting.idleSafetyPollingEnabledOverride,
                effectiveIdleSafetyPollingEnabled(emailAccount),
                pollingSettingsService.effectiveIdleSafetyPollInterval().toString(),
                setting == null ? null : setting.idleSafetyPollIntervalOverride,
                effectiveIdleSafetyPollInterval(emailAccount).toString(),
                pollingSettingsService.effectiveIdleSettings().renewalInterval().toString(),
                setting == null ? null : setting.idleRenewalIntervalOverride,
                effectiveIdleRenewalInterval(emailAccount).toString());
    }

    @Transactional
    public boolean effectiveIdleSafetyPollingEnabled(RuntimeEmailAccount account) {
        SourcePollingSetting setting = repository == null ? null : repository.findBySourceId(account.id()).orElse(null);
        if (setting != null && setting.idleSafetyPollingEnabledOverride != null) {
            return setting.idleSafetyPollingEnabledOverride;
        }
        return pollingSettingsService == null || pollingSettingsService.effectiveIdleSettings().safetyPollingEnabled();
    }

    @Transactional
    public java.time.Duration effectiveIdleSafetyPollInterval(RuntimeEmailAccount account) {
        SourcePollingSetting setting = repository == null ? null : repository.findBySourceId(account.id()).orElse(null);
        return setting != null && setting.idleSafetyPollIntervalOverride != null
                ? java.time.Duration.parse(setting.idleSafetyPollIntervalOverride)
                : pollingSettingsService == null ? java.time.Duration.ofMinutes(30) : pollingSettingsService.effectiveIdleSafetyPollInterval();
    }

    @Transactional
    public java.time.Duration effectiveIdleRenewalInterval(RuntimeEmailAccount account) {
        SourcePollingSetting setting = repository == null ? null : repository.findBySourceId(account.id()).orElse(null);
        return setting != null && setting.idleRenewalIntervalOverride != null
                ? java.time.Duration.parse(setting.idleRenewalIntervalOverride)
                : pollingSettingsService == null ? java.time.Duration.ofMinutes(25)
                    : pollingSettingsService.effectiveIdleSettings().renewalInterval();
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
