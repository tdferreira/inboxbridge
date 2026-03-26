package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.dto.PollRunResult;

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
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.gmailImportService = new GmailImportService();
        service.gmailLabelService = new FakeGmailLabelService();
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeBridgeService = new FakeRuntimeBridgeService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(false, Duration.ofMinutes(2), "2m", 33);
        service.sourcePollingStateService = sourcePollingStateService;

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(33, mailSourceClient.lastFetchWindow);
        assertEquals("user-fetcher", sourcePollingStateService.lastRecordedSuccessSourceId);
        assertEquals(0, result.getErrors().size());
    }

    @Test
    void runPollReportsCooldownForManualTrigger() {
        PollingService service = new PollingService();
        service.mailSourceClient = new RecordingMailSourceClient();
        service.importDeduplicationService = new ImportDeduplicationService();
        service.gmailImportService = new GmailImportService();
        service.gmailLabelService = new FakeGmailLabelService();
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeBridgeService = new FakeRuntimeBridgeService(List.of(systemBridge("env-fetcher")));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingStateService = new CooldownSourcePollingStateService();

        PollRunResult result = service.runPoll("admin-ui");

        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().getFirst().contains("cooling down"));
    }

    private static RuntimeBridge userBridge(Long userId) {
        return new RuntimeBridge(
                "user-fetcher",
                "USER",
                userId,
                "alice",
                true,
                dev.inboxbridge.config.BridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                dev.inboxbridge.config.BridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.BridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.of("Imported/Test"),
                new GmailTarget("target", userId, "alice", "me", "client", "secret", "refresh", "https://localhost", true, false, false));
    }

    private static RuntimeBridge systemBridge(String sourceId) {
        return new RuntimeBridge(
                sourceId,
                "SYSTEM",
                null,
                "system",
                true,
                dev.inboxbridge.config.BridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                dev.inboxbridge.config.BridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.BridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.of("Imported/Test"),
                new GmailTarget("target", null, "system", "me", "client", "secret", "refresh", "https://localhost", true, false, false));
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

    private static final class RecordingMailSourceClient extends MailSourceClient {
        private int lastFetchWindow;

        @Override
        public List<dev.inboxbridge.domain.FetchedMessage> fetch(RuntimeBridge bridge, int fetchWindow) {
            lastFetchWindow = fetchWindow;
            return List.of();
        }
    }

    private static final class FakePollingSettingsService extends PollingSettingsService {
        private final EffectivePollingSettings settings;

        private FakePollingSettingsService(boolean enabled, Duration interval, String text, int fetchWindow) {
            this.settings = new EffectivePollingSettings(enabled, text, interval, fetchWindow);
        }

        @Override
        public EffectivePollingSettings effectiveSettings() {
            return settings;
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

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval) {
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public void recordSuccess(String sourceId, Instant finishedAt, PollingSettingsService.EffectivePollingSettings settings) {
            lastRecordedSuccessSourceId = sourceId;
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
    }

    private static final class FakeRuntimeBridgeService extends RuntimeBridgeService {
        private final List<RuntimeBridge> bridges;

        private FakeRuntimeBridgeService(List<RuntimeBridge> bridges) {
            this.bridges = bridges;
        }

        @Override
        public List<RuntimeBridge> listEnabledForPolling() {
            return bridges;
        }
    }

    private static final class FakeGmailLabelService extends GmailLabelService {
        @Override
        public List<String> resolveLabelIds(GmailTarget target, java.util.Optional<String> customLabel) {
            return List.of();
        }
    }

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, int duplicates, String error) {
        }
    }
}
