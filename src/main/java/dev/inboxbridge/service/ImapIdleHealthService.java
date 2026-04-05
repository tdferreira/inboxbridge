package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImapIdleHealthService {

    static final Duration SCHEDULER_FALLBACK_THRESHOLD = Duration.ofMinutes(2);

    private final Map<String, IdleHealthState> states = new ConcurrentHashMap<>();

    public void ensureTracked(String sourceId, Instant now) {
        if (sourceId == null) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        states.computeIfAbsent(sourceId, ignored -> new IdleHealthState(null, effectiveNow));
    }

    public void markConnected(String sourceId, Instant now) {
        if (sourceId == null) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        states.put(sourceId, new IdleHealthState(effectiveNow, null));
    }

    public void markDisconnected(String sourceId, Instant now) {
        if (sourceId == null) {
            return;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        states.compute(sourceId, (ignored, existing) -> {
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

    public boolean isHealthy(String sourceId) {
        IdleHealthState state = states.get(sourceId);
        return state != null && state.disconnectedSince() == null;
    }

    public boolean shouldSchedulerFallback(String sourceId, Instant now) {
        IdleHealthState state = states.get(sourceId);
        if (state == null || state.disconnectedSince() == null) {
            return false;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        return !effectiveNow.isBefore(state.disconnectedSince().plus(SCHEDULER_FALLBACK_THRESHOLD));
    }

    record IdleHealthState(Instant lastConnectedAt, Instant disconnectedSince) {
    }
}
