package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.LiveEventView;
import dev.inboxbridge.dto.LiveNotificationContentView;
import dev.inboxbridge.dto.LiveNotificationView;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.persistence.AppUser;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

@ApplicationScoped
public class PollingLiveService {
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicReference<PollingLiveRunState> activeRun = new AtomicReference<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    SessionLocationAlertService sessionLocationAlertService;

    @Inject
    PollingLivePresentationService pollingLivePresentationService;

    public PollRunHandle startRun(String trigger, List<RuntimeEmailAccount> emailAccounts, AppUser actor) {
        synchronized (lock) {
            if (activeRun.get() != null) {
                return null;
            }
            PollingLiveRunState state = new PollingLiveRunState(trigger, actor, emailAccounts);
            activeRun.set(state);
            publishPollEvent("poll-run-started", state);
            publishNotification(presentationService().notificationForRunStarted(state));
            return new PollRunHandle(state.runId);
        }
    }

    public String currentActiveSourceId() {
        PollingLiveRunState state = activeRun.get();
        if (state == null) {
            return null;
        }
        synchronized (lock) {
            return state.primaryActiveSourceId();
        }
    }

    public PollLiveView snapshotFor(AppUser viewer) {
        PollingLiveRunState state = activeRun.get();
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
            PollingLiveRunState state = requireControllableRun(actor);
            if (state == null || state.pauseRequested || "PAUSED".equals(state.state)) {
                return false;
            }
            state.pauseRequested = true;
            state.updatedAt = Instant.now();
            state.state = "PAUSING";
            publishPollEvent("poll-run-pausing", state);
            publishNotification(simpleNotification("notifications.livePollPauseRequested", "warning", "live-poll"));
            return true;
        }
    }

    public boolean requestResume(AppUser actor) {
        synchronized (lock) {
            PollingLiveRunState state = requireControllableRun(actor);
            if (state == null || (!state.pauseRequested && !"PAUSED".equals(state.state) && !"PAUSING".equals(state.state))) {
                return false;
            }
            state.pauseRequested = false;
            state.state = "RUNNING";
            state.updatedAt = Instant.now();
            lock.notifyAll();
            publishPollEvent("poll-run-resumed", state);
            publishNotification(simpleNotification("notifications.livePollResumed", "success", "live-poll"));
            return true;
        }
    }

    public boolean requestStop(AppUser actor) {
        List<Runnable> cancellationActions = List.of();
        synchronized (lock) {
            PollingLiveRunState state = requireControllableRun(actor);
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
            publishNotification(simpleNotification("notifications.livePollStopRequested", "warning", "live-poll"));
        }
        cancellationActions.forEach(this::runCancellationAction);
        return true;
    }

    public boolean moveSourceToFront(AppUser actor, String sourceId) {
        synchronized (lock) {
            PollingLiveRunState state = requireControllableRun(actor);
            if (state == null) {
                return false;
            }
            PollingLiveRunState.SourceState source = state.sourcesById.get(sourceId);
            if (source == null || !source.isQueued()) {
                return false;
            }
            state.queue.remove(sourceId);
            state.queue.addFirst(sourceId);
            state.updatedAt = Instant.now();
            publishPollEvent("poll-source-reprioritized", state);
            publishNotification(simpleNotification("notifications.livePollSourceMovedNext", "success", "live-poll"));
            return true;
        }
    }

    public boolean retrySource(AppUser actor, String sourceId) {
        synchronized (lock) {
            PollingLiveRunState state = requireControllableRun(actor);
            if (state == null) {
                return false;
            }
            PollingLiveRunState.SourceState source = state.sourcesById.get(sourceId);
            if (source == null || "RUNNING".equals(source.state) || source.isQueued()) {
                return false;
            }
            source.state = "RETRY_QUEUED";
            source.error = null;
            source.startedAt = null;
            source.finishedAt = null;
            source.totalMessages = 0;
            source.totalBytes = 0L;
            source.processedBytes = 0L;
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
            publishNotification(simpleNotification("notifications.livePollSourceRetryQueued", "warning", "live-poll"));
            return true;
        }
    }

    public String nextSourceId(String runId) {
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
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
                PollingLiveRunState.SourceState source = state.sourcesById.get(nextId);
                if (source == null || !source.isQueued()) {
                    continue;
                }
                state.activeSourceIds.add(nextId);
                state.state = "RUNNING";
                source.state = "RUNNING";
                source.startedAt = Instant.now();
                state.updatedAt = source.startedAt;
                publishPollEvent("poll-source-started", state);
                publishNotification(presentationService().notificationForSourceStarted(state, source));
                return nextId;
            }
        }
    }

    public boolean awaitIfPausedOrStopped(String runId) {
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
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
            PollingLiveRunState state = requireRun(runId);
            return state != null && state.stopRequested;
        }
    }

    public String stopRequestedByUsername(String runId) {
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
            return state == null ? null : state.stopRequestedByUsername;
        }
    }

    public void markSourceFinished(String runId, String sourceId, int fetched, int imported, int duplicates, String error) {
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            PollingLiveRunState.SourceState source = state.sourcesById.get(sourceId);
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
            publishNotification(presentationService().notificationForSourceFinished(state, source));
        }
    }

    public void updateSourceProgress(String runId, String sourceId, int totalMessages, long totalBytes, long processedBytes, int fetched, int imported, int duplicates) {
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            PollingLiveRunState.SourceState source = state.sourcesById.get(sourceId);
            if (source == null) {
                return;
            }
            source.totalMessages = Math.max(0, totalMessages);
            source.totalBytes = Math.max(0L, totalBytes);
            source.processedBytes = Math.max(0L, processedBytes);
            source.fetched = Math.max(0, fetched);
            source.imported = Math.max(0, imported);
            source.duplicates = Math.max(0, duplicates);
            source.error = null;
            source.finishedAt = null;
            state.updatedAt = Instant.now();
            publishPollEvent("poll-source-progress", state);
        }
    }

    public void markSourceStopped(String runId, String sourceId, int fetched, int imported, int duplicates) {
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            PollingLiveRunState.SourceState source = state.sourcesById.get(sourceId);
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
            PollingLiveRunState state = requireRun(runId);
            if (state == null) {
                return;
            }
            state.activeSourceIds.clear();
            if ("STOPPED".equals(finalState)) {
                Instant stoppedAt = Instant.now();
                for (PollingLiveRunState.SourceState source : state.sourcesById.values()) {
                    if (source.isQueued() || "RUNNING".equals(source.state)) {
                        source.state = "STOPPED";
                        source.finishedAt = stoppedAt;
                    }
                }
            }
            state.state = finalState;
            state.updatedAt = Instant.now();
            publishPollEvent("poll-run-finished", state);
            publishNotification(presentationService().notificationForRunFinished(state));
            activeRun.set(null);
        }
    }

    private PollingLiveRunState requireControllableRun(AppUser actor) {
        PollingLiveRunState state = activeRun.get();
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

    private PollingLiveRunState requireRun(String runId) {
        PollingLiveRunState state = activeRun.get();
        if (state == null || !state.runId.equals(runId)) {
            return null;
        }
        return state;
    }

    private void publishPollEvent(String type, PollingLiveRunState state) {
        Instant timestamp = Instant.now();
        for (Subscriber subscriber : subscribers) {
            PollLiveView view = presentationService().buildView(state, subscriber.viewerId, subscriber.admin);
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
        PollingLiveRunState state = activeRun.get();
        for (Subscriber subscriber : subscribers) {
            PollLiveView view = state == null ? null : presentationService().buildView(state, subscriber.viewerId, subscriber.admin);
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
        PollingLiveRunState state = activeRun.get();
        for (Subscriber subscriber : subscribers) {
            if (!viewerId.equals(subscriber.viewerId)) {
                continue;
            }
            PollLiveView view = state == null ? null : presentationService().buildView(state, subscriber.viewerId, subscriber.admin);
            subscriber.emit(new LiveEventView("notification-created", timestamp, view, notification, null));
        }
    }

    public void publishNewSignInDetected(Long viewerId, SessionStreamKind streamKind, Long sessionId) {
        if (viewerId == null || streamKind == null || sessionId == null) {
            return;
        }
        SessionLocationAlertService.SessionLocationAssessment assessment = sessionLocationAlertService == null
                ? new SessionLocationAlertService.SessionLocationAssessment(null, false)
                : sessionLocationAlertService.assessNewSession(viewerId, streamKind.name(), sessionId, null);
        publishNotificationToUser(viewerId, presentationService().newSignInNotification(assessment, streamKind, sessionId));
    }

    public void registerCancellationAction(String runId, Runnable action) {
        if (action == null) {
            return;
        }
        boolean runImmediately = false;
        synchronized (lock) {
            PollingLiveRunState state = requireRun(runId);
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

    private PollingLivePresentationService presentationService() {
        return pollingLivePresentationService == null ? new PollingLivePresentationService() : pollingLivePresentationService;
    }

    private PollLiveView buildView(PollingLiveRunState state, AppUser viewer) {
        return presentationService().buildView(state, viewer.id, viewer.role == AppUser.Role.ADMIN);
    }

    private LiveNotificationView simpleNotification(String key, String tone, String groupKey) {
        LiveNotificationContentView content = new LiveNotificationContentView("translation", key, java.util.Map.of());
        return new LiveNotificationView(null, content, groupKey, content, false, List.of(), null, tone);
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

}
