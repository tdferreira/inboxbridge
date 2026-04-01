package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;

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
