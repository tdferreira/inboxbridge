package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.dto.MailImportResponse;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PollingSourceExecutionServiceTest {

    @Test
    void executeReturnsLinkErrorWithoutFetchingWhenDestinationIsNotLinked() {
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService pollingStateService = new RecordingSourcePollingStateService();
        PollingSourceExecutionService service = service(
                mailSourceClient,
                new LinkedDestinationService(false),
                pollingStateService,
                new NoopSourcePollEventService(),
                null);

        PollingSourceExecutionService.SourceExecutionOutcome outcome = service.execute(
                runtimePopAccount(),
                "manual-api",
                settings(),
                null,
                null,
                "MY_INBOXBRIDGE");

        assertEquals(0, outcome.fetched());
        assertEquals(0, outcome.imported());
        assertEquals("gmail_account_not_linked", outcome.error().code());
        assertFalse(mailSourceClient.fetchCalled);
        assertTrue(pollingStateService.successSourceIds.isEmpty());
    }

    @Test
    void executeTreatsAlreadyImportedMessagesAsDuplicatesAndRecordsPopCheckpoint() {
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        mailSourceClient.messages = List.of(new FetchedMessage(
                "source-pop",
                "source-pop:uidl:message-1",
                Optional.of("<message-1@example.com>"),
                Instant.parse("2026-04-07T10:00:00Z"),
                Optional.empty(),
                null,
                null,
                "uidl-1",
                "raw-1".getBytes()));
        mailSourceClient.spamProbe = Optional.of(new MailSourceClient.MailboxCountProbe("Junk", 3));
        RecordingSourcePollingStateService pollingStateService = new RecordingSourcePollingStateService();
        RecordingImportDeduplicationService deduplicationService = new RecordingImportDeduplicationService(true);
        RecordingDestinationService destinationService = new RecordingDestinationService(true);
        PollingSourceExecutionService service = new PollingSourceExecutionService(
                mailSourceClient,
                deduplicationService,
                new SingleMailDestinationServices(destinationService),
                new NoopSourcePollEventService(),
                pollingStateService,
                null,
                null,
                null);

        PollingSourceExecutionService.SourceExecutionOutcome outcome = service.execute(
                runtimePopAccount(),
                "manual-api",
                settings(),
                null,
                "alice",
                "MY_INBOXBRIDGE");

        assertEquals(1, outcome.fetched());
        assertEquals(0, outcome.imported());
        assertEquals(1, outcome.duplicates());
        assertEquals(3, outcome.spamJunkMessageCount());
        assertEquals(List.of("source-pop -> Junk (3)"), outcome.spamJunkFolderSummaries());
        assertEquals(1, mailSourceClient.postPollApplied);
        assertEquals(List.of("uidl-1"), pollingStateService.recordedPopCheckpoints);
        assertEquals(List.of("source-pop"), pollingStateService.successSourceIds);
        assertEquals(0, destinationService.importCalls);
        assertEquals(0, deduplicationService.recordImportCalls);
    }

    private static PollingSourceExecutionService service(
            RecordingMailSourceClient mailSourceClient,
            MailDestinationService destinationService,
            RecordingSourcePollingStateService pollingStateService,
            SourcePollEventService sourcePollEventService,
            PollThrottleService pollThrottleService) {
        return new PollingSourceExecutionService(
                mailSourceClient,
                new RecordingImportDeduplicationService(false),
                new SingleMailDestinationServices(destinationService),
                sourcePollEventService,
                pollingStateService,
                pollThrottleService,
                null,
                null);
    }

    private static PollingSettingsService.EffectivePollingSettings settings() {
        return new PollingSettingsService.EffectivePollingSettings(true, "PT5M", Duration.ofMinutes(5), 25);
    }

    private static RuntimeEmailAccount runtimePopAccount() {
        return new RuntimeEmailAccount(
                "source-pop",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.POP3,
                "pop.example.test",
                995,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                destinationTarget());
    }

    private static ImapAppendDestinationTarget destinationTarget() {
        return new ImapAppendDestinationTarget(
                "destination-key",
                99L,
                "alice",
                "provider",
                "imap.destination.test",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "destination@example.test",
                "secret",
                "Imported");
    }

    private static final class RecordingMailSourceClient extends MailSourceClient {
        private List<FetchedMessage> messages = List.of();
        private Optional<MailboxCountProbe> spamProbe = Optional.empty();
        private boolean fetchCalled;
        private int postPollApplied;

        @Override
        public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            fetchCalled = true;
            return messages;
        }

        @Override
        public Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
            return spamProbe;
        }

        @Override
        public void applyPostPollSettings(RuntimeEmailAccount bridge, FetchedMessage message) {
            postPollApplied++;
        }
    }

    private static final class RecordingImportDeduplicationService extends ImportDeduplicationService {
        private final boolean alreadyImported;
        private int recordImportCalls;

        private RecordingImportDeduplicationService(boolean alreadyImported) {
            this.alreadyImported = alreadyImported;
        }

        @Override
        public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
            return alreadyImported;
        }

        @Override
        public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            recordImportCalls++;
        }
    }

    private static class LinkedDestinationService implements MailDestinationService {
        private final boolean linked;

        private LinkedDestinationService(boolean linked) {
            this.linked = linked;
        }

        @Override
        public boolean supports(MailDestinationTarget target) {
            return true;
        }

        @Override
        public boolean isLinked(MailDestinationTarget target) {
            return linked;
        }

        @Override
        public String notLinkedMessage(MailDestinationTarget target) {
            return "the destination mailbox is not linked";
        }

        @Override
        public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message) {
            return new MailImportResponse("destination-message", "thread-1");
        }
    }

    private static final class RecordingDestinationService extends LinkedDestinationService {
        private int importCalls;

        private RecordingDestinationService(boolean linked) {
            super(linked);
        }

        @Override
        public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message) {
            importCalls++;
            return super.importMessage(target, bridge, message);
        }
    }

    private static final class RecordingSourcePollingStateService extends SourcePollingStateService {
        private final List<String> successSourceIds = new java.util.ArrayList<>();
        private final List<String> recordedPopCheckpoints = new java.util.ArrayList<>();

        @Override
        public void recordSuccess(String sourceId, Instant finishedAt, PollingSettingsService.EffectivePollingSettings settings) {
            successSourceIds.add(sourceId);
        }

        @Override
        public void recordPopCheckpoint(String sourceId, String destinationKey, String uidl, Instant checkpointedAt) {
            recordedPopCheckpoints.add(uidl);
        }
    }

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(
                String sourceId,
                String trigger,
                Instant startedAt,
                Instant finishedAt,
                int fetched,
                int imported,
                long importedBytes,
                int duplicates,
                int spamJunkMessageCount,
                String actorUsername,
                String executionSurface,
                String error,
                PollDecisionSnapshot decisionSnapshot) {
        }
    }

    private static final class SingleMailDestinationServices implements Instance<MailDestinationService> {
        private final MailDestinationService service;

        private SingleMailDestinationServices(MailDestinationService service) {
            this.service = service;
        }

        @Override
        public MailDestinationService get() {
            return service;
        }

        @Override
        public Iterator<MailDestinationService> iterator() {
            return List.of(service).iterator();
        }

        @Override
        public Instance<MailDestinationService> select(java.lang.annotation.Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends MailDestinationService> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends MailDestinationService> Instance<U> select(TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return false;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(MailDestinationService instance) {
        }

        @Override
        public Handle<MailDestinationService> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<MailDestinationService>> handles() {
            throw new UnsupportedOperationException();
        }
    }
}
