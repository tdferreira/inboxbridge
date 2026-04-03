package dev.inboxbridge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.LiveEventView;
import dev.inboxbridge.dto.LiveNotificationContentView;
import dev.inboxbridge.dto.LiveNotificationView;
import dev.inboxbridge.dto.PollLiveSourceView;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.persistence.AppUser;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;

@ApplicationScoped
public class PollingLiveService {
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicReference<RunState> activeRun = new AtomicReference<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    public PollRunHandle startRun(String trigger, List<RuntimeEmailAccount> emailAccounts, AppUser actor) {
        synchronized (lock) {
            if (activeRun.get() != null) {
                return null;
            }
            RunState state = new RunState(trigger, actor, emailAccounts);
            activeRun.set(state);
            publishPollEvent("poll-run-started", state);
            publishNotification(notificationForRunStarted(state));
            return new PollRunHandle(state.runId);
        }
    }

    public String currentActiveSourceId() {
        RunState state = activeRun.get();
        if (state == null) {
            return null;
        }
        synchronized (lock) {
            return state.primaryActiveSourceId();
        }
    }

    public PollLiveView snapshotFor(AppUser viewer) {
        RunState state = activeRun.get();
        if (state == null) {
            return new PollLiveView(false, null, "IDLE", null, null, false, null, null, null, List.of());
        }
        synchronized (lock) {
            return buildView(state, viewer);
        }
    }

