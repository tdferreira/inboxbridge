package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.inboxbridge.domain.ImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpointRepository;
import dev.inboxbridge.dto.SourcePollingStateView;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Tracks when each source should poll next and applies cooldown/backoff after
 * repeated provider failures so InboxBridge does not hammer blocked accounts.
 */
@ApplicationScoped
public class SourcePollingStateService {

    private static final Duration MAX_BACKOFF = Duration.ofHours(6);

    @Inject
    SourcePollingStateRepository repository;

    @Inject
    SourceImapCheckpointRepository imapCheckpointRepository;

    @Inject
    PollingSettingsService pollingSettingsService;

    public Optional<SourcePollingStateView> viewForSource(String sourceId) {
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findBySourceId(sourceId).map(this::toView);
    }

    public Map<String, SourcePollingStateView> viewBySourceIds(List<String> sourceIds) {
        if (repository == null) {
            return Map.of();
        }
        return repository.findBySourceIds(sourceIds).values().stream()
                .collect(Collectors.toMap(state -> state.sourceId, this::toView));
    }

    @Transactional
    public Optional<ImapCheckpoint> imapCheckpoint(String sourceId, String destinationKey, String folderName) {
        if (repository == null) {
            return Optional.empty();
        }
        if (imapCheckpointRepository != null) {
            Optional<ImapCheckpoint> persisted = imapCheckpointRepository.findByScope(sourceId, destinationKey, folderName)
                    .map(state -> new ImapCheckpoint(state.folderName, state.uidValidity, state.lastSeenUid));
            if (persisted.isPresent()) {
                return persisted;
            }
        }
        return repository.findBySourceId(sourceId)
                .filter(state -> state.imapFolderName != null && state.imapUidValidity != null && state.imapLastSeenUid != null)
                .filter(state -> checkpointMatchesDestination(state.imapCheckpointDestinationKey, destinationKey))
                .filter(state -> state.imapFolderName.equalsIgnoreCase(folderName))
                .map(state -> new ImapCheckpoint(state.imapFolderName, state.imapUidValidity, state.imapLastSeenUid));
    }

    @Transactional
    public Optional<String> popCheckpoint(String sourceId, String destinationKey) {
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findBySourceId(sourceId)
                .filter(state -> checkpointMatchesDestination(state.popCheckpointDestinationKey, destinationKey))
                .map(state -> state.popLastSeenUidl)
                .filter(uidl -> uidl != null && !uidl.isBlank());
    }

    public PollEligibility eligibility(
            String sourceId,
            PollingSettingsService.EffectivePollingSettings settings,
            Instant now,
            boolean ignoreInterval) {
        return eligibility(sourceId, settings, now, ignoreInterval, false);
    }

    @Transactional
    public PollEligibility eligibility(
            String sourceId,
            PollingSettingsService.EffectivePollingSettings settings,
            Instant now,
            boolean ignoreInterval,
            boolean ignoreCooldown) {
        if (!settings.pollEnabled()) {
            return new PollEligibility(false, "DISABLED", viewForSource(sourceId).orElse(null));
        }
        SourcePollingState state = repository.findBySourceId(sourceId).orElse(null);
        if (!ignoreCooldown && state != null && state.cooldownUntil != null && now.isBefore(state.cooldownUntil)) {
            return new PollEligibility(false, "COOLDOWN", toView(state));
        }
        if (!ignoreInterval && state != null && state.nextPollAt != null && now.isBefore(state.nextPollAt)) {
            return new PollEligibility(false, "INTERVAL", toView(state));
        }
        return new PollEligibility(true, "READY", state == null ? null : toView(state));
    }

    @Transactional
    public void recordSuccess(String sourceId, Instant finishedAt, PollingSettingsService.EffectivePollingSettings settings) {
        SourcePollingState state = repository.findBySourceId(sourceId).orElseGet(SourcePollingState::new);
        if (state.id == null) {
            state.sourceId = sourceId;
        }
        state.nextPollAt = nextAlignedPollAt(finishedAt, settings.pollInterval());
        state.cooldownUntil = null;
        state.consecutiveFailures = 0;
        state.lastFailureReason = null;
        state.lastFailureAt = null;
        state.lastSuccessAt = finishedAt;
        state.updatedAt = finishedAt;
        repository.persist(state);
    }

    Instant nextAlignedPollAt(Instant finishedAt, Duration pollInterval) {
        if (finishedAt == null || pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            return finishedAt;
        }
        long intervalMillis = Math.max(1L, pollInterval.toMillis());
        long finishedAtMillis = finishedAt.toEpochMilli();
        long nextBoundary = Math.multiplyExact(Math.floorDiv(finishedAtMillis, intervalMillis) + 1L, intervalMillis);
        return Instant.ofEpochMilli(nextBoundary);
    }

