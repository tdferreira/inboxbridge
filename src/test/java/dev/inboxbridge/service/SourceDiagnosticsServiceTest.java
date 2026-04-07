package dev.inboxbridge.service;

import dev.inboxbridge.service.destination.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.dto.SourceDiagnosticsView;
import dev.inboxbridge.persistence.PollThrottleState;
import dev.inboxbridge.persistence.PollThrottleStateRepository;
import dev.inboxbridge.persistence.SourceImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpointRepository;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;

class SourceDiagnosticsServiceTest {

    @Test
    void viewByRuntimeAccountsIncludesCheckpointThrottleAndIdleDetails() {
        SourceDiagnosticsService service = service();
        RuntimeEmailAccount account = new RuntimeEmailAccount(
                "imap-source",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                java.util.Optional.of("INBOX, Projects/2026"),
                false,
                SourceFetchMode.IDLE,
                java.util.Optional.of("Projects"),
                SourcePostPollSettings.none(),
                new ImapAppendDestinationTarget(
                        "user-destination:7",
                        7L,
                        "alice",
                        "OUTLOOK_IMAP",
                        "outlook.office365.com",
                        993,
                        true,
                        InboxBridgeConfig.AuthMethod.OAUTH2,
                        InboxBridgeConfig.OAuthProvider.MICROSOFT,
                        "alice@example.com",
                        "",
                        "INBOX"));

        String destinationIdentity = DestinationIdentityKeys.forTarget(account.destination());
        InMemorySourcePollingStateRepository pollingStates = (InMemorySourcePollingStateRepository) service.sourcePollingStateRepository;
        SourcePollingState pollingState = new SourcePollingState();
        pollingState.sourceId = "imap-source";
        pollingState.imapCheckpointDestinationKey = destinationIdentity;
        pollingState.popCheckpointDestinationKey = destinationIdentity;
        pollingState.popLastSeenUidl = "ignored-for-imap";
        pollingState.updatedAt = Instant.parse("2026-04-06T10:05:00Z");
        pollingStates.persist(pollingState);

        InMemorySourceImapCheckpointRepository checkpoints = (InMemorySourceImapCheckpointRepository) service.sourceImapCheckpointRepository;
        checkpoints.persist(checkpoint("imap-source", destinationIdentity, "Projects/2026", 77L, 205L, "2026-04-06T10:02:00Z"));
        checkpoints.persist(checkpoint("imap-source", destinationIdentity, "INBOX", 44L, 101L, "2026-04-06T10:01:00Z"));

        InMemoryPollThrottleStateRepository throttles = (InMemoryPollThrottleStateRepository) service.pollThrottleStateRepository;
        throttles.persist(throttle("source-host:imap.example.com", "SOURCE_HOST", 3, "2026-04-06T10:06:00Z"));
        throttles.persist(throttle("destination-host:outlook.office365.com", "DESTINATION_HOST", 2, "2026-04-06T10:07:00Z"));

        ImapIdleHealthService idleHealth = service.imapIdleHealthService;
        Instant baseline = Instant.now().minus(ImapIdleHealthService.SCHEDULER_FALLBACK_THRESHOLD).minusSeconds(30);
        idleHealth.ensureTracked("imap-source", "imap-source\nINBOX", baseline);
        idleHealth.markConnected("imap-source", "imap-source\nINBOX", baseline.plusSeconds(5));
        idleHealth.markConnected("imap-source", "imap-source\nProjects/2026", baseline.plusSeconds(6));
        idleHealth.markDisconnected(
                "imap-source",
                "imap-source\nProjects/2026",
                baseline.minus(ImapIdleHealthService.SCHEDULER_FALLBACK_THRESHOLD).minusSeconds(10));

        Map<String, SourceDiagnosticsView> views = service.viewByRuntimeAccounts(List.of(account));

        SourceDiagnosticsView view = views.get("imap-source");
        assertNotNull(view);
        assertEquals(destinationIdentity, view.destinationIdentityKey());
        assertEquals(2, view.imapCheckpoints().size());
        assertEquals("INBOX", view.imapCheckpoints().getFirst().folderName());
        assertEquals("Projects/2026", view.imapCheckpoints().get(1).folderName());
        assertEquals(3, view.sourceThrottle().adaptiveMultiplier());
        assertEquals(2, view.destinationThrottle().adaptiveMultiplier());
        assertFalse(view.idleHealthy());
        assertTrue(view.idleSchedulerFallback());
        assertEquals(2, view.idleWatches().size());
        assertEquals("CONNECTED", view.idleWatches().getFirst().status());
        assertEquals("DISCONNECTED", view.idleWatches().get(1).status());
    }

