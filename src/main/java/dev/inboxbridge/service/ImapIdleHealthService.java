package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImapIdleHealthService {

    static final Duration SCHEDULER_FALLBACK_THRESHOLD = Duration.ofMinutes(2);

    private final Map<String, Map<String, IdleHealthState>> states = new ConcurrentHashMap<>();

    public void ensureTracked(String sourceId, Instant now) {
        ensureTracked(sourceId, sourceId, now);
    }

    public void ensureTracked(String sourceId, String watchKey, Instant now) {
        if (sourceId == null) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        states.computeIfAbsent(sourceId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(watchKey == null ? sourceId : watchKey, ignored -> new IdleHealthState(null, effectiveNow));
    }

    public void markConnected(String sourceId, Instant now) {
        markConnected(sourceId, sourceId, now);
    }

    public void markConnected(String sourceId, String watchKey, Instant now) {
        if (sourceId == null) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        states.computeIfAbsent(sourceId, ignored -> new ConcurrentHashMap<>())
                .put(watchKey == null ? sourceId : watchKey, new IdleHealthState(effectiveNow, null));
    }

    public void markDisconnected(String sourceId, Instant now) {
        markDisconnected(sourceId, sourceId, now);
    }

    public void markDisconnected(String sourceId, String watchKey, Instant now) {
        if (sourceId == null) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        states.computeIfAbsent(sourceId, ignored -> new ConcurrentHashMap<>())
                .compute(watchKey == null ? sourceId : watchKey, (ignored, existing) -> {
            if (existing == null) {
                return new IdleHealthState(null, effectiveNow);
            }
            if (existing.disconnectedSince() != null) {
                return existing;
            }
            return new IdleHealthState(existing.lastConnectedAt(), effectiveNow);
        });
    }

    public void clear(String sourceId) {
        if (sourceId == null) {
            return;
        }
        states.remove(sourceId);
    }

    public void clear(String sourceId, String watchKey) {
        if (sourceId == null) {
            return;
        }
        Map<String, IdleHealthState> perWatch = states.get(sourceId);
        if (perWatch == null) {
            return;
        }
        perWatch.remove(watchKey == null ? sourceId : watchKey);
        if (perWatch.isEmpty()) {
            states.remove(sourceId);
        }
    }

    public boolean isHealthy(String sourceId) {
        List<IdleHealthState> sourceStates = statesFor(sourceId);
        return !sourceStates.isEmpty() && sourceStates.stream().allMatch(state -> state.disconnectedSince() == null);
    }

    public boolean shouldSchedulerFallback(String sourceId, Instant now) {
        List<IdleHealthState> sourceStates = statesFor(sourceId);
        if (sourceStates.isEmpty()) {
            return false;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        return sourceStates.stream()
                .map(IdleHealthState::disconnectedSince)
                .filter(disconnectedSince -> disconnectedSince != null)
                .min(Comparator.naturalOrder())
                .map(disconnectedSince -> !effectiveNow.isBefore(disconnectedSince.plus(SCHEDULER_FALLBACK_THRESHOLD)))
                .orElse(false);
    }

    private List<IdleHealthState> statesFor(String sourceId) {
        Map<String, IdleHealthState> perWatch = states.get(sourceId);
        if (perWatch == null || perWatch.isEmpty()) {
            return List.of();
        }
        return List.copyOf(perWatch.values());
    }

    record IdleHealthState(Instant lastConnectedAt, Instant disconnectedSince) {
    }
}
