package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PollingLivePresentationServiceTest {

    private final PollingLivePresentationService service = new PollingLivePresentationService();

    @Test
    void buildViewShowsOnlyViewerOwnedSourcesAndActiveSource() {
        AppUser admin = actor(1L, "admin", AppUser.Role.ADMIN);
        PollingLiveRunState state = new PollingLiveRunState(
                "admin-ui",
                admin,
                List.of(source("alice-source", 7L, "alice"), source("bob-source", 8L, "bob")));

        state.queue.pollFirst();
        state.activeSourceIds.add("alice-source");
        state.sourcesById.get("alice-source").state = "RUNNING";

        var adminView = service.buildView(state, admin.id, true);
        var aliceView = service.buildView(state, 7L, false);
        var charlieView = service.buildView(state, 9L, false);

        assertEquals(2, adminView.sources().size());
        assertEquals(List.of("alice-source"), aliceView.sources().stream().map((source) -> source.sourceId()).toList());
        assertEquals("alice-source", aliceView.activeSourceId());
        assertFalse(aliceView.viewerCanControl());
        assertNull(charlieView);
    }

    @Test
    void sourceFinishedNotificationSkipsCooldownButReportsRealFailures() {
        AppUser alice = actor(7L, "alice", AppUser.Role.USER);
        PollingLiveRunState state = new PollingLiveRunState("user-ui", alice, List.of(source("alpha", 7L, "alice")));
        PollingLiveRunState.SourceState source = state.sourcesById.get("alpha");
        source.fetched = 5;
        source.imported = 3;
        source.duplicates = 2;

        source.error = "COOLDOWN";
        assertNull(service.notificationForSourceFinished(state, source));

        source.error = "boom";
        var failure = service.notificationForSourceFinished(state, source);
        assertNotNull(failure);
        assertEquals("notifications.livePollSourceFailed", failure.message().key());
        assertEquals("2", failure.message().params().get("duplicates"));

        source.error = null;
        var success = service.notificationForSourceFinished(state, source);
        assertNotNull(success);
        assertEquals("notifications.livePollSourceFinished", success.message().key());
        assertEquals("success", success.tone());
    }

    @Test
    void newSignInNotificationIncludesRecentSessionTargetAndOptionalLocation() {
        var defaultNotification = service.newSignInNotification(
                new SessionLocationAlertService.SessionLocationAssessment(null, false),
                PollingLiveService.SessionStreamKind.REMOTE,
                55L);
        assertEquals("notifications.newSessionDetected", defaultNotification.message().key());
        assertEquals("recent-session-REMOTE-55", defaultNotification.targetId());

        var unusualLocationNotification = service.newSignInNotification(
                new SessionLocationAlertService.SessionLocationAssessment("Berlin, DE", true),
                PollingLiveService.SessionStreamKind.BROWSER,
                11L);
        assertEquals("notifications.newSessionDetectedFromUnusualLocation", unusualLocationNotification.message().key());
        assertEquals("Berlin, DE", unusualLocationNotification.message().params().get("location"));
        assertTrue(unusualLocationNotification.targetId().contains("BROWSER-11"));
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