    Duration successJitterFor(String sourceId, Duration pollInterval) {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            return Duration.ZERO;
        }
        PollingSettingsService.EffectiveThrottleSettings throttleSettings = pollingSettingsService.effectiveThrottleSettings();
        double configuredRatio = Math.max(0d, throttleSettings.successJitterRatio());
        if (configuredRatio <= 0d) {
            return Duration.ZERO;
        }
        Duration configuredCap = throttleSettings.maxSuccessJitter();
        if (configuredCap == null || configuredCap.isNegative()) {
            configuredCap = Duration.ZERO;
        }
        long intervalMillis = pollInterval.toMillis();
        long ratioMillis = (long) Math.floor(intervalMillis * configuredRatio);
        long maxJitterMillis = configuredCap.isZero() ? ratioMillis : Math.min(configuredCap.toMillis(), ratioMillis);
        if (maxJitterMillis <= 0L) {
            return Duration.ZERO;
        }
        int hash = Math.abs((sourceId == null ? "" : sourceId).hashCode());
        long jitterMillis = hash % (maxJitterMillis + 1L);
        return Duration.ofMillis(jitterMillis);
    }

    @Transactional
    /**
     * Persists the current source failure and returns the applied cooldown
     * decision so higher-level poll history can record why the source is cooling
     * down and for how long.
     */
    public CooldownDecision recordFailure(String sourceId, Instant finishedAt, String errorMessage) {
        SourcePollingState state = repository.findBySourceId(sourceId).orElseGet(SourcePollingState::new);
        if (state.id == null) {
            state.sourceId = sourceId;
        }
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify(errorMessage);
        int failures = state.consecutiveFailures + 1;
        Duration backoff = backoffFor(classification, failures);
        state.consecutiveFailures = failures;
        state.lastFailureReason = truncate(errorMessage);
        state.lastFailureAt = finishedAt;
        state.cooldownUntil = finishedAt.plus(backoff);
        state.nextPollAt = state.cooldownUntil;
        state.updatedAt = finishedAt;
        repository.persist(state);
        return new CooldownDecision(
                classification.category().name(),
                failures,
                backoff,
                state.cooldownUntil);
    }

    @Transactional
    public void recordImapCheckpoint(
            String sourceId,
            String destinationKey,
            String folderName,
            Long uidValidity,
            Long lastSeenUid,
            Instant observedAt) {
        if (repository == null || sourceId == null || destinationKey == null || destinationKey.isBlank()
                || folderName == null || uidValidity == null || lastSeenUid == null || lastSeenUid <= 0L) {
            return;
        }
        if (imapCheckpointRepository != null) {
            SourceImapCheckpoint checkpoint = imapCheckpointRepository.findByScope(sourceId, destinationKey, folderName)
                    .orElseGet(SourceImapCheckpoint::new);
            if (checkpoint.id == null) {
                checkpoint.sourceId = sourceId;
                checkpoint.destinationKey = destinationKey;
                checkpoint.folderName = folderName;
            }
            boolean sameUidValidity = checkpoint.uidValidity != null && checkpoint.uidValidity.equals(uidValidity);
            if (!sameUidValidity || checkpoint.lastSeenUid == null || lastSeenUid > checkpoint.lastSeenUid) {
                checkpoint.uidValidity = uidValidity;
                checkpoint.lastSeenUid = lastSeenUid;
            }
            checkpoint.updatedAt = observedAt == null ? Instant.now() : observedAt;
            imapCheckpointRepository.persist(checkpoint);
        }

        SourcePollingState state = repository.findBySourceId(sourceId).orElseGet(SourcePollingState::new);
        if (state.id == null) {
            state.sourceId = sourceId;
        }
        boolean sameDestination = checkpointMatchesDestination(state.imapCheckpointDestinationKey, destinationKey);
        boolean sameFolder = state.imapFolderName != null && state.imapFolderName.equalsIgnoreCase(folderName);
        boolean sameUidValidity = state.imapUidValidity != null && state.imapUidValidity.equals(uidValidity);
        if (!sameDestination || !sameFolder || !sameUidValidity || state.imapLastSeenUid == null) {
            state.imapCheckpointDestinationKey = destinationKey;
            state.imapFolderName = folderName;
            state.imapUidValidity = uidValidity;
            state.imapLastSeenUid = lastSeenUid;
        } else if (lastSeenUid > state.imapLastSeenUid) {
            state.imapLastSeenUid = lastSeenUid;
        }
        if (observedAt != null) {
            state.updatedAt = observedAt;
        }
        repository.persist(state);
    }

    @Transactional
    public void recordPopCheckpoint(String sourceId, String destinationKey, String uidl, Instant observedAt) {
        if (repository == null || sourceId == null || destinationKey == null || destinationKey.isBlank()
                || uidl == null || uidl.isBlank()) {
            return;
        }
        SourcePollingState state = repository.findBySourceId(sourceId).orElseGet(SourcePollingState::new);
        if (state.id == null) {
            state.sourceId = sourceId;
        }
        state.popCheckpointDestinationKey = destinationKey;
        state.popLastSeenUidl = uidl;
        if (observedAt != null) {
            state.updatedAt = observedAt;
        }
        repository.persist(state);
    }

    private Duration backoffFor(MailFailureClassifier.Classification classification, int consecutiveFailures) {
        Duration result = classification.baseBackoff();
        int multiplierSteps = Math.max(0, Math.min(4, consecutiveFailures - 1));
        for (int i = 0; i < multiplierSteps; i++) {
            result = result.multipliedBy(2);
            if (result.compareTo(MAX_BACKOFF) >= 0) {
                return MAX_BACKOFF;
            }
        }
        return result.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : result;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private boolean checkpointMatchesDestination(String storedDestinationKey, String requestedDestinationKey) {
        if (storedDestinationKey == null || storedDestinationKey.isBlank()) {
            return false;
        }
        return storedDestinationKey.equals(requestedDestinationKey);
    }

    private SourcePollingStateView toView(SourcePollingState state) {
        return new SourcePollingStateView(
                state.nextPollAt,
                state.cooldownUntil,
                state.consecutiveFailures,
                state.lastFailureReason,
                state.lastFailureAt,
                state.lastSuccessAt);
    }

    public record PollEligibility(
            boolean shouldPoll,
            String reason,
            SourcePollingStateView state) {
    }

    public record CooldownDecision(
            String failureCategory,
            int consecutiveFailures,
            Duration backoff,
            Instant cooldownUntil) {
    }
}
