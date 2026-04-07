package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.testsupport.ScopedLogCapture;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

class PollingServiceTest {

    @Test
    void scheduledPollDelegatesEveryTickToRuntimeEligibility() {
        RecordingPollingService service = new RecordingPollingService();

        service.scheduledPoll();
        service.scheduledPoll();

        assertEquals("scheduler", service.lastTrigger);
        assertEquals(2, service.invocations);
    }

    @Test
    void runPollUsesUserSpecificFetchWindowAndRecordsSuccess() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        RecordingSourcePollEventService sourcePollEventService = new RecordingSourcePollEventService();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService() {
            @Override
            public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
                return false;
            }

            @Override
            public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            }
        };
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = sourcePollEventService;
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(33, mailSourceClient.lastFetchWindow);
        assertEquals("user-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
        assertEquals(0, result.getErrors().size());
        assertEquals(4, result.getSpamJunkMessageCount());
        assertEquals(0L, result.getImportedBytes());
        assertEquals(4, sourcePollEventService.lastSpamJunkMessageCount);
        assertEquals(0L, sourcePollEventService.lastImportedBytes);
    }

    @Test
    void runPollRecordsPopUidlCheckpointAfterHandledMessages() {
        PollingService service = new PollingService();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        service.mailSourceClient = new RecordingMailSourceClient() {
            @Override
            public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
                lastFetchWindow = fetchWindow;
                return List.of(
                        new FetchedMessage(
                                bridge.id(),
                                bridge.id() + ":uidl:uidl-1",
                                java.util.Optional.of("<message-1@example.com>"),
                                Instant.parse("2026-03-31T10:00:00Z"),
                                java.util.Optional.empty(),
                                null,
                                null,
                                "uidl-1",
                                "raw-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        new FetchedMessage(
                                bridge.id(),
                                bridge.id() + ":uidl:uidl-2",
                                java.util.Optional.of("<message-2@example.com>"),
                                Instant.parse("2026-03-31T10:01:00Z"),
                                java.util.Optional.empty(),
                                null,
                                null,
                                "uidl-2",
                                "raw-2".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        };
        service.importDeduplicationService = new ImportDeduplicationService() {
            @Override
            public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
                return false;
            }

            @Override
            public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            }
        };
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(popBridge("legacy-pop", 7L, "alice", "target")));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(0, result.getErrors().size());
        assertEquals("legacy-pop", sourcePollingStateService.lastRecordedSuccessSourceId);
        assertEquals("uidl-2", sourcePollingStateService.lastRecordedPopCheckpoint);
        assertEquals(
                DestinationIdentityKeys.forTarget(popBridge("legacy-pop", 7L, "alice", "target").destination()),
                sourcePollingStateService.lastRecordedCheckpointDestinationKey);
    }

    @Test
    void runPollIgnoresSourcePollEventPersistenceFailures() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new ThrowingSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result;
        List<ScopedLogCapture.CapturedRecord> records;
        try (ScopedLogCapture capture = ScopedLogCapture.captureWarnings(PollingSourceExecutionService.class)) {
            result = service.runPoll("manual-api");
            records = capture.records();
        }

        assertEquals(0, result.getErrors().size());
        assertEquals("user-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.getFirst().level());
        assertEquals(
                "Unable to record source poll event for user-fetcher; continuing without persisted last-run history",
                records.getFirst().message());
        assertTrue(records.getFirst().thrown() instanceof IllegalStateException);
    }

    @Test
    void runPollReportsCooldownForManualTrigger() {
        PollingService service = new PollingService();
        service.mailSourceClient = new RecordingMailSourceClient();
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(systemBridge("env-fetcher")));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new CooldownSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("admin-ui");

        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().getFirst().contains("cooling down"));
    }

    @Test
    void runPollFailureEventIncludesCooldownAndThrottleDecisionAuditFields() {
        PollingService service = new PollingService();
        RecordingSourcePollEventService sourcePollEventService = new RecordingSourcePollEventService();
        RecordingFailureSourcePollingStateService sourcePollingStateService = new RecordingFailureSourcePollingStateService();
        RecordingPollThrottleService pollThrottleService = new RecordingPollThrottleService();
        service.mailSourceClient = new RecordingMailSourceClient() {
            @Override
            public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
                throw new IllegalStateException("429 too many requests");
            }
        };
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = sourcePollEventService;
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.pollThrottleService = pollThrottleService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result;
        List<ScopedLogCapture.CapturedRecord> records;
        try (ScopedLogCapture capture = ScopedLogCapture.captureWarnings(PollingSourceExecutionService.class)) {
            result = service.runPoll("manual-api");
            records = capture.records();
        }

        assertEquals(1, result.getErrors().size());
        assertEquals("RATE_LIMIT", sourcePollEventService.lastFailureCategory);
        assertEquals(15 * 60 * 1000L, sourcePollEventService.lastCooldownBackoffMillis);
        assertNotNull(sourcePollEventService.lastCooldownUntil);
        assertNotNull(sourcePollEventService.lastSourceThrottleMultiplierAfter);
        assertEquals(1, records.size());
        assertEquals(Level.SEVERE, records.getFirst().level());
        assertEquals("Source user-fetcher failed: 429 too many requests", records.getFirst().message());
        assertTrue(records.getFirst().thrown() instanceof IllegalStateException);
    }

    @Test
    void schedulerPollDoesNotStartALiveRunWhenAllSourcesAreStillWaitingForTheirInterval() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingPollingLiveService pollingLiveService = new RecordingPollingLiveService();
        service.mailSourceClient = mailSourceClient;
        service.pollingLiveService = pollingLiveService;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new SourcePollingStateService() {
            @Override
            public PollEligibility eligibility(
                    String sourceId,
                    PollingSettingsService.EffectivePollingSettings settings,
                    Instant now,
                    boolean ignoreInterval,
                    boolean ignoreCooldown) {
                return new PollEligibility(false, "INTERVAL", null);
            }
        };
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("scheduler");

        assertEquals(0, result.getFetched());
        assertEquals(0, result.getImported());
        assertEquals(0, result.getDuplicates());
        assertEquals(0, result.getErrorDetails().size());
        assertEquals(0, mailSourceClient.lastFetchWindow);
        assertEquals(0, pollingLiveService.startedRuns);
    }

    @Test
    void runPollSkipsSourcesWhoseEffectivePollingSettingsAreDisabled() {
        PollingService service = new PollingService();
        MultiSourceRecordingMailSourceClient mailSourceClient = new MultiSourceRecordingMailSourceClient();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService() {
            @Override
            public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
                return false;
            }

            @Override
            public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            }
        };
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(
                userBridge("enabled-fetcher", 7L, "alice", "alice-destination"),
                userBridge("disabled-fetcher", 7L, "alice", "alice-destination")));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new SourcePollingSettingsService() {
            @Override
            public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount bridge) {
                if ("disabled-fetcher".equals(bridge.id())) {
                    return new PollingSettingsService.EffectivePollingSettings(false, "5m", Duration.ofMinutes(5), 10);
                }
                return new PollingSettingsService.EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 10);
            }
        };
        SourcePollingStateService sourcePollingStateService = new SourcePollingStateService() {
            @Override
            public PollEligibility eligibility(
                    String sourceId,
                    PollingSettingsService.EffectivePollingSettings settings,
                    Instant now,
                    boolean ignoreInterval,
                    boolean ignoreCooldown) {
                if (!settings.pollEnabled()) {
                    return new PollEligibility(false, "DISABLED", null);
                }
                return new PollEligibility(true, "READY", null);
            }

            @Override
            public void recordSuccess(String sourceId, Instant finishedAt, PollingSettingsService.EffectivePollingSettings settings) {
            }
        };
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(1, result.getErrors().size());
        assertEquals(List.of("enabled-fetcher"), mailSourceClient.fetchedSourceIds);
    }

    @Test
    void runPollForSourceBypassesCooldownWhenExplicitlyTriggered() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        sourcePollingStateService.cooldownUntil = Instant.now().plus(Duration.ofMinutes(20));
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        RuntimeEmailAccount emailAccount = systemBridge("outlook-main");
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(emailAccount));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPollForSource(emailAccount, "manual-source");

        assertEquals(0, result.getErrors().size());
        assertEquals(10, mailSourceClient.lastFetchWindow);
        assertEquals("outlook-main", sourcePollingStateService.lastRecordedSuccessSourceId);
    }

    @Test
    void runPollForSourceUsesSourceSpecificFetchWindowOverride() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        RuntimeEmailAccount emailAccount = userBridge("user-fetcher", 7L, "alice", "alice-destination");
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(emailAccount));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 50);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(3), "3m", 25);
        service.sourcePollingSettingsService = new SourcePollingSettingsService() {
            @Override
            public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount bridge) {
                return new PollingSettingsService.EffectivePollingSettings(true, "1m", Duration.ofMinutes(1), 7);
            }
        };
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPollForSource(emailAccount, "manual-source");

        assertEquals(0, result.getErrors().size());
        assertEquals(7, mailSourceClient.lastFetchWindow);
    }

    @Test
    void schedulerPollSkipsIdleSources() {
        PollingService service = new PollingService();
        MultiSourceRecordingMailSourceClient mailSourceClient = new MultiSourceRecordingMailSourceClient();
        RecordingDestinationMailboxService destinationService = new RecordingDestinationMailboxService();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(
                userBridgeWithFetchMode("polling-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.POLLING),
                userBridgeWithFetchMode("idle-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.IDLE)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("scheduler");

        assertEquals(0, result.getErrors().size());
        assertEquals(List.of("polling-fetcher"), mailSourceClient.fetchedSourceIds);
        assertEquals(List.of("polling-fetcher->alice-destination"), destinationService.importRoutes);
    }

    @Test
    void schedulerPollFallsBackToIdleSourceWhenWatcherHasStayedUnhealthyLongEnough() {
        PollingService service = new PollingService();
        MultiSourceRecordingMailSourceClient mailSourceClient = new MultiSourceRecordingMailSourceClient();
        RecordingDestinationMailboxService destinationService = new RecordingDestinationMailboxService();
        ImapIdleHealthService imapIdleHealthService = new ImapIdleHealthService();
        Instant baseline = Instant.now().minus(ImapIdleHealthService.SCHEDULER_FALLBACK_THRESHOLD).minusSeconds(5);
        imapIdleHealthService.ensureTracked("polling-fetcher", baseline);
        imapIdleHealthService.ensureTracked("idle-fetcher", baseline);
        imapIdleHealthService.markConnected("polling-fetcher", baseline);
        imapIdleHealthService.markDisconnected("idle-fetcher", baseline);
        service.imapIdleHealthService = imapIdleHealthService;
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(
                userBridgeWithFetchMode("polling-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.POLLING),
                userBridgeWithFetchMode("idle-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.IDLE)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("scheduler");

        assertEquals(0, result.getErrors().size());
        assertEquals(List.of("idle-fetcher", "polling-fetcher"), mailSourceClient.fetchedSourceIds.stream().sorted().toList());
        assertEquals(
                List.of("idle-fetcher->alice-destination", "polling-fetcher->alice-destination"),
                destinationService.importRoutes.stream().sorted().toList());
    }

    @Test
    void schedulerPollStillSkipsRecentlyDisconnectedIdleSourcesDuringNormalReconnectWindow() {
        PollingService service = new PollingService();
        MultiSourceRecordingMailSourceClient mailSourceClient = new MultiSourceRecordingMailSourceClient();
        RecordingDestinationMailboxService destinationService = new RecordingDestinationMailboxService();
        ImapIdleHealthService imapIdleHealthService = new ImapIdleHealthService();
        Instant now = Instant.now();
        imapIdleHealthService.ensureTracked("polling-fetcher", now);
        imapIdleHealthService.ensureTracked("idle-fetcher", now);
        imapIdleHealthService.markConnected("polling-fetcher", now);
        imapIdleHealthService.markDisconnected("idle-fetcher", now.minusSeconds(10));
        service.imapIdleHealthService = imapIdleHealthService;
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(
                userBridgeWithFetchMode("polling-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.POLLING),
                userBridgeWithFetchMode("idle-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.IDLE)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("scheduler");

        assertEquals(0, result.getErrors().size());
        assertEquals(List.of("polling-fetcher"), mailSourceClient.fetchedSourceIds);
        assertEquals(List.of("polling-fetcher->alice-destination"), destinationService.importRoutes);
    }

    @Test
    void idleTriggeredRunPollsIdleSourceExplicitly() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        RuntimeEmailAccount emailAccount = userBridgeWithFetchMode("idle-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.IDLE);
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(emailAccount));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runIdleTriggeredPollForSource(emailAccount);

        assertEquals(0, result.getErrors().size());
        assertEquals(10, mailSourceClient.lastFetchWindow);
        assertEquals("idle-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
    }

    @Test
    void idleTriggeredRunCanProceedWhileSchedulerPollIsMarkedActive() throws Exception {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        RuntimeEmailAccount emailAccount = userBridgeWithFetchMode("idle-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.IDLE);
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(emailAccount));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        setRunning(service, true);
        setActivePoll(service, "scheduler", null, Instant.parse("2026-04-05T08:26:40Z"));
        try {
            PollRunResult result = service.runIdleTriggeredPollForSource(emailAccount);

            assertEquals(0, result.getErrors().size());
            assertEquals(10, mailSourceClient.lastFetchWindow);
            assertEquals("idle-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
        } finally {
            setActivePoll(service, null, null, null);
            setRunning(service, false);
        }
    }

    @Test
    void idleTriggeredRunStillReturnsBusyWhenAnotherManualPollIsActive() throws Exception {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RuntimeEmailAccount emailAccount = userBridgeWithFetchMode("idle-fetcher", 7L, "alice", "alice-destination", SourceFetchMode.IDLE);
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(emailAccount));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        setRunning(service, true);
        setActivePoll(service, "manual-source", "other-source", Instant.parse("2026-04-05T08:26:40Z"));
        try {
            PollRunResult result = service.runIdleTriggeredPollForSource(emailAccount);

            assertEquals(1, result.getErrorDetails().size());
            assertEquals("poll_busy", result.getErrorDetails().getFirst().code());
            assertEquals(0, mailSourceClient.lastFetchWindow);
        } finally {
            setActivePoll(service, null, null, null);
            setRunning(service, false);
        }
    }

    @Test
    void runPollPublishesLiveProgressAsMessagesAreProcessed() {
        PollingService service = new PollingService();
        MultiMessageMailSourceClient mailSourceClient = new MultiMessageMailSourceClient();
        RecordingPollingLiveService pollingLiveService = new RecordingPollingLiveService();
        service.mailSourceClient = mailSourceClient;
        service.pollingLiveService = pollingLiveService;
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(0, result.getErrors().size());
        assertEquals(List.of("2:10:0:0:0", "2:10:5:1:0", "2:10:10:2:0"), pollingLiveService.progressSnapshots);
    }

    @Test
    void runPollForSourceSkipsDisabledSources() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        RuntimeEmailAccount emailAccount = new RuntimeEmailAccount(
                "disabled-source",
                "SYSTEM",
                null,
                "system",
                false,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "secret",
                "",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.empty(),
                new GmailApiDestinationTarget(
                        "disabled-destination",
                        null,
                        "system",
                        UserMailDestinationConfigService.PROVIDER_GMAIL,
                        "me",
                        "client",
                        "secret",
                        "refresh",
                        "https://localhost",
                        true,
                        false,
                        false));
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(emailAccount));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 50);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(3), "3m", 25);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPollForSource(emailAccount, "manual-source");

        assertEquals(1, result.getErrorDetails().size());
        assertEquals("source_disabled", result.getErrorDetails().getFirst().code());
        assertEquals(0, mailSourceClient.lastFetchWindow);
    }

    @Test
    void runPollReportsClearErrorWhenGmailAccountIsNotLinked() {
        PollingService service = new PollingService();
        service.mailSourceClient = new RecordingMailSourceClient();
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(false));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingFailureSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(1, result.getErrors().size());
        assertEquals("gmail_account_not_linked", result.getErrorDetails().getFirst().code());
        assertTrue(result.getErrors().getFirst().contains("cannot run because"));
    }

    @Test
    void runPollForUserDoesNotRecordCooldownWhenDestinationMailboxIsMissing() {
        PollingService service = new PollingService();
        RecordingFailureSourcePollingStateService sourcePollingStateService = new RecordingFailureSourcePollingStateService();
        service.mailSourceClient = new RecordingMailSourceClient();
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(false));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPollForUser(userActor(7L), "user-ui");

        assertEquals(1, result.getErrorDetails().size());
        assertEquals("gmail_account_not_linked", result.getErrorDetails().getFirst().code());
        assertEquals(0, sourcePollingStateService.failureCalls);
    }

    @Test
    void runPollMapsMicrosoftOauthRevocationToStructuredError() {
        PollingService service = new PollingService();
        service.mailSourceClient = new RecordingMailSourceClient() {
            @Override
            public List<dev.inboxbridge.domain.FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
                throw new IllegalStateException(MicrosoftOAuthService.MICROSOFT_ACCESS_REVOKED_MESSAGE);
            }
        };
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(microsoftUserEmailAccount(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingFailureSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result;
        List<ScopedLogCapture.CapturedRecord> records;
        try (ScopedLogCapture capture = ScopedLogCapture.captureWarnings(PollingSourceExecutionService.class)) {
            result = service.runPoll("manual-api");
            records = capture.records();
        }

        assertEquals(1, result.getErrorDetails().size());
        assertEquals("microsoft_access_revoked", result.getErrorDetails().getFirst().code());
        assertEquals(1, records.size());
        assertEquals(Level.SEVERE, records.getFirst().level());
        assertEquals(
                "Source outlook-main failed: The linked Microsoft account no longer grants InboxBridge access. Reconnect it from this mail account.",
                records.getFirst().message());
        assertTrue(records.getFirst().thrown() instanceof IllegalStateException);
    }

    @Test
    void runPollForUserBypassesCooldownForBroadManualRuns() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        sourcePollingStateService.cooldownUntil = Instant.now().plus(Duration.ofMinutes(15));
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = 7L;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.USER;

        PollRunResult result = service.runPollForUser(actor, "user-ui");

        assertEquals(0, result.getErrorDetails().size());
        assertEquals(33, mailSourceClient.lastFetchWindow);
        assertEquals("user-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
    }

    @Test
    void runPollForAllUsersBypassesCooldownButStillHonorsManualRateLimits() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        sourcePollingStateService.cooldownUntil = Instant.now().plus(Duration.ofMinutes(15));
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10) {
            @Override
            public ManualPollRateLimit effectiveManualPollRateLimit() {
                return new ManualPollRateLimit(1, Duration.ofMinutes(1), 60);
            }
        };
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = 1L;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.ADMIN;

        PollRunResult first = service.runPollForAllUsers(actor, "admin-ui");
        PollRunResult second = service.runPollForAllUsers(actor, "admin-ui");

        assertEquals(0, first.getErrorDetails().size());
        assertEquals(33, mailSourceClient.lastFetchWindow);
        assertEquals("user-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
        assertEquals(1, second.getErrorDetails().size());
        assertEquals("manual_poll_rate_limited", second.getErrorDetails().getFirst().code());
    }

    @Test
    void runPollKeepsUserMailboxesIsolatedAcrossUserScopedAndAllUsersRuns() {
        PollingService service = new PollingService();
        MultiSourceRecordingMailSourceClient mailSourceClient = new MultiSourceRecordingMailSourceClient();
        RecordingDestinationMailboxService destinationService = new RecordingDestinationMailboxService();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService() {
            @Override
            public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
                return false;
            }

            @Override
            public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            }
        };
        service.mailDestinationServices = new FakeMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(
                userBridge("alice-source-a", 7L, "alice", "alice-destination"),
                userBridge("alice-source-b", 7L, "alice", "alice-destination"),
                userBridge("bob-source-a", 8L, "bob", "bob-destination"),
                userBridge("bob-source-b", 8L, "bob", "bob-destination")));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult userRun = service.runPollForUser(userActor(7L), "user-ui");

        assertEquals(0, userRun.getErrors().size());
        assertEquals(
                List.of("alice-source-a", "alice-source-b"),
                mailSourceClient.fetchedSourceIds.stream().sorted().toList());
        assertEquals(
                List.of("alice-source-a->alice-destination", "alice-source-b->alice-destination"),
                destinationService.importRoutes.stream().sorted().toList());

        mailSourceClient.fetchedSourceIds.clear();
        destinationService.importRoutes.clear();

        dev.inboxbridge.persistence.AppUser admin = new dev.inboxbridge.persistence.AppUser();
        admin.id = 1L;
        admin.role = dev.inboxbridge.persistence.AppUser.Role.ADMIN;

        PollRunResult allUsersRun = service.runPollForAllUsers(admin, "admin-ui");

        assertEquals(0, allUsersRun.getErrors().size());
        assertEquals(
                List.of("alice-source-a", "alice-source-b", "bob-source-a", "bob-source-b"),
                mailSourceClient.fetchedSourceIds.stream().sorted().toList());
        assertEquals(
                List.of(
                        "alice-source-a->alice-destination",
                        "alice-source-b->alice-destination",
                        "bob-source-a->bob-destination",
                        "bob-source-b->bob-destination"),
                destinationService.importRoutes.stream().sorted().toList());
        assertTrue(destinationService.importRoutes.stream().noneMatch((route) ->
                route.contains("alice-source") && route.contains("bob-destination")));
        assertTrue(destinationService.importRoutes.stream().noneMatch((route) ->
                route.contains("bob-source") && route.contains("alice-destination")));
    }

    @Test
    void runPollStartsIndependentSourcesInParallel() throws Exception {
        PollingService service = new PollingService();
        ParallelStartMailSourceClient mailSourceClient = new ParallelStartMailSourceClient(2);
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(
                userBridge("alpha-source", 7L, "alice", "alice-destination"),
                userBridge("beta-source", 7L, "alice", "alice-destination")));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        AtomicReference<PollRunResult> resultRef = new AtomicReference<>();
        Thread pollThread = new Thread(() -> resultRef.set(service.runPoll("parallel-test")));
        pollThread.start();

        assertTrue(mailSourceClient.allFetchesStarted.await(2, TimeUnit.SECONDS));
        mailSourceClient.allowFetchesToFinish.countDown();
        pollThread.join(2000L);

        assertEquals(0, resultRef.get().getErrorDetails().size());
        assertEquals(List.of("alpha-source", "beta-source"), mailSourceClient.startedSourceIds.stream().sorted().toList());
    }

    @Test
    void runPollForUserRateLimitsRepeatedManualRuns() {
        PollingService service = new PollingService();
        service.mailSourceClient = new RecordingMailSourceClient();
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10) {
            @Override
            public ManualPollRateLimit effectiveManualPollRateLimit() {
                return new ManualPollRateLimit(1, Duration.ofMinutes(1), 60);
            }
        };
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = 7L;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.USER;

        PollRunResult first = service.runPollForUser(actor, "user-ui");
        PollRunResult second = service.runPollForUser(actor, "user-ui");

        assertEquals(0, first.getErrors().size());
        assertEquals(1, second.getErrorDetails().size());
        assertEquals("manual_poll_rate_limited", second.getErrorDetails().getFirst().code());
    }

    @Test
    void runPollAppliesSourceHostAndDestinationProviderThrottles() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient() {
            @Override
            public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
                lastFetchWindow = fetchWindow;
                return List.of(new FetchedMessage(
                        bridge.id(),
                        bridge.id() + ":message-1",
                        java.util.Optional.of("<message-1@example.com>"),
                        Instant.parse("2026-03-27T10:00:00Z"),
                        "raw".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        };
        RecordingPollThrottleService pollThrottleService = new RecordingPollThrottleService();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService() {
            @Override
            public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
                return false;
            }

            @Override
            public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            }
        };
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        RecordingSourcePollingStateService sourcePollingStateService = new RecordingSourcePollingStateService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();
        service.pollThrottleService = pollThrottleService;

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(0, result.getErrors().size());
        assertEquals("user-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
        assertEquals(List.of("imap.example.com"), pollThrottleService.sourceHosts);
        assertEquals(List.of(UserMailDestinationConfigService.PROVIDER_GMAIL), pollThrottleService.destinationProviders);
    }

    @Test
    void runPollAppliesPostPollSourceActionsForImportedAndDuplicateMessages() {
        PollingService service = new PollingService();
        RecordingMailSourceClient mailSourceClient = new RecordingMailSourceClient() {
            @Override
            public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
                lastFetchWindow = fetchWindow;
                return List.of(
                        new FetchedMessage(
                                bridge.id(),
                                bridge.id() + ":imap-uid:44:1",
                                java.util.Optional.of("<imported@example.com>"),
                                Instant.parse("2026-03-27T10:00:00Z"),
                                "raw-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        new FetchedMessage(
                                bridge.id(),
                                bridge.id() + ":imap-uid:44:2",
                                java.util.Optional.of("<duplicate@example.com>"),
                                Instant.parse("2026-03-27T10:01:00Z"),
                                "raw-2".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        };
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService() {
            @Override
            public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
                return fetchedMessage.sourceMessageKey().endsWith(":2");
            }

            @Override
            public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
            }
        };
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridgeWithPostPollAction()));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(0, result.getErrors().size());
        assertEquals(List.of("user-fetcher:imap-uid:44:1", "user-fetcher:imap-uid:44:2"), mailSourceClient.postPollAppliedMessageKeys);
    }

    @Test
    void busyPollMessageIncludesCurrentSourceWhenSingleSourcePollIsActive() throws Exception {
        PollingService service = new PollingService();
        setActivePoll(service, "app-fetcher", "outlook-main", Instant.parse("2026-03-27T09:56:56Z"));

        java.lang.reflect.Method method = PollingService.class.getDeclaredMethod("currentBusyMessage");
        method.setAccessible(true);
        String message = (String) method.invoke(service);

        assertTrue(message.contains("outlook-main"));
        assertTrue(message.contains("app-fetcher"));
    }

    @Test
    void livePauseWaitsDuringActiveSourceUntilResume() throws Exception {
        PollingService service = new PollingService();
        BlockingDestinationService destinationService = new BlockingDestinationService();
        destinationService.blockFirstImport = true;
        service.mailSourceClient = new MultiMessageMailSourceClient();
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();
        service.pollingLiveService = new PollingLiveService();

        dev.inboxbridge.persistence.AppUser actor = userActor(7L);
        AtomicReference<PollRunResult> resultRef = new AtomicReference<>();
        Thread pollThread = new Thread(() -> resultRef.set(service.runPollForUser(actor, "user-ui")));

        pollThread.start();
        assertTrue(destinationService.firstImportStarted.await(2, TimeUnit.SECONDS));
        assertTrue(service.pollingLiveService.requestPause(actor));

        destinationService.allowFirstImportToFinish.countDown();
        waitFor(() -> "PAUSED".equals(service.pollingLiveService.snapshotFor(actor).state()));
        assertEquals(1, destinationService.importCount.get());
        assertEquals(1L, destinationService.secondImportStarted.getCount());

        assertTrue(service.pollingLiveService.requestResume(actor));
        assertTrue(destinationService.secondImportStarted.await(2, TimeUnit.SECONDS));

        pollThread.join(2000L);
        assertEquals(2, destinationService.importCount.get());
        assertEquals(0, resultRef.get().getErrorDetails().size());
    }

    @Test
    void liveStopHaltsRemainingMessagesOfActiveSource() throws Exception {
        PollingService service = new PollingService();
        BlockingDestinationService destinationService = new BlockingDestinationService();
        destinationService.blockFirstImport = true;
        service.mailSourceClient = new MultiMessageMailSourceClient();
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(destinationService);
        RecordingSourcePollEventService sourcePollEventService = new RecordingSourcePollEventService();
        service.sourcePollEventService = sourcePollEventService;
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();
        service.pollingLiveService = new PollingLiveService();

        dev.inboxbridge.persistence.AppUser actor = userActor(7L);
        AtomicReference<PollRunResult> resultRef = new AtomicReference<>();
        Thread pollThread = new Thread(() -> resultRef.set(service.runPollForUser(actor, "user-ui")));

        pollThread.start();
        assertTrue(destinationService.firstImportStarted.await(2, TimeUnit.SECONDS));
        assertTrue(service.pollingLiveService.requestStop(actor));

        destinationService.allowFirstImportToFinish.countDown();
        pollThread.join(2000L);

        assertEquals(1, destinationService.importCount.get());
        assertEquals("Stopped by user.", sourcePollEventService.lastError);
        assertEquals("STOPPED", sourcePollEventService.lastStatus);
        assertEquals(0, resultRef.get().getErrorDetails().size());
    }

    @Test
    void liveStopCancelsBlockingFetchOperations() throws Exception {
        PollingService service = new PollingService();
        BlockingCancelableMailSourceClient mailSourceClient = new BlockingCancelableMailSourceClient();
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new NeverDuplicateImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        RecordingSourcePollEventService sourcePollEventService = new RecordingSourcePollEventService();
        service.sourcePollEventService = sourcePollEventService;
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new RecordingSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();
        service.pollingLiveService = new PollingLiveService();
        service.pollCancellationService = new PollCancellationService();
        mailSourceClient.pollCancellationService = service.pollCancellationService;

        dev.inboxbridge.persistence.AppUser actor = userActor(7L);
        AtomicReference<PollRunResult> resultRef = new AtomicReference<>();
        Thread pollThread = new Thread(() -> resultRef.set(service.runPollForUser(actor, "user-ui")));

        pollThread.start();
        assertTrue(mailSourceClient.fetchStarted.await(2, TimeUnit.SECONDS));
        assertTrue(service.pollingLiveService.requestStop(actor));

        pollThread.join(2000L);

        assertTrue(mailSourceClient.cancelled.get());
        assertEquals("Stopped by user.", sourcePollEventService.lastError);
        assertEquals("STOPPED", sourcePollEventService.lastStatus);
        assertEquals(0, resultRef.get().getErrorDetails().size());
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Condition was not met before timeout");
    }

    private static RuntimeEmailAccount userBridge(Long userId) {
        return userBridge("user-fetcher", userId, "alice", "target");
    }

    private static RuntimeEmailAccount userBridge(String sourceId, Long userId, String ownerUsername, String destinationSubjectKey) {
        return userBridgeWithFetchMode(sourceId, userId, ownerUsername, destinationSubjectKey, SourceFetchMode.POLLING);
    }

    private static RuntimeEmailAccount userBridgeWithFetchMode(
            String sourceId,
            Long userId,
            String ownerUsername,
            String destinationSubjectKey,
            SourceFetchMode fetchMode) {
        return new RuntimeEmailAccount(
                sourceId,
                "USER",
                userId,
                ownerUsername,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                java.util.Optional.of("INBOX"),
                false,
                fetchMode,
                java.util.Optional.of("Imported/Test"),
                new GmailApiDestinationTarget(
                        destinationSubjectKey,
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

    private static RuntimeEmailAccount systemBridge(String sourceId) {
        return new RuntimeEmailAccount(
                sourceId,
                "SYSTEM",
                null,
                "system",
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.of("Imported/Test"),
                new GmailApiDestinationTarget("target", null, "system", UserMailDestinationConfigService.PROVIDER_GMAIL, "me", "client", "secret", "refresh", "https://localhost", true, false, false));
    }

    private static RuntimeEmailAccount popBridge(String sourceId, Long userId, String ownerUsername, String destinationSubjectKey) {
        return new RuntimeEmailAccount(
                sourceId,
                "USER",
                userId,
                ownerUsername,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.POP3,
                "pop.example.com",
                995,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                java.util.Optional.empty(),
                false,
                java.util.Optional.of("Imported/Test"),
                new GmailApiDestinationTarget(
                        destinationSubjectKey,
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

    private static RuntimeEmailAccount microsoftUserEmailAccount(Long userId) {
        return new RuntimeEmailAccount(
                "outlook-main",
                "USER",
                userId,
                "alice",
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                "outlook.office365.com",
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.OAUTH2,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.MICROSOFT,
                "user@example.com",
                "",
                "refresh-token",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.empty(),
                new GmailApiDestinationTarget("target", userId, "alice", UserMailDestinationConfigService.PROVIDER_GMAIL, "me", "client", "secret", "refresh", "https://localhost", true, false, false));
    }

    private static RuntimeEmailAccount userBridgeWithPostPollAction() {
        return new RuntimeEmailAccount(
                "user-fetcher",
                "USER",
                7L,
                "alice",
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.of("Imported/Test"),
                new dev.inboxbridge.domain.SourcePostPollSettings(
                        true,
                        dev.inboxbridge.domain.SourcePostPollAction.MOVE,
                        java.util.Optional.of("Archive")),
                new GmailApiDestinationTarget("target", 7L, "alice", UserMailDestinationConfigService.PROVIDER_GMAIL, "me", "client", "secret", "refresh", "https://localhost", true, false, false));
    }

    private static dev.inboxbridge.persistence.AppUser userActor(Long userId) {
        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = userId;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.USER;
        return actor;
    }

    private static final class RecordingPollingService extends PollingService {
        private String lastTrigger;
        private int invocations;

        @Override
        public dev.inboxbridge.dto.PollRunResult runPoll(String trigger) {
            lastTrigger = trigger;
            invocations++;
            return new dev.inboxbridge.dto.PollRunResult();
        }
    }

    private static class RecordingMailSourceClient extends MailSourceClient {
        protected int lastFetchWindow;
        protected final java.util.List<String> postPollAppliedMessageKeys = new java.util.ArrayList<>();

        @Override
        public List<dev.inboxbridge.domain.FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            lastFetchWindow = fetchWindow;
            return List.of();
        }

        @Override
        public java.util.Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
            return java.util.Optional.of(new MailboxCountProbe("Spam", 4));
        }

        @Override
        public void applyPostPollSettings(RuntimeEmailAccount bridge, FetchedMessage message) {
            if (bridge.postPollSettings().hasAnyAction()) {
                postPollAppliedMessageKeys.add(message.sourceMessageKey());
            }
        }
    }

    private static final class MultiSourceRecordingMailSourceClient extends RecordingMailSourceClient {
        private final java.util.List<String> fetchedSourceIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public List<dev.inboxbridge.domain.FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            lastFetchWindow = fetchWindow;
            fetchedSourceIds.add(bridge.id());
            return List.of(new FetchedMessage(
                    bridge.id(),
                    bridge.id() + ":message-1",
                    java.util.Optional.of("<" + bridge.id() + "@example.com>"),
                    Instant.parse("2026-03-31T10:00:00Z"),
                    bridge.id().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    private static final class RecordingPollThrottleService extends PollThrottleService {
        private final java.util.List<String> sourceHosts = new java.util.ArrayList<>();
        private final java.util.List<String> destinationProviders = new java.util.ArrayList<>();

        @Override
        public ThrottleLease acquireSourceMailboxPermit(RuntimeEmailAccount emailAccount) {
            sourceHosts.add(emailAccount.host());
            return ThrottleLease.noopLease();
        }

        @Override
        public ThrottleLease acquireDestinationDeliveryPermit(MailDestinationTarget target) {
            destinationProviders.add(target.providerId());
            return ThrottleLease.noopLease();
        }

        @Override
        public ThrottleAudit recordSourceSuccess(RuntimeEmailAccount emailAccount) {
            return null;
        }

        @Override
        public ThrottleAudit recordSourceFailure(RuntimeEmailAccount emailAccount, String errorMessage) {
            return new ThrottleAudit("source-host:" + emailAccount.host(), "SOURCE_HOST", "RATE_LIMIT", 1, 2, Duration.ofSeconds(2), Instant.now().plusSeconds(2));
        }

        @Override
        public ThrottleAudit recordDestinationSuccess(MailDestinationTarget target) {
            return null;
        }

        @Override
        public ThrottleAudit recordDestinationFailure(MailDestinationTarget target, String errorMessage) {
            return null;
        }
    }

    private static final class MultiMessageMailSourceClient extends RecordingMailSourceClient {
        @Override
        public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            lastFetchWindow = fetchWindow;
            return List.of(
                    new FetchedMessage(
                            bridge.id(),
                            bridge.id() + ":message-1",
                            java.util.Optional.of("<message-1@example.com>"),
                            Instant.parse("2026-03-31T10:00:00Z"),
                            "raw-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    new FetchedMessage(
                            bridge.id(),
                            bridge.id() + ":message-2",
                            java.util.Optional.of("<message-2@example.com>"),
                            Instant.parse("2026-03-31T10:01:00Z"),
                            "raw-2".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    private static final class ParallelStartMailSourceClient extends RecordingMailSourceClient {
        private final CountDownLatch allFetchesStarted;
        private final CountDownLatch allowFetchesToFinish = new CountDownLatch(1);
        private final List<String> startedSourceIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        private ParallelStartMailSourceClient(int expectedSources) {
            this.allFetchesStarted = new CountDownLatch(expectedSources);
        }

        @Override
        public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            startedSourceIds.add(bridge.id());
            allFetchesStarted.countDown();
            try {
                assertTrue(allowFetchesToFinish.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Fetch was interrupted", interrupted);
            }
            return List.of();
        }
    }

    private static final class BlockingCancelableMailSourceClient extends RecordingMailSourceClient {
        private final CountDownLatch fetchStarted = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private PollCancellationService pollCancellationService;

        @Override
        public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            pollCancellationService.register(() -> cancelled.set(true));
            fetchStarted.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!cancelled.get() && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    cancelled.set(true);
                    throw new IllegalStateException("Fetch interrupted during cancellation", interrupted);
                }
            }
            if (cancelled.get()) {
                throw new IllegalStateException("Fetch cancelled by stop request");
            }
            throw new IllegalStateException("Fetch cancellation was not triggered before timeout");
        }
    }

    private static final class NeverDuplicateImportDeduplicationService extends ImportDeduplicationService {
        @Override
        public boolean alreadyImported(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget) {
            return false;
        }

        @Override
        public void recordImport(FetchedMessage fetchedMessage, MailDestinationTarget destinationTarget, MailImportResponse response) {
        }
    }

    private static class FakePollingSettingsService extends PollingSettingsService {
        private final EffectivePollingSettings settings;

        private FakePollingSettingsService(boolean enabled, Duration interval, String text, int fetchWindow) {
            this.settings = new EffectivePollingSettings(enabled, text, interval, fetchWindow);
        }

        @Override
        public EffectivePollingSettings effectiveSettings() {
            return settings;
        }

        @Override
        public ManualPollRateLimit effectiveManualPollRateLimit() {
            return new ManualPollRateLimit(5, Duration.ofMinutes(1), 60);
        }
    }

    private static final class FakeUserPollingSettingsService extends UserPollingSettingsService {
        private final PollingSettingsService.EffectivePollingSettings settings;

        private FakeUserPollingSettingsService(boolean enabled, Duration interval, String text, int fetchWindow) {
            this.settings = new PollingSettingsService.EffectivePollingSettings(enabled, text, interval, fetchWindow);
        }

        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsForUser(Long userId) {
            return settings;
        }
    }

    private static final class RecordingSourcePollingStateService extends SourcePollingStateService {
        private String lastRecordedSuccessSourceId;
        private String lastRecordedPopCheckpoint;
        private String lastRecordedCheckpointDestinationKey;
        private Instant cooldownUntil;

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval) {
            if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
                return new PollEligibility(false, "COOLDOWN", new dev.inboxbridge.dto.SourcePollingStateView(
                        null,
                        cooldownUntil,
                        1,
                        "auth failure",
                        now,
                        null));
            }
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval, boolean ignoreCooldown) {
            if (!ignoreCooldown && cooldownUntil != null && now.isBefore(cooldownUntil)) {
                return new PollEligibility(false, "COOLDOWN", new dev.inboxbridge.dto.SourcePollingStateView(
                        null,
                        cooldownUntil,
                        1,
                        "auth failure",
                        now,
                        null));
            }
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public void recordSuccess(String sourceId, Instant finishedAt, PollingSettingsService.EffectivePollingSettings settings) {
            lastRecordedSuccessSourceId = sourceId;
        }

        @Override
        public void recordPopCheckpoint(String sourceId, String destinationKey, String uidl, Instant observedAt) {
            lastRecordedCheckpointDestinationKey = destinationKey;
            lastRecordedPopCheckpoint = uidl;
        }
    }

    private static final class FakeSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount bridge) {
            if ("user-fetcher".equals(bridge.id())) {
                return new PollingSettingsService.EffectivePollingSettings(true, "2m", Duration.ofMinutes(2), 33);
            }
            return new PollingSettingsService.EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 10);
        }
    }

    private static final class CooldownSourcePollingStateService extends SourcePollingStateService {
        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval) {
            return new PollEligibility(false, "COOLDOWN", new dev.inboxbridge.dto.SourcePollingStateView(
                    Instant.parse("2026-03-26T12:30:00Z"),
                    Instant.parse("2026-03-26T12:30:00Z"),
                    2,
                    "429 too many requests",
                    Instant.parse("2026-03-26T12:00:00Z"),
                    null));
        }

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval, boolean ignoreCooldown) {
            if (ignoreCooldown) {
                return new PollEligibility(true, "READY", null);
            }
            return eligibility(sourceId, settings, now, ignoreInterval);
        }
    }

    private static final class FakeRuntimeEmailAccountService extends RuntimeEmailAccountService {
        private final List<RuntimeEmailAccount> emailAccounts;

        private FakeRuntimeEmailAccountService(List<RuntimeEmailAccount> emailAccounts) {
            this.emailAccounts = emailAccounts;
        }

        @Override
        public List<RuntimeEmailAccount> listEnabledForPolling() {
            return emailAccounts;
        }

        @Override
        public List<RuntimeEmailAccount> listEnabledForUser(dev.inboxbridge.persistence.AppUser actor) {
            return emailAccounts.stream()
                    .filter(emailAccount -> actor != null && actor.id != null && actor.id.equals(emailAccount.ownerUserId()))
                    .toList();
        }
    }

    private static final class RecordingFailureSourcePollingStateService extends SourcePollingStateService {
        private int failureCalls;

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval) {
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval, boolean ignoreCooldown) {
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public CooldownDecision recordFailure(String sourceId, Instant finishedAt, String failureReason) {
            failureCalls++;
            return new CooldownDecision("RATE_LIMIT", failureCalls, Duration.ofMinutes(15), finishedAt.plus(Duration.ofMinutes(15)));
        }
    }

    private static final class FakeMailDestinationService implements MailDestinationService {
        private final boolean linked;

        private FakeMailDestinationService(boolean linked) {
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
            return GmailApiMailDestinationService.GMAIL_ACCOUNT_NOT_LINKED_MESSAGE;
        }

        @Override
        public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, dev.inboxbridge.domain.FetchedMessage message) {
            return new MailImportResponse("imported-1", null);
        }
    }

    private static final class RecordingDestinationMailboxService implements MailDestinationService {
        private final java.util.List<String> importRoutes = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public boolean supports(MailDestinationTarget target) {
            return true;
        }

        @Override
        public boolean isLinked(MailDestinationTarget target) {
            return true;
        }

        @Override
        public String notLinkedMessage(MailDestinationTarget target) {
            return GmailApiMailDestinationService.GMAIL_ACCOUNT_NOT_LINKED_MESSAGE;
        }

        @Override
        public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message) {
            importRoutes.add(bridge.id() + "->" + target.subjectKey());
            return new MailImportResponse(bridge.id() + ":imported", null);
        }
    }

    private static final class BlockingDestinationService implements MailDestinationService {
        private final CountDownLatch firstImportStarted = new CountDownLatch(1);
        private final CountDownLatch allowFirstImportToFinish = new CountDownLatch(1);
        private final CountDownLatch secondImportStarted = new CountDownLatch(1);
        private final AtomicInteger importCount = new AtomicInteger();
        private volatile boolean blockFirstImport;

        @Override
        public boolean supports(MailDestinationTarget target) {
            return true;
        }

        @Override
        public boolean isLinked(MailDestinationTarget target) {
            return true;
        }

        @Override
        public String notLinkedMessage(MailDestinationTarget target) {
            return GmailApiMailDestinationService.GMAIL_ACCOUNT_NOT_LINKED_MESSAGE;
        }

        @Override
        public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message) {
            int attempt = importCount.incrementAndGet();
            if (attempt == 1) {
                firstImportStarted.countDown();
                if (blockFirstImport) {
                    try {
                        allowFirstImportToFinish.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
            } else if (attempt == 2) {
                secondImportStarted.countDown();
            }
            return new MailImportResponse(bridge.id() + ":imported:" + attempt, null);
        }
    }

    private static final class FakeMailDestinationServices implements Instance<MailDestinationService> {
        private final List<MailDestinationService> services;

        private FakeMailDestinationServices(MailDestinationService... services) {
            this.services = List.of(services);
        }

        @Override
        public MailDestinationService get() {
            return services.getFirst();
        }

        @Override
        public Iterator<MailDestinationService> iterator() {
            return services.iterator();
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
            return services.isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return services.size() > 1;
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

    private static void setRunning(PollingService service, boolean value) throws Exception {
        java.lang.reflect.Field runningField = PollingService.class.getDeclaredField("running");
        runningField.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean running =
                (java.util.concurrent.atomic.AtomicBoolean) runningField.get(service);
        running.set(value);
    }

    private static void setActivePoll(PollingService service, String trigger, String sourceId, Instant startedAt) throws Exception {
        java.lang.reflect.Field activePollField = PollingService.class.getDeclaredField("activePoll");
        activePollField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Object> activePoll =
                (java.util.concurrent.atomic.AtomicReference<Object>) activePollField.get(service);
        if (trigger == null) {
            activePoll.set(null);
            return;
        }
        java.lang.reflect.Constructor<?> constructor = Class
                .forName("dev.inboxbridge.service.polling.PollingService$ActivePoll")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        activePoll.set(constructor.newInstance(trigger, sourceId, startedAt));
    }

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, long importedBytes, int duplicates, int spamJunkMessageCount, String actorUsername, String executionSurface, String error, PollDecisionSnapshot decisionSnapshot) {
        }
    }

    private static final class RecordingSourcePollEventService extends SourcePollEventService {
        private String lastError;
        private long lastImportedBytes;
        private int lastSpamJunkMessageCount;
        private String lastStatus;
        private String lastFailureCategory;
        private Long lastCooldownBackoffMillis;
        private Instant lastCooldownUntil;
        private Integer lastSourceThrottleMultiplierAfter;

        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, long importedBytes, int duplicates, int spamJunkMessageCount, String actorUsername, String executionSurface, String error, PollDecisionSnapshot decisionSnapshot) {
            lastError = error;
            lastImportedBytes = importedBytes;
            lastSpamJunkMessageCount = spamJunkMessageCount;
            lastStatus = error == null ? "SUCCESS" : "Stopped by user.".equals(error) ? "STOPPED" : "ERROR";
            lastFailureCategory = decisionSnapshot == null ? null : decisionSnapshot.failureCategory();
            lastCooldownBackoffMillis = decisionSnapshot == null ? null : decisionSnapshot.cooldownBackoffMillis();
            lastCooldownUntil = decisionSnapshot == null ? null : decisionSnapshot.cooldownUntil();
            lastSourceThrottleMultiplierAfter = decisionSnapshot == null ? null : decisionSnapshot.sourceThrottleMultiplierAfter();
        }
    }

    private static final class ThrowingSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, long importedBytes, int duplicates, int spamJunkMessageCount, String actorUsername, String executionSurface, String error, PollDecisionSnapshot decisionSnapshot) {
            throw new IllegalStateException("simulated source poll event persistence failure");
        }
    }

    private static final class RecordingPollingLiveService extends PollingLiveService {
        private final java.util.List<String> progressSnapshots = new java.util.ArrayList<>();
        private int startedRuns;

        @Override
        public PollRunHandle startRun(String trigger, List<RuntimeEmailAccount> emailAccounts, dev.inboxbridge.persistence.AppUser actor) {
            startedRuns++;
            return super.startRun(trigger, emailAccounts, actor);
        }

        @Override
        public void updateSourceProgress(String runId, String sourceId, int totalMessages, long totalBytes, long processedBytes, int fetched, int imported, int duplicates) {
            progressSnapshots.add(totalMessages + ":" + totalBytes + ":" + processedBytes + ":" + fetched + ":" + duplicates);
            super.updateSourceProgress(runId, sourceId, totalMessages, totalBytes, processedBytes, fetched, imported, duplicates);
        }
    }
}