    @Test
    void viewByRuntimeAccountsIncludesPopCheckpointForCurrentDestination() {
        SourceDiagnosticsService service = service();
        RuntimeEmailAccount account = new RuntimeEmailAccount(
                "pop-source",
                "USER",
                9L,
                "bob",
                true,
                InboxBridgeConfig.Protocol.POP3,
                "pop.example.com",
                995,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "bob@example.com",
                "secret",
                "",
                java.util.Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                java.util.Optional.of("POP"),
                SourcePostPollSettings.none(),
                new ImapAppendDestinationTarget(
                        "user-destination:9",
                        9L,
                        "bob",
                        "OUTLOOK_IMAP",
                        "outlook.office365.com",
                        993,
                        true,
                        InboxBridgeConfig.AuthMethod.PASSWORD,
                        InboxBridgeConfig.OAuthProvider.NONE,
                        "bob@example.com",
                        "secret",
                        "InboxBridge"));

        String destinationIdentity = DestinationIdentityKeys.forTarget(account.destination());
        InMemorySourcePollingStateRepository pollingStates = (InMemorySourcePollingStateRepository) service.sourcePollingStateRepository;
        SourcePollingState state = new SourcePollingState();
        state.sourceId = "pop-source";
        state.popCheckpointDestinationKey = destinationIdentity;
        state.popLastSeenUidl = "uidl-101";
        state.updatedAt = Instant.parse("2026-04-06T10:05:00Z");
        pollingStates.persist(state);

        SourceDiagnosticsView view = service.viewByRuntimeAccounts(List.of(account)).get("pop-source");

        assertNotNull(view);
        assertEquals(destinationIdentity, view.destinationIdentityKey());
        assertEquals("uidl-101", view.popLastSeenUidl());
        assertTrue(view.imapCheckpoints().isEmpty());
        assertTrue(view.idleWatches().isEmpty());
    }

    private SourceDiagnosticsService service() {
        SourceDiagnosticsService service = new SourceDiagnosticsService();
        service.sourcePollingStateRepository = new InMemorySourcePollingStateRepository();
        service.sourceImapCheckpointRepository = new InMemorySourceImapCheckpointRepository();
        service.pollThrottleStateRepository = new InMemoryPollThrottleStateRepository();
        service.imapIdleHealthService = new ImapIdleHealthService();
        return service;
    }

    private SourceImapCheckpoint checkpoint(
            String sourceId,
            String destinationKey,
            String folderName,
            Long uidValidity,
            Long lastSeenUid,
            String updatedAt) {
        SourceImapCheckpoint checkpoint = new SourceImapCheckpoint();
        checkpoint.sourceId = sourceId;
        checkpoint.destinationKey = destinationKey;
        checkpoint.folderName = folderName;
        checkpoint.uidValidity = uidValidity;
        checkpoint.lastSeenUid = lastSeenUid;
        checkpoint.updatedAt = Instant.parse(updatedAt);
        return checkpoint;
    }

    private PollThrottleState throttle(String throttleKey, String kind, int multiplier, String nextAllowedAt) {
        PollThrottleState state = new PollThrottleState();
        state.throttleKey = throttleKey;
        state.throttleKind = kind;
        state.adaptiveMultiplier = multiplier;
        state.nextAllowedAt = Instant.parse(nextAllowedAt);
        state.updatedAt = Instant.parse("2026-04-06T10:00:00Z");
        return state;
    }

    private static final class InMemorySourcePollingStateRepository extends SourcePollingStateRepository {
        private final List<SourcePollingState> states = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Map<String, SourcePollingState> findBySourceIds(List<String> sourceIds) {
            return states.stream()
                    .filter(state -> sourceIds.contains(state.sourceId))
                    .collect(java.util.stream.Collectors.toMap(state -> state.sourceId, state -> state));
        }

        @Override
        public void persist(SourcePollingState entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                states.add(entity);
            }
        }
    }

    private static final class InMemorySourceImapCheckpointRepository extends SourceImapCheckpointRepository {
        private final List<SourceImapCheckpoint> checkpoints = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public List<SourceImapCheckpoint> listBySourceIds(List<String> sourceIds) {
            return checkpoints.stream()
                    .filter(checkpoint -> sourceIds.contains(checkpoint.sourceId))
                    .toList();
        }

        @Override
        public void persist(SourceImapCheckpoint entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                checkpoints.add(entity);
            }
        }
    }

    private static final class InMemoryPollThrottleStateRepository extends PollThrottleStateRepository {
        private final List<PollThrottleState> states = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<PollThrottleState> findByThrottleKey(String throttleKey) {
            return states.stream().filter(state -> throttleKey.equals(state.throttleKey)).findFirst();
        }

        @Override
        public void persist(PollThrottleState entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                states.add(entity);
            }
        }
    }
}
