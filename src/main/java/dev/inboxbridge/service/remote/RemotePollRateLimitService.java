package dev.inboxbridge.service.remote;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RemotePollRateLimitService {

    private final Map<String, Deque<Instant>> invocationsByActor = new ConcurrentHashMap<>();

    public Decision tryAcquire(String actorKey, int maxRuns, Duration window, Instant now) {
        if (actorKey == null || actorKey.isBlank() || maxRuns < 1 || window == null || window.isZero() || window.isNegative()) {
            return new Decision(true, null);
        }
        Deque<Instant> invocations = invocationsByActor.computeIfAbsent(actorKey, ignored -> new ArrayDeque<>());
        synchronized (invocations) {
            Instant threshold = now.minus(window);
            while (!invocations.isEmpty() && invocations.peekFirst().isBefore(threshold)) {
                invocations.removeFirst();
            }
            if (invocations.size() >= maxRuns) {
                Instant retryAt = invocations.peekFirst().plus(window);
                return new Decision(false, retryAt);
            }
            invocations.addLast(now);
            return new Decision(true, null);
        }
    }

    public record Decision(boolean allowed, Instant retryAt) {
    }
}
