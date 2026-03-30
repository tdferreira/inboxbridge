package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.dto.PollRunResult;
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
        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
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

        PollRunResult result = service.runPoll("manual-api");

        assertEquals(1, result.getErrorDetails().size());
        assertEquals("microsoft_access_revoked", result.getErrorDetails().getFirst().code());
    }

    @Test
    void runPollForUserRespectsCooldownForBroadManualRuns() {
        PollingService service = new PollingService();
        service.mailSourceClient = new RecordingMailSourceClient();
        service.importDeduplicationService = new ImportDeduplicationService();
        service.mailDestinationServices = new FakeMailDestinationServices(new FakeMailDestinationService(true));
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(userBridge(7L)));
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.userPollingSettingsService = new FakeUserPollingSettingsService(true, Duration.ofMinutes(5), "5m", 10);
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new CooldownSourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = 7L;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.USER;

        PollRunResult result = service.runPollForUser(actor, "user-ui");

        assertEquals(1, result.getErrorDetails().size());
        assertEquals("source_cooling_down", result.getErrorDetails().getFirst().code());
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
    void busyPollMessageIncludesCurrentSourceWhenSingleSourcePollIsActive() throws Exception {
        PollingService service = new PollingService();
        java.lang.reflect.Field activePollField = PollingService.class.getDeclaredField("activePoll");
        activePollField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Object> activePoll =
                (java.util.concurrent.atomic.AtomicReference<Object>) activePollField.get(service);
        java.lang.reflect.Constructor<?> constructor = Class
                .forName("dev.inboxbridge.service.PollingService$ActivePoll")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        activePoll.set(constructor.newInstance("app-fetcher", "outlook-main", Instant.parse("2026-03-27T09:56:56Z")));

        java.lang.reflect.Method method = PollingService.class.getDeclaredMethod("currentBusyMessage");
        method.setAccessible(true);
        String message = (String) method.invoke(service);

        assertTrue(message.contains("outlook-main"));
        assertTrue(message.contains("app-fetcher"));
    }

    private static RuntimeEmailAccount userBridge(Long userId) {
        return new RuntimeEmailAccount(
                "user-fetcher",
                "USER",
                userId,
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
                new GmailApiDestinationTarget("target", userId, "alice", UserMailDestinationConfigService.PROVIDER_GMAIL, "me", "client", "secret", "refresh", "https://localhost", true, false, false));
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
        private int lastFetchWindow;

        @Override
        public List<dev.inboxbridge.domain.FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
            lastFetchWindow = fetchWindow;
            return List.of();
        }

        @Override
        public java.util.Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
            return java.util.Optional.of(new MailboxCountProbe("Spam", 4));
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
        public void recordFailure(String sourceId, Instant finishedAt, String failureReason) {
            failureCalls++;
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

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, int duplicates, String error) {
        }
    }
}
