package dev.inboxbridge.service.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import dev.inboxbridge.service.auth.SessionLocationAlertService;
import dev.inboxbridge.service.auth.SessionLocationAlertService.SessionLocationAssessment;

class PollingLiveServiceTest {

    @Test
    void snapshotForUserShowsOnlyOwnedSourcesWhileAdminSeesEverything() {
        PollingLiveService service = new PollingLiveService();
        AppUser admin = actor(1L, "admin", AppUser.Role.ADMIN);
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);

        PollingLiveService.PollRunHandle handle = service.startRun(
                "admin-ui",
                List.of(source("alice-source", 7L, "alice"), source("bob-source", 8L, "bob")),
                admin);

        assertNotNull(handle);
        assertEquals(2, service.snapshotFor(admin).sources().size());
        assertEquals(List.of("alice-source"), service.snapshotFor(alice).sources().stream().map(source -> source.sourceId()).toList());
        assertFalse(service.snapshotFor(alice).viewerCanControl());
    }

    @Test
    void moveNextAndRetryUpdateQueuedOrder() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice"), source("beta", 7L, "alice"), source("gamma", 7L, "alice")),
                alice);

        assertNotNull(handle);
        assertEquals("alpha", service.nextSourceId(handle.runId()));
        service.markSourceFinished(handle.runId(), "alpha", 1, 1, 0, null);

        assertTrue(service.moveSourceToFront(alice, "gamma"));
        assertEquals("gamma", service.nextSourceId(handle.runId()));
        service.markSourceFinished(handle.runId(), "gamma", 0, 0, 0, "boom");

        assertTrue(service.retrySource(alice, "gamma"));
        assertEquals("gamma", service.nextSourceId(handle.runId()));
    }

    @Test
    void snapshotReflectsMultipleRunningSourcesWhenWorkersDequeueInParallel() {
        PollingLiveService service = new PollingLiveService();
        AppUser admin = actor(1L, "admin", AppUser.Role.ADMIN);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "admin-ui",
                List.of(source("alpha", 7L, "alice"), source("beta", 8L, "bob"), source("gamma", 7L, "alice")),
                admin);

        assertNotNull(handle);
        assertEquals("alpha", service.nextSourceId(handle.runId()));
        assertEquals("beta", service.nextSourceId(handle.runId()));

        var snapshot = service.snapshotFor(admin);
        assertEquals(2, snapshot.sources().stream().filter((source) -> "RUNNING".equals(source.state())).count());
        assertTrue(List.of("alpha", "beta").contains(snapshot.activeSourceId()));
        assertEquals(List.of("gamma"), snapshot.sources().stream()
                .filter((source) -> "QUEUED".equals(source.state()))
                .map((source) -> source.sourceId())
                .toList());
    }

    @Test
    void snapshotIncludesPerSourceProgressTotals() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice")),
                alice);

        assertNotNull(handle);
        assertEquals("alpha", service.nextSourceId(handle.runId()));
        service.updateSourceProgress(handle.runId(), "alpha", 50, 10_000L, 6_000L, 3, 2, 1);

        var snapshot = service.snapshotFor(alice);
        assertEquals(50, snapshot.sources().getFirst().totalMessages());
        assertEquals(3, snapshot.sources().getFirst().processedMessages());
        assertEquals(10_000L, snapshot.sources().getFirst().totalBytes());
        assertEquals(6_000L, snapshot.sources().getFirst().processedBytes());
        assertEquals(3, snapshot.sources().getFirst().fetched());
        assertEquals(2, snapshot.sources().getFirst().imported());
        assertEquals(1, snapshot.sources().getFirst().duplicates());
    }

    @Test
    void pauseAndResumeChangeLiveState() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice")),
                alice);

        assertNotNull(handle);
        assertTrue(service.requestPause(alice));
        assertEquals("PAUSING", service.snapshotFor(alice).state());
        assertTrue(service.requestResume(alice));
        assertEquals("RUNNING", service.snapshotFor(alice).state());
        assertTrue(service.requestStop(alice));
        assertEquals("STOPPING", service.snapshotFor(alice).state());
    }

    @Test
    void awaitIfPausedOrStoppedBlocksUntilResumeAndStopsWhenRequested() throws Exception {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice")),
                alice);

        assertNotNull(handle);
        assertEquals("alpha", service.nextSourceId(handle.runId()));
        assertTrue(service.requestPause(alice));

        AtomicBoolean resumed = new AtomicBoolean(false);
        Thread waitingThread = new Thread(() -> resumed.set(service.awaitIfPausedOrStopped(handle.runId())));
        waitingThread.start();

        waitFor(() -> "PAUSED".equals(service.snapshotFor(alice).state()));
        assertFalse(resumed.get());

        assertTrue(service.requestResume(alice));
        waitingThread.join(2000L);
        assertTrue(resumed.get());

        assertTrue(service.requestStop(alice));
        assertFalse(service.awaitIfPausedOrStopped(handle.runId()));
    }

    @Test
    void publishSessionRevokedCompletesMatchingSubscriber() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        AtomicReference<String> eventType = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        service.subscribe(alice, PollingLiveService.SessionStreamKind.REMOTE, 55L)
                .subscribe().with(
                        event -> eventType.set(event.type()),
                        failure -> {
                            throw new AssertionError(failure);
                        },
                        () -> completed.set(true));

        service.publishSessionRevoked(alice.id, PollingLiveService.SessionStreamKind.REMOTE, 55L);

        assertEquals("session-revoked", eventType.get());
        assertTrue(completed.get());
    }

    @Test
    void publishSessionRevokedSupportsExtensionSubscribers() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        AtomicReference<String> eventType = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        service.subscribe(alice, PollingLiveService.SessionStreamKind.EXTENSION, 77L)
                .subscribe().with(
                        event -> eventType.set(event.type()),
                        failure -> {
                            throw new AssertionError(failure);
                        },
                        () -> completed.set(true));

        service.publishSessionRevoked(alice.id, PollingLiveService.SessionStreamKind.EXTENSION, 77L);

        assertEquals("session-revoked", eventType.get());
        assertTrue(completed.get());
    }

    @Test
    void publishNewSignInDetectedNotifiesMatchingUserSubscribers() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        AtomicReference<dev.inboxbridge.dto.LiveEventView> eventRef = new AtomicReference<>();

        service.subscribe(alice, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(eventRef::set);

        service.publishNewSignInDetected(alice.id, PollingLiveService.SessionStreamKind.REMOTE, 55L);

        assertEquals("notification-created", eventRef.get().type());
        assertEquals("notifications.newSessionDetected", eventRef.get().notification().message().key());
        assertEquals("recent-session-REMOTE-55", eventRef.get().notification().targetId());
    }

    @Test
    void publishNewSignInDetectedHighlightsUnusualLocationsWhenAvailable() {
        PollingLiveService service = new PollingLiveService();
        service.sessionLocationAlertService = new SessionLocationAlertService() {
            @Override
            public SessionLocationAssessment assessNewSession(Long userId, String sessionType, Long sessionId, String locationLabel) {
                return new SessionLocationAssessment("Berlin, DE", true);
            }
        };
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        AtomicReference<dev.inboxbridge.dto.LiveEventView> eventRef = new AtomicReference<>();

        service.subscribe(alice, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(eventRef::set);

        service.publishNewSignInDetected(alice.id, PollingLiveService.SessionStreamKind.REMOTE, 55L);

        assertEquals("notifications.newSessionDetectedFromUnusualLocation", eventRef.get().notification().message().key());
        assertEquals("Berlin, DE", eventRef.get().notification().message().params().get("location"));
    }

    @Test
    void skipReasonsDoNotEmitLiveFailureNotifications() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice")),
                alice);
        List<String> eventTypes = new CopyOnWriteArrayList<>();

        assertNotNull(handle);
        service.subscribe(alice, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(event -> eventTypes.add(event.type()));

        service.markSourceFinished(handle.runId(), "alpha", 0, 0, 0, "COOLDOWN");

        assertTrue(eventTypes.contains("poll-snapshot"));
        assertTrue(eventTypes.contains("poll-source-finished"));
        assertFalse(eventTypes.contains("notification-created"));
    }

    @Test
    void realSourceFailuresStillEmitLiveFailureNotifications() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice")),
                alice);
        List<String> eventTypes = new CopyOnWriteArrayList<>();

        assertNotNull(handle);
        service.subscribe(alice, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(event -> eventTypes.add(event.type()));

        service.markSourceFinished(handle.runId(), "alpha", 0, 0, 0, "boom");

        assertTrue(eventTypes.contains("poll-source-finished"));
        assertTrue(eventTypes.contains("notification-created"));
    }

    @Test
    void multiSourceUserRunsDoNotEmitPerSourceSuccessNotifications() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 7L, "alice")),
                alice);
        List<String> notificationKeys = new CopyOnWriteArrayList<>();

        assertNotNull(handle);
        service.subscribe(alice, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(event -> {
                    if ("notification-created".equals(event.type()) && event.notification() != null) {
                        notificationKeys.add(event.notification().message().key());
                    }
                });

        service.markSourceFinished(handle.runId(), "alpha", 1, 1, 0, null);

        assertFalse(notificationKeys.contains("notifications.fetcherPollStarted"));
        assertFalse(notificationKeys.contains("notifications.livePollSourceFinished"));
    }

    @Test
    void singleSourceRunsEmitPerSourceStartAndSuccessNotifications() {
        PollingLiveService service = new PollingLiveService();
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "app-fetcher",
                List.of(source("alpha", 7L, "alice")),
                alice);
        List<String> notificationKeys = new CopyOnWriteArrayList<>();

        assertNotNull(handle);
        service.subscribe(alice, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(event -> {
                    if ("notification-created".equals(event.type()) && event.notification() != null) {
                        notificationKeys.add(event.notification().message().key());
                    }
                });

        assertEquals("alpha", service.nextSourceId(handle.runId()));
        service.markSourceFinished(handle.runId(), "alpha", 1, 1, 0, null);

        assertTrue(notificationKeys.contains("notifications.fetcherPollStarted"));
        assertTrue(notificationKeys.contains("notifications.livePollSourceFinished"));
    }

    @Test
    void schedulerRunsDoNotEmitUserFacingPollNotifications() {
        PollingLiveService service = new PollingLiveService();
        AppUser system = actor(-1L, "system", AppUser.Role.ADMIN);
        PollingLiveService.PollRunHandle handle = service.startRun(
                "scheduler",
                List.of(source("alpha", 7L, "alice")),
                system);
        List<String> eventTypes = new CopyOnWriteArrayList<>();

        assertNotNull(handle);
        service.subscribe(system, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(event -> eventTypes.add(event.type()));

        assertEquals("alpha", service.nextSourceId(handle.runId()));
        service.markSourceFinished(handle.runId(), "alpha", 1, 1, 0, null);
        service.finishRun(handle.runId(), "COMPLETED");

        assertTrue(eventTypes.contains("poll-snapshot"));
        assertTrue(eventTypes.contains("poll-source-started"));
        assertTrue(eventTypes.contains("poll-source-finished"));
        assertFalse(eventTypes.contains("notification-created"));
    }

    @Test
    void adminActorRunningPollFromUserWorkspaceUsesUserScopedNotifications() {
        PollingLiveService service = new PollingLiveService();
        AppUser admin = actor(1L, "admin", AppUser.Role.ADMIN);
        AtomicReference<dev.inboxbridge.dto.LiveEventView> eventRef = new AtomicReference<>();

        service.subscribe(admin, PollingLiveService.SessionStreamKind.BROWSER, 11L)
                .subscribe().with(event -> {
                    if ("notification-created".equals(event.type())) {
                        eventRef.set(event);
                    }
                });

        PollingLiveService.PollRunHandle handle = service.startRun(
                "user-ui",
                List.of(source("alpha", 1L, "admin")),
                admin);

        assertNotNull(handle);
        assertEquals("notifications.userPollStarted", eventRef.get().notification().message().key());
        assertEquals("user-polling-section", eventRef.get().notification().targetId());
        assertEquals("user-poll", eventRef.get().notification().groupKey());
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Condition was not met before timeout");
    }

    private static AppUser actor(Long id, String username, AppUser.Role role) {
        AppUser actor = new AppUser();
        actor.id = id;
        actor.username = username;
        actor.role = role;
        return actor;
    }

    private static RuntimeEmailAccount source(String id, Long userId, String ownerUsername) {
        return new RuntimeEmailAccount(
                id,
                "USER",
                userId,
                ownerUsername,
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                ownerUsername + "@example.com",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                Optional.of(id),
                new GmailApiDestinationTarget(
                        "target-" + userId,
                        userId,
                        ownerUsername,
                        UserMailDestinationConfigService.PROVIDER_GMAIL,
                        "me",
                        "client",
                        "secret",
                        "refresh",
                        "https://localhost",
                        true,
                        false,
                        false));
    }
}
