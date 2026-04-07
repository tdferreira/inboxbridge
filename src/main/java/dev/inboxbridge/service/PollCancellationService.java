package dev.inboxbridge.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PollCancellationService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ScopeState>> scopesByRunId = new ConcurrentHashMap<>();
    private final ThreadLocal<ScopeState> currentScope = new ThreadLocal<>();

    public Scope bind(String runId, String sourceId) {
        if (runId == null || sourceId == null) {
            return Scope.noop();
        }
        ScopeState state = new ScopeState(runId, sourceId);
        scopesByRunId.computeIfAbsent(runId, ignored -> new ConcurrentHashMap<>()).put(sourceId, state);
        ScopeState previous = currentScope.get();
        currentScope.set(state);
        return new Scope(this, state, previous);
    }

    public void register(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        ScopeState state = currentScope.get();
        if (state == null) {
            closeQuietly(resource);
            return;
        }
        state.register(resource);
    }

    public void cancelRun(String runId) {
        ConcurrentHashMap<String, ScopeState> scopes = scopesByRunId.get(runId);
        if (scopes == null) {
            return;
        }
        List.copyOf(scopes.values()).forEach(ScopeState::cancel);
    }

    public void clearRun(String runId) {
        scopesByRunId.remove(runId);
    }

    private void closeScope(ScopeState state, ScopeState previous) {
        if (currentScope.get() == state) {
            if (previous == null) {
                currentScope.remove();
            } else {
                currentScope.set(previous);
            }
        }
        ConcurrentHashMap<String, ScopeState> scopes = scopesByRunId.get(state.runId);
        if (scopes == null) {
            return;
        }
        scopes.remove(state.sourceId, state);
        if (scopes.isEmpty()) {
            scopesByRunId.remove(state.runId, scopes);
        }
    }

    private void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
            // ignored during cooperative cancellation
        }
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null, null, null);

        private final PollCancellationService service;
        private final ScopeState state;
        private final ScopeState previous;
        private boolean closed;

        private Scope(PollCancellationService service, ScopeState state, ScopeState previous) {
            this.service = service;
            this.state = state;
            this.previous = previous;
        }

        public static Scope noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (closed || service == null || state == null) {
                return;
            }
            closed = true;
            service.closeScope(state, previous);
        }
    }

    private static final class ScopeState {
        private final String runId;
        private final String sourceId;
        private final Set<AutoCloseable> resources = ConcurrentHashMap.newKeySet();
        private volatile boolean cancelled;

        private ScopeState(String runId, String sourceId) {
            this.runId = runId;
            this.sourceId = sourceId;
        }

        private void register(AutoCloseable resource) {
            resources.add(resource);
            if (cancelled) {
                closeQuietly(resource);
                resources.remove(resource);
            }
        }

        private void cancel() {
            cancelled = true;
            List.copyOf(resources).forEach(ScopeState::closeQuietly);
        }

        private static void closeQuietly(AutoCloseable resource) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // ignored during cooperative cancellation
            }
        }
    }
}
