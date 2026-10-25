package dev.inboxbridge.service.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ImapIdleHealthService {

    public static final Duration SCHEDULER_FALLBACK_THRESHOLD = Duration.ofMinutes(2);

    @Inject
    @ConfigProperty(name = "inboxbridge.mail.idle-safety-polling-enabled", defaultValue = "true")
    boolean safetyPollingEnabled = true;

    @Inject
    dev.inboxbridge.service.polling.SourcePollingSettingsService sourcePollingSettingsService;

    public boolean isSafetyPollingEnabled(dev.inboxbridge.domain.RuntimeEmailAccount account) {
        return sourcePollingSettingsService == null ? isSafetyPollingEnabled()
                : sourcePollingSettingsService.effectiveIdleSafetyPollingEnabled(account);
    }

    public Duration safetyPollInterval(dev.inboxbridge.domain.RuntimeEmailAccount account) {
        return sourcePollingSettingsService == null ? Duration.ofMinutes(30)
                : sourcePollingSettingsService.effectiveIdleSafetyPollInterval(account);
    }

    /** The oldest folder acknowledgement protects against one live folder hiding a stalled sibling. */
    public Instant nextSafetyPollAt(dev.inboxbridge.domain.RuntimeEmailAccount account, Instant lastSuccessfulPoll) {
        if (!isSafetyPollingEnabled(account)) return null;
        Instant baseline = statesFor(account.id()).stream()
                .map(state -> state.lastRenewedAt() != null ? state.lastRenewedAt()
                        : state.lastConnectedAt() != null ? state.lastConnectedAt() : state.disconnectedSince())
                .filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        if (lastSuccessfulPoll != null && (baseline == null || lastSuccessfulPoll.isAfter(baseline))) baseline = lastSuccessfulPoll;
        return baseline == null ? null : baseline.plus(safetyPollInterval(account));
    }

    public boolean isSafetyPollDue(dev.inboxbridge.domain.RuntimeEmailAccount account, Instant now, Instant lastSuccessfulPoll) {
        Instant deadline = nextSafetyPollAt(account, lastSuccessfulPoll);
        return deadline != null && !now.isBefore(deadline);
    }

    public boolean isSafetyPollingEnabled() {
        return safetyPollingEnabled;
    }

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
                .computeIfAbsent(watchKey == null ? sourceId : watchKey, ignored -> new IdleHealthState(null, effectiveNow, null));
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
                .put(watchKey == null ? sourceId : watchKey, new IdleHealthState(effectiveNow, null, null));
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
                return new IdleHealthState(null, effectiveNow, null);
            }
            if (existing.disconnectedSince() != null) {
                return existing;
            }
            return new IdleHealthState(existing.lastConnectedAt(), effectiveNow, existing.lastRenewedAt());
        });
    }

    /** Only an acknowledged renewal on a connected watcher establishes liveness. */
    public void markRenewed(String sourceId, String watchKey, Instant now) {
        Map<String, IdleHealthState> watches = states.get(sourceId);
        if (watches != null) {
            watches.computeIfPresent(watchKey, (ignored, state) -> state.disconnectedSince() == null
                    ? new IdleHealthState(state.lastConnectedAt(), null, now) : state);
        }
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

    public List<WatchHealth> watchStates(String sourceId) {
        Map<String, IdleHealthState> perWatch = states.get(sourceId);
        if (perWatch == null || perWatch.isEmpty()) {
            return List.of();
        }
        return perWatch.entrySet().stream()
                .map(entry -> new WatchHealth(
                        entry.getKey(),
                        entry.getValue().lastConnectedAt(),
                        entry.getValue().disconnectedSince(), entry.getValue().lastRenewedAt()))
                .sorted(Comparator.comparing(WatchHealth::watchKey))
                .toList();
    }

    private List<IdleHealthState> statesFor(String sourceId) {
        Map<String, IdleHealthState> perWatch = states.get(sourceId);
        if (perWatch == null || perWatch.isEmpty()) {
            return List.of();
        }
        return List.copyOf(perWatch.values());
    }

    record IdleHealthState(Instant lastConnectedAt, Instant disconnectedSince, Instant lastRenewedAt) {
    }

    public record WatchHealth(String watchKey, Instant lastConnectedAt, Instant disconnectedSince, Instant lastRenewedAt) {
    }
}
