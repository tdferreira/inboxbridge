package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private static final Duration DEFAULT_FAILURE_BACKOFF = Duration.ofMinutes(2);
    private static final Duration TRANSIENT_FAILURE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMinutes(15);
    private static final Duration AUTH_FAILURE_BACKOFF = Duration.ofMinutes(30);
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);

    @Inject
    SourcePollingStateRepository repository;

    public Optional<SourcePollingStateView> viewForSource(String sourceId) {
        return repository.findBySourceId(sourceId).map(this::toView);
    }

    public Map<String, SourcePollingStateView> viewBySourceIds(List<String> sourceIds) {
        return repository.findBySourceIds(sourceIds).values().stream()
                .collect(Collectors.toMap(state -> state.sourceId, this::toView));
    }

    public PollEligibility eligibility(
            String sourceId,
            PollingSettingsService.EffectivePollingSettings settings,
            Instant now,
            boolean ignoreInterval) {
        if (!settings.pollEnabled()) {
            return new PollEligibility(false, "DISABLED", viewForSource(sourceId).orElse(null));
        }
        SourcePollingState state = repository.findBySourceId(sourceId).orElse(null);
        if (state != null && state.cooldownUntil != null && now.isBefore(state.cooldownUntil)) {
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
        state.nextPollAt = finishedAt.plus(settings.pollInterval());
        state.cooldownUntil = null;
        state.consecutiveFailures = 0;
        state.lastFailureReason = null;
        state.lastFailureAt = null;
        state.lastSuccessAt = finishedAt;
        state.updatedAt = finishedAt;
        repository.persist(state);
    }

    @Transactional
    public void recordFailure(String sourceId, Instant finishedAt, String errorMessage) {
        SourcePollingState state = repository.findBySourceId(sourceId).orElseGet(SourcePollingState::new);
        if (state.id == null) {
            state.sourceId = sourceId;
        }
        int failures = state.consecutiveFailures + 1;
        Duration backoff = backoffFor(errorMessage, failures);
        state.consecutiveFailures = failures;
        state.lastFailureReason = truncate(errorMessage);
        state.lastFailureAt = finishedAt;
        state.cooldownUntil = finishedAt.plus(backoff);
        state.nextPollAt = state.cooldownUntil;
        state.updatedAt = finishedAt;
        repository.persist(state);
    }

    private Duration backoffFor(String errorMessage, int consecutiveFailures) {
        String normalized = errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
        Duration base = DEFAULT_FAILURE_BACKOFF;
        if (containsAny(normalized, "429", "too many", "rate limit", "throttl", "quota", "temporarily blocked", "try again later", "lockout")) {
            base = RATE_LIMIT_BACKOFF;
        } else if (containsAny(normalized, "authenticate failed", "authenticationfailed", "basicauthblocked", "invalid_grant", "consent_required", "logondenied", "authent")) {
            base = AUTH_FAILURE_BACKOFF;
        } else if (containsAny(normalized, "timeout", "timed out", "connection refused", "connection reset", "service unavailable", "temporarily unavailable", "i/o", "ioexception")) {
            base = TRANSIENT_FAILURE_BACKOFF;
        }

        Duration result = base;
        int multiplierSteps = Math.max(0, Math.min(4, consecutiveFailures - 1));
        for (int i = 0; i < multiplierSteps; i++) {
            result = result.multipliedBy(2);
            if (result.compareTo(MAX_BACKOFF) >= 0) {
                return MAX_BACKOFF;
            }
        }
        return result.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : result;
    }

    private boolean containsAny(String normalized, String... patterns) {
        for (String pattern : patterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
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
}
