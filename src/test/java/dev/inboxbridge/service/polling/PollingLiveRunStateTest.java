package dev.inboxbridge.service.polling;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PollingLiveRunStateTest {

    @Test
    void sourceStateTracksQueueAndActivePositions() {
        PollingLiveRunState state = new PollingLiveRunState(
                "user-ui",
                actor(7L, "alice", AppUser.Role.USER),
                List.of(source("alpha", 7L, "alice"), source("beta", 7L, "alice"), source("gamma", 7L, "alice")));

        PollingLiveRunState.SourceState alpha = state.sourcesById.get("alpha");
        PollingLiveRunState.SourceState beta = state.sourcesById.get("beta");
        PollingLiveRunState.SourceState gamma = state.sourcesById.get("gamma");

        assertEquals(1, alpha.position(state.queue, state.activeSourceIds));
        assertEquals(2, beta.position(state.queue, state.activeSourceIds));

        state.queue.remove("alpha");
        state.activeSourceIds.add("alpha");

        assertEquals(0, alpha.position(state.queue, state.activeSourceIds));
        assertEquals(1, beta.position(state.queue, state.activeSourceIds));
        assertEquals(2, gamma.position(state.queue, state.activeSourceIds));
    }

    @Test
    void sourceStateActionableDependsOnAdminOrRunOwner() {
        PollingLiveRunState state = new PollingLiveRunState(
                "user-ui",
                actor(7L, "alice", AppUser.Role.USER),
                List.of(source("alpha", 7L, "alice")));

        PollingLiveRunState.SourceState source = state.sourcesById.get("alpha");

        assertTrue(source.actionable(true, 99L, state.actorUserId));
        assertTrue(source.actionable(false, 7L, state.actorUserId));
        assertFalse(source.actionable(false, 8L, state.actorUserId));
    }

    @Test
    void sourceStateUsesCustomLabelAndProcessedMessagesMirrorFetchedCount() {
        PollingLiveRunState state = new PollingLiveRunState(
                "user-ui",
                actor(7L, "alice", AppUser.Role.USER),
                List.of(source("alpha", 7L, "alice", Optional.of("Inbox Alpha"))));

        PollingLiveRunState.SourceState source = state.sourcesById.get("alpha");
        source.fetched = 4;

        assertEquals("Inbox Alpha", source.label);
        assertEquals(4, source.processedMessages());
        assertTrue(source.isQueued());

        source.state = "COMPLETED";
        assertFalse(source.isQueued());
    }

    private static AppUser actor(Long id, String username, AppUser.Role role) {
        AppUser actor = new AppUser();
        actor.id = id;
        actor.username = username;
        actor.role = role;
        return actor;
    }

    private static RuntimeEmailAccount source(String id, Long userId, String ownerUsername) {
        return source(id, userId, ownerUsername, Optional.empty());
    }

    private static RuntimeEmailAccount source(String id, Long userId, String ownerUsername, Optional<String> customLabel) {
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
                customLabel,
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