    public Multi<LiveEventView> subscribe(AppUser viewer, SessionStreamKind streamKind, Long streamSessionId) {
        return Multi.createFrom().emitter((MultiEmitter<? super LiveEventView> emitter) -> {
            Subscriber subscriber = new Subscriber(viewer.id, viewer.role == AppUser.Role.ADMIN, streamKind, streamSessionId, emitter);
            subscribers.add(subscriber);
            var heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                    () -> {
                        if (subscribers.contains(subscriber)) {
                            subscriber.emit(new LiveEventView("keepalive", Instant.now(), null, null, null));
                        }
                    },
                    HEARTBEAT_INTERVAL_SECONDS,
                    HEARTBEAT_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            emitter.onTermination(() -> {
                subscribers.remove(subscriber);
                heartbeat.cancel(true);
            });
            PollLiveView snapshot = snapshotFor(viewer);
            if (snapshot.running() || !snapshot.sources().isEmpty()) {
                emitter.emit(new LiveEventView("poll-snapshot", Instant.now(), snapshot, null, null));
            }
        });
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    public void publishSessionRevoked(Long viewerId, SessionStreamKind streamKind, Long streamSessionId) {
        if (viewerId == null || streamKind == null || streamSessionId == null) {
            return;
        }
        Instant timestamp = Instant.now();
        for (Subscriber subscriber : subscribers) {
            if (subscriber.streamKind != streamKind || !viewerId.equals(subscriber.viewerId)) {
                continue;
            }
            subscriber.emit(new LiveEventView("session-revoked", timestamp, null, null, streamSessionId));
            subscriber.close();
        }
    }

    public boolean requestPause(AppUser actor) {
        synchronized (lock) {
            RunState state = requireControllableRun(actor);
            if (state == null || state.pauseRequested || "PAUSED".equals(state.state)) {
                return false;
            }
            state.pauseRequested = true;
            state.updatedAt = Instant.now();
            state.state = "PAUSING";
            publishPollEvent("poll-run-pausing", state);
            publishNotification(notification("notifications.livePollPauseRequested", "warning", "live-poll"));
            return true;
        }
    }

    public boolean requestResume(AppUser actor) {
        synchronized (lock) {
            RunState state = requireControllableRun(actor);
            if (state == null || (!state.pauseRequested && !"PAUSED".equals(state.state) && !"PAUSING".equals(state.state))) {
                return false;
            }
            state.pauseRequested = false;
            state.state = "RUNNING";
            state.updatedAt = Instant.now();
            lock.notifyAll();
            publishPollEvent("poll-run-resumed", state);
            publishNotification(notification("notifications.livePollResumed", "success", "live-poll"));
            return true;
        }
    }

    public boolean requestStop(AppUser actor) {
        List<Runnable> cancellationActions = List.of();
        synchronized (lock) {
            RunState state = requireControllableRun(actor);
            if (state == null || state.stopRequested) {
                return false;
            }
            state.stopRequested = true;
            state.stopRequestedByUsername = actor == null ? null : actor.username;
            state.updatedAt = Instant.now();
            state.state = "STOPPING";
            lock.notifyAll();
            cancellationActions = List.copyOf(state.cancellationActions);
            publishPollEvent("poll-run-stopping", state);
            publishNotification(notification("notifications.livePollStopRequested", "warning", "live-poll"));
        }
        cancellationActions.forEach(this::runCancellationAction);
        return true;
    }

    public boolean moveSourceToFront(AppUser actor, String sourceId) {
        synchronized (lock) {
            RunState state = requireControllableRun(actor);
            if (state == null) {
                return false;
            }
            SourceState source = state.sourcesById.get(sourceId);
            if (source == null || !source.isQueued()) {
                return false;
            }
            state.queue.remove(sourceId);
            state.queue.addFirst(sourceId);
            state.updatedAt = Instant.now();
            publishPollEvent("poll-source-reprioritized", state);
            publishNotification(notification("notifications.livePollSourceMovedNext", "success", "live-poll"));
            return true;
        }
    }

    public boolean retrySource(AppUser actor, String sourceId) {
        synchronized (lock) {
            RunState state = requireControllableRun(actor);
            if (state == null) {
                return false;
            }
            SourceState source = state.sourcesById.get(sourceId);
            if (source == null || "RUNNING".equals(source.state) || source.isQueued()) {
                return false;
            }
            source.state = "RETRY_QUEUED";
            source.error = null;
            source.startedAt = null;
            source.finishedAt = null;
            source.fetched = 0;
            source.imported = 0;
            source.duplicates = 0;
            source.attempt += 1;
            state.queue.addFirst(sourceId);
            state.updatedAt = Instant.now();
            if ("STOPPING".equals(state.state)) {
                state.stopRequested = false;
                state.state = "RUNNING";
            }
            publishPollEvent("poll-source-retry-queued", state);
            publishNotification(notification("notifications.livePollSourceRetryQueued", "warning", "live-poll"));
            return true;
        }
    }

    public String nextSourceId(String runId) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            if (state == null) {
                return null;
            }
            while (true) {
                if (state.stopRequested) {
                    return null;
                }
                if (state.pauseRequested) {
                    state.state = "PAUSED";
                    state.updatedAt = Instant.now();
                    publishPollEvent("poll-run-paused", state);
                    try {
                        lock.wait(250L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                String nextId = state.queue.pollFirst();
                if (nextId == null) {
                    return null;
                }
                SourceState source = state.sourcesById.get(nextId);
                if (source == null || !source.isQueued()) {
                    continue;
                }
                state.activeSourceIds.add(nextId);
                state.state = "RUNNING";
                source.state = "RUNNING";
                source.startedAt = Instant.now();
                state.updatedAt = source.startedAt;
                publishPollEvent("poll-source-started", state);
                publishNotification(notificationForSourceStarted(state, source));
                return nextId;
            }
        }
    }

    public boolean awaitIfPausedOrStopped(String runId) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            if (state == null) {
                return false;
            }
            while (true) {
                if (state.stopRequested) {
                    return false;
                }
                if (!state.pauseRequested) {
                    return true;
                }
                if (!"PAUSED".equals(state.state)) {
                    state.state = "PAUSED";
                    state.updatedAt = Instant.now();
                    publishPollEvent("poll-run-paused", state);
                }
                try {
                    lock.wait(250L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    public boolean stopRequested(String runId) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            return state != null && state.stopRequested;
        }
    }

    public String stopRequestedByUsername(String runId) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            return state == null ? null : state.stopRequestedByUsername;
        }
    }

    public void markSourceFinished(String runId, String sourceId, int fetched, int imported, int duplicates, String error) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            SourceState source = state.sourcesById.get(sourceId);
            if (source == null) {
                return;
            }
            source.fetched = fetched;
            source.imported = imported;
            source.duplicates = duplicates;
            source.error = error;
            source.finishedAt = Instant.now();
            source.state = error == null ? "COMPLETED" : "FAILED";
            state.activeSourceIds.remove(sourceId);
            state.updatedAt = source.finishedAt;
            publishPollEvent("poll-source-finished", state);
            publishNotification(notificationForSourceFinished(state, source));
        }
    }

    public void markSourceStopped(String runId, String sourceId, int fetched, int imported, int duplicates) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            SourceState source = state.sourcesById.get(sourceId);
            if (source == null) {
                return;
            }
            source.fetched = fetched;
            source.imported = imported;
            source.duplicates = duplicates;
            source.error = null;
            source.finishedAt = Instant.now();
            source.state = "STOPPED";
            state.activeSourceIds.remove(sourceId);
            state.updatedAt = source.finishedAt;
            publishPollEvent("poll-source-finished", state);
        }
    }

    public void finishRun(String runId, String finalState) {
        synchronized (lock) {
            RunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            state.activeSourceIds.clear();
            if ("STOPPED".equals(finalState)) {
                Instant stoppedAt = Instant.now();
                for (SourceState source : state.sourcesById.values()) {
                    if (source.isQueued() || "RUNNING".equals(source.state)) {
                        source.state = "STOPPED";
                        source.finishedAt = stoppedAt;
                    }
                }
            }
            state.state = finalState;
            state.updatedAt = Instant.now();
            publishPollEvent("poll-run-finished", state);
            publishNotification(notificationForRunFinished(state));
            activeRun.set(null);
        }
    }

    private RunState requireControllableRun(AppUser actor) {
        RunState state = activeRun.get();
        if (state == null) {
            return null;
        }
        if (actor.role == AppUser.Role.ADMIN) {
            return state;
        }
        if (state.actorUserId != null && state.actorUserId.equals(actor.id)) {
            return state;
        }
        return null;
    }

    private RunState requireRun(String runId) {
        RunState state = activeRun.get();
        if (state == null || !state.runId.equals(runId)) {
            return null;
        }
        return state;
    }

    private void publishPollEvent(String type, RunState state) {
        Instant timestamp = Instant.now();
        for (Subscriber subscriber : subscribers) {
            PollLiveView view = buildView(state, subscriber.viewerId, subscriber.admin);
            if (view == null) {
                continue;
            }
            subscriber.emit(new LiveEventView(type, timestamp, view, null, null));
        }
    }

    public void publishNotification(LiveNotificationView notification) {
        if (notification == null) {
            return;
        }
        Instant timestamp = Instant.now();
        RunState state = activeRun.get();
        for (Subscriber subscriber : subscribers) {
            PollLiveView view = state == null ? null : buildView(state, subscriber.viewerId, subscriber.admin);
            if (state != null && view == null && !subscriber.admin) {
                continue;
            }
            subscriber.emit(new LiveEventView("notification-created", timestamp, view, notification, null));
        }
    }

    public void publishNotificationToUser(Long viewerId, LiveNotificationView notification) {
        if (viewerId == null || notification == null) {
            return;
        }
        Instant timestamp = Instant.now();
        RunState state = activeRun.get();
        for (Subscriber subscriber : subscribers) {
            if (!viewerId.equals(subscriber.viewerId)) {
                continue;
            }
            PollLiveView view = state == null ? null : buildView(state, subscriber.viewerId, subscriber.admin);
            subscriber.emit(new LiveEventView("notification-created", timestamp, view, notification, null));
        }
    }

    public void publishNewSignInDetected(Long viewerId, SessionStreamKind streamKind, Long sessionId) {
        if (viewerId == null || streamKind == null || sessionId == null) {
            return;
        }
        publishNotificationToUser(viewerId, notification(
                "notifications.newSessionDetected",
                "warning",
                "session-activity",
                buildRecentSessionTargetId(streamKind, sessionId)));
    }

    private PollLiveView buildView(RunState state, AppUser viewer) {
        return buildView(state, viewer.id, viewer.role == AppUser.Role.ADMIN);
    }

    private PollLiveView buildView(RunState state, Long viewerId, boolean admin) {
        List<PollLiveSourceView> sources = state.sourcesById.values().stream()
                .filter(source -> admin || (source.ownerUserId != null && source.ownerUserId.equals(viewerId)))
                .sorted(Comparator
                        .comparingInt((SourceState source) -> source.position(state.queue, state.activeSourceIds))
                        .thenComparing(source -> source.label))
                .map(source -> new PollLiveSourceView(
                        source.sourceId,
                        source.ownerUsername,
                        source.label,
                        source.state,
                        source.actionable(admin, viewerId, state.actorUserId),
                        source.position(state.queue, state.activeSourceIds),
                        source.attempt,
                        source.fetched,
                        source.imported,
                        source.duplicates,
                        source.error,
                        source.startedAt,
                        source.finishedAt))
                .toList();
        if (!admin && sources.isEmpty()) {
            return null;
        }
        boolean viewerCanControl = admin || (state.actorUserId != null && state.actorUserId.equals(viewerId));
        return new PollLiveView(
                true,
                state.runId,
                state.state,
                state.trigger,
                state.actorUsername,
                viewerCanControl,
                admin ? state.primaryActiveSourceId() : activeVisibleSourceId(state, viewerId),
                state.startedAt,
                state.updatedAt,
                sources);
    }

    private String activeVisibleSourceId(RunState state, Long viewerId) {
        for (String activeSourceId : state.activeSourceIds) {
            SourceState source = state.sourcesById.get(activeSourceId);
            if (source == null) {
                continue;
            }
            if (source.ownerUserId != null && source.ownerUserId.equals(viewerId)) {
                return source.sourceId;
            }
        }
        return null;
    }

    public void registerCancellationAction(String runId, Runnable action) {
        if (action == null) {
            return;
        }
        boolean runImmediately = false;
        synchronized (lock) {
            RunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            if (state.stopRequested) {
                runImmediately = true;
            } else {
                state.cancellationActions.add(action);
            }
        }
        if (runImmediately) {
            runCancellationAction(action);
        }
    }

    private void runCancellationAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // cooperative cancellation should not fail the stop request path
        }
    }

    private LiveNotificationView notificationForRunStarted(RunState state) {
        String key = state.actorAdmin ? "notifications.pollStarted" : "notifications.userPollStarted";
        String targetId = state.actorAdmin ? "system-dashboard-section" : "user-polling-section";
        return notification(key, "warning", state.actorAdmin ? "global-poll" : "user-poll", targetId);
    }

    private LiveNotificationView notificationForRunFinished(RunState state) {
        if ("STOPPED".equals(state.state)) {
            return null;
        }
        String key = state.actorAdmin ? "notifications.livePollFinished" : "notifications.liveUserPollFinished";
        String targetId = state.actorAdmin ? "system-dashboard-section" : "user-polling-section";
        return notification(key, "success", state.actorAdmin ? "global-poll" : "user-poll", targetId, true);
    }

    private LiveNotificationView notificationForSourceStarted(RunState state, SourceState source) {
        return notification(
                "notifications.fetcherPollStarted",
                "warning",
                "fetcher-poll:" + source.sourceId,
                "source-email-accounts-section",
                false,
                Map.of("emailAccountId", source.sourceId));
    }

    private LiveNotificationView notificationForSourceFinished(RunState state, SourceState source) {
        String key = source.error == null ? "notifications.livePollSourceFinished" : "notifications.livePollSourceFailed";
        return notification(
                key,
                source.error == null ? "success" : "error",
                "fetcher-poll:" + source.sourceId,
                "source-email-accounts-section",
                true,
                Map.of(
                        "emailAccountId", source.sourceId,
                        "fetched", String.valueOf(source.fetched),
                        "imported", String.valueOf(source.imported),
                        "duplicates", String.valueOf(source.duplicates)));
    }

    private LiveNotificationView notification(String key, String tone, String groupKey) {
        return notification(key, tone, groupKey, null, false, Map.of());
    }

    private LiveNotificationView notification(String key, String tone, String groupKey, String targetId) {
        return notification(key, tone, groupKey, targetId, false, Map.of());
    }

    private LiveNotificationView notification(String key, String tone, String groupKey, String targetId, boolean replaceGroup) {
        return notification(key, tone, groupKey, targetId, replaceGroup, Map.of());
    }

    private LiveNotificationView notification(
            String key,
            String tone,
            String groupKey,
            String targetId,
            boolean replaceGroup,
            Map<String, String> params) {
        LiveNotificationContentView content = new LiveNotificationContentView("translation", key, params);
        return new LiveNotificationView(null, content, groupKey, content, replaceGroup, List.of(), targetId, tone);
    }

    private String buildRecentSessionTargetId(SessionStreamKind streamKind, Long sessionId) {
        return "recent-session-" + streamKind.name() + "-" + sessionId;
    }

    public record PollRunHandle(String runId) {
    }

    public enum SessionStreamKind {
        BROWSER,
        REMOTE
    }

    private record Subscriber(
            Long viewerId,
            boolean admin,
            SessionStreamKind streamKind,
            Long streamSessionId,
            MultiEmitter<? super LiveEventView> emitter) {
        void emit(LiveEventView event) {
            emitter.emit(event);
        }

        void close() {
            emitter.complete();
        }
    }

    private static final class RunState {
        private final String runId = UUID.randomUUID().toString();
        private final String trigger;
        private final Instant startedAt = Instant.now();
        private final Long actorUserId;
        private final String actorUsername;
        private final boolean actorAdmin;
        private final LinkedHashMap<String, SourceState> sourcesById = new LinkedHashMap<>();
        private final java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        private final LinkedHashSet<String> activeSourceIds = new LinkedHashSet<>();
        private final List<Runnable> cancellationActions = new ArrayList<>();
        private String state = "RUNNING";
        private boolean pauseRequested;
        private boolean stopRequested;
        private String stopRequestedByUsername;
        private Instant updatedAt = startedAt;

        private RunState(String trigger, AppUser actor, List<RuntimeEmailAccount> emailAccounts) {
            this.trigger = trigger;
            this.actorUserId = actor == null ? null : actor.id;
            this.actorUsername = actor == null ? "system" : actor.username;
            this.actorAdmin = actor != null && actor.role == AppUser.Role.ADMIN;
            for (RuntimeEmailAccount emailAccount : emailAccounts) {
                SourceState source = new SourceState(emailAccount);
                sourcesById.put(emailAccount.id(), source);
                queue.addLast(emailAccount.id());
            }
        }

        private String primaryActiveSourceId() {
            return activeSourceIds.stream().findFirst().orElse(null);
        }
    }

    private static final class SourceState {
        private final String sourceId;
        private final Long ownerUserId;
        private final String ownerUsername;
        private final String label;
        private String state = "QUEUED";
        private int attempt = 1;
        private int fetched;
        private int imported;
        private int duplicates;
        private String error;
        private Instant startedAt;
        private Instant finishedAt;

        private SourceState(RuntimeEmailAccount emailAccount) {
            this.sourceId = emailAccount.id();
            this.ownerUserId = emailAccount.ownerUserId();
            this.ownerUsername = emailAccount.ownerUsername();
            this.label = emailAccount.customLabel().orElse(emailAccount.id());
        }

        private boolean isQueued() {
            return "QUEUED".equals(state) || "RETRY_QUEUED".equals(state);
        }

        private int position(java.util.ArrayDeque<String> queue, java.util.Set<String> activeSourceIds) {
            if (activeSourceIds.contains(sourceId)) {
                return 0;
            }
            int index = 1;
            for (String queuedId : queue) {
                if (sourceId.equals(queuedId)) {
                    return index;
                }
                index += 1;
            }
            return Integer.MAX_VALUE;
        }

        private boolean actionable(boolean admin, Long viewerId, Long actorUserId) {
            return admin || (actorUserId != null && actorUserId.equals(viewerId));
        }
    }
}
