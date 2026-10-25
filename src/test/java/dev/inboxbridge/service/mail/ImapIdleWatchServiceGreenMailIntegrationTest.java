package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.MailClientConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

class ImapIdleWatchServiceGreenMailIntegrationTest {

    private static final String HOST = "localhost";
    private static final String SOURCE_ID = "idle-greenmail-source";
    private static final String SOURCE_USERNAME = "idle-source@example.com";
    private static final String SOURCE_PASSWORD = "Source#123";

    @RegisterExtension
    final GreenMailExtension sourceMail = new GreenMailExtension(new ServerSetup(0, HOST, ServerSetup.PROTOCOL_IMAPS));

    @BeforeEach
    void setUp() {
        sourceMail.setUser(SOURCE_USERNAME, SOURCE_PASSWORD);
    }

    private final List<ImapIdleWatchService> testWatchers = new CopyOnWriteArrayList<>();

    private ImapIdleWatchService newWatchService() {
        ImapIdleWatchService service = new ImapIdleWatchService();
        service.imapIdleHealthService = new ImapIdleHealthService();
        testWatchers.add(service);
        return service;
    }

    @AfterEach
    void stopWatchers() {
        testWatchers.forEach(ImapIdleWatchService::shutdown);
    }

    @Test
    void sourceSpecificRenewalChangeLeavesOtherMailboxConnected() throws Exception {
        var renewal = new java.util.concurrent.atomic.AtomicReference<>(Duration.ofMinutes(25));
        var watcher = newWatchService();
        RuntimeEmailAccount base = runtimeAccount(SourceFetchMode.IDLE);
        RuntimeEmailAccount other = new RuntimeEmailAccount("idle-other", "USER", 7L, "alice", true,
                base.protocol(), base.host(), base.port(), base.tls(), base.authMethod(), base.oauthProvider(),
                base.username(), base.password(), "", Optional.of("INBOX"), false, SourceFetchMode.IDLE, Optional.empty(), null);
        watcher.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(base, other));
        watcher.mailSessionFactory = secureMailSessionFactory();
        watcher.sourcePollingSettingsService = new dev.inboxbridge.service.polling.SourcePollingSettingsService() {
            @Override public Duration effectiveIdleRenewalInterval(RuntimeEmailAccount account) {
                return account.id().equals(SOURCE_ID) ? renewal.get() : Duration.ofMinutes(25);
            }
            @Override public Duration effectiveIdleSafetyPollInterval(RuntimeEmailAccount account) {
                return Duration.ofMinutes(30);
            }
        };
        watcher.refreshWatches();
        waitForCondition(() -> watcher.imapIdleHealthService.isHealthy(SOURCE_ID) && watcher.imapIdleHealthService.isHealthy("idle-other"));
        Instant first = watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt();
        Instant unchanged = watcher.imapIdleHealthService.watchStates("idle-other").getFirst().lastConnectedAt();
        renewal.set(Duration.ofMillis(250));
        watcher.refreshWatches();
        waitForCondition(() -> watcher.imapIdleHealthService.isHealthy(SOURCE_ID)
                && watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt().isAfter(first));
        assertEquals(unchanged, watcher.imapIdleHealthService.watchStates("idle-other").getFirst().lastConnectedAt());
    }

    @Test
    void changedRenewalSettingsReconnectWithoutRestartingTheService() throws Exception {
        var settings = new java.util.concurrent.atomic.AtomicReference<>(
                new dev.inboxbridge.service.polling.PollingSettingsService.EffectiveIdleSettings(true, Duration.ofMinutes(25), Duration.ofSeconds(20)));
        var watcher = newWatchService();
        watcher.pollingSettingsService = new dev.inboxbridge.service.polling.PollingSettingsService() {
            @Override public EffectiveIdleSettings effectiveIdleSettings() { return settings.get(); }
        };
        watcher.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watcher.mailSessionFactory = secureMailSessionFactory();
        watcher.refreshWatches();
        waitForCondition(() -> watcher.imapIdleHealthService.isHealthy(SOURCE_ID));
        Instant connected = watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt();
        // A policy-only change must not interrupt the live connection.
        settings.set(new dev.inboxbridge.service.polling.PollingSettingsService.EffectiveIdleSettings(false, Duration.ofMinutes(25), Duration.ofSeconds(20)));
        watcher.refreshWatches();
        assertEquals(connected, watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt());
        settings.set(new dev.inboxbridge.service.polling.PollingSettingsService.EffectiveIdleSettings(false, Duration.ofMillis(250), Duration.ofSeconds(2)));
        watcher.refreshWatches();
        waitForCondition(() -> watcher.imapIdleHealthService.isHealthy(SOURCE_ID)
                && watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt().isAfter(connected));
        waitForCondition(() -> watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastRenewedAt() != null);
    }

    @Test
    void renewalEndsAndReissuesIdleWithoutReopeningTheFolder() throws Exception {
        RecordingPollingService pollingService = new RecordingPollingService();
        ImapIdleWatchService watcher = newWatchService();
        watcher.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watcher.pollingService = pollingService;
        watcher.idleRenewalInterval = Duration.ofMillis(250);
        watcher.idleRenewalTimeout = Duration.ofSeconds(2);
        ByteArrayOutputStream transcript = new ByteArrayOutputStream();
        MailSessionFactory sessions = secureMailSessionFactory();
        watcher.mailSessionFactory = new MailSessionFactory() {
            @Override public Session idleImapSession(RuntimeEmailAccount account) {
                Session session = sessions.idleImapSession(account);
                session.setDebugOut(new PrintStream(transcript, true, StandardCharsets.UTF_8));
                session.setDebug(true);
                return session;
            }
        };
        pollingService.watchService = watcher;
        watcher.refreshWatches();
        waitForInvocations(pollingService, 1);
        pollingService.invokedSourceIds.clear();
        Instant firstConnection = watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt();
        waitForCondition(() -> {
            watcher.drainPendingSources();
            return transcript.toString(StandardCharsets.UTF_8).contains("DONE")
                    && transcript.toString(StandardCharsets.UTF_8).contains("NOOP")
                    && watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastRenewedAt() != null;
        });
        assertEquals(firstConnection, watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt());
        watcher.drainPendingSources();
        assertTrue(pollingService.invokedSourceIds.isEmpty(), "Successful renewals must not queue imports");
        assertEquals(List.of("catchup"), pollingService.reasons);
        // Notifications still work after the command was renewed on the same connection.
        pollingService.invokedSourceIds.clear();
        appendMessage("after-renewal", "<after-renewal@example.com>");
        waitForInvocations(pollingService, 1);
        assertEquals("notification", pollingService.reasons.getLast());
        assertTrue(watcher.imapIdleHealthService.isHealthy(SOURCE_ID));
    }

    @Test
    void reconnectQueuesCatchUpForMailDeliveredWhileTheServerWasDown() throws Exception {
        RecordingPollingService pollingService = new RecordingPollingService();
        ImapIdleWatchService watcher = newWatchService();
        watcher.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watcher.pollingService = pollingService;
        watcher.mailSessionFactory = secureMailSessionFactory();
        pollingService.watchService = watcher;
        watcher.refreshWatches();
        waitForInvocations(pollingService, 1);
        Instant firstConnection = watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt();
        int port = sourceMail.getImaps().getPort();
        sourceMail.stop();
        waitForCondition(() -> !watcher.imapIdleHealthService.isHealthy(SOURCE_ID));
        MimeMessage offlineMessage = new MimeMessage(secureImapSession());
        offlineMessage.setFrom("sender@example.com");
        offlineMessage.setRecipients(Message.RecipientType.TO, SOURCE_USERNAME);
        offlineMessage.setSubject("delivered-during-outage");
        offlineMessage.setText("Must be found by catch-up without a new IDLE notification");
        offlineMessage.saveChanges();
        com.icegreen.greenmail.util.GreenMail restarted = new com.icegreen.greenmail.util.GreenMail(
                new ServerSetup(port, HOST, ServerSetup.PROTOCOL_IMAPS));
        restarted.setUser(SOURCE_USERNAME, SOURCE_PASSWORD).deliver(offlineMessage);
        pollingService.invokedSourceIds.clear();
        try {
            restarted.start();
            waitForCondition(() -> watcher.imapIdleHealthService.isHealthy(SOURCE_ID)
                    && watcher.imapIdleHealthService.watchStates(SOURCE_ID).getFirst().lastConnectedAt().isAfter(firstConnection));
            waitForInvocations(pollingService, 1);
            assertEquals(SOURCE_ID, pollingService.invokedSourceIds.getFirst());
            assertEquals("delivered-during-outage", restarted.getReceivedMessages()[0].getSubject());
        } finally {
            watcher.shutdown();
            restarted.stop();
        }
    }

    private void waitForCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(25);
        }
        fail("Timed out waiting for IMAP watcher state");
    }

    @Test
    void idleWatcherQueuesAndRunsAnIdleTriggeredPollWhenNewMailArrives() throws Exception {
        RecordingPollingService pollingService = new RecordingPollingService();
        ImapIdleWatchService watchService = newWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
        watchService.mailSessionFactory = secureMailSessionFactory();
        pollingService.watchService = watchService;

        watchService.refreshWatches();
        waitForInvocations(pollingService, 1);
        pollingService.invokedSourceIds.clear();

        appendMessage("watched-message", "<watched-message@example.com>");
        waitForInvocations(pollingService, 1);

        assertEquals(List.of(SOURCE_ID), pollingService.invokedSourceIds);
        watchService.shutdown();
    }

    @Test
    void idleWatcherQueuesAndRunsAnIdleTriggeredPollWhenMultipartMailWithAttachmentArrives() throws Exception {
        RecordingPollingService pollingService = new RecordingPollingService();
        ImapIdleWatchService watchService = newWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
        watchService.mailSessionFactory = secureMailSessionFactory();
        pollingService.watchService = watchService;

        watchService.refreshWatches();
        waitForInvocations(pollingService, 1);
        pollingService.invokedSourceIds.clear();

        appendMultipartMessageWithAttachment("watched-attachment", "<watched-attachment@example.com>");
        waitForInvocations(pollingService, 1);

        assertEquals(List.of(SOURCE_ID), pollingService.invokedSourceIds);
        watchService.shutdown();
    }

    @Test
    void idleWatcherAlsoWatchesSecondaryConfiguredFolders() throws Exception {
        RecordingPollingService pollingService = new RecordingPollingService();
        ImapIdleWatchService watchService = newWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount("INBOX, Projects/2026", SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
        watchService.mailSessionFactory = secureMailSessionFactory();
        pollingService.watchService = watchService;

        ensureFolderExists("Projects/2026");
        watchService.refreshWatches();
        waitForInvocations(pollingService, 1);
        pollingService.invokedSourceIds.clear();

        appendMessage("Projects/2026", "watched-project", "<watched-project@example.com>");
        waitForInvocations(pollingService, 1);

        assertEquals(List.of(SOURCE_ID), pollingService.invokedSourceIds);
        watchService.shutdown();
    }

    @Test
    void idleWatcherRequeuesBusyRunsUntilThePollRunnerAcceptsThem() throws Exception {
        BusyOncePollingService pollingService = new BusyOncePollingService();
        ImapIdleWatchService watchService = newWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
        watchService.mailSessionFactory = secureMailSessionFactory();

        watchService.refreshWatches();
        waitForCondition(() -> watchService.imapIdleHealthService.isHealthy(SOURCE_ID));
        watchService.drainPendingSources();
        assertTrue(pollingService.invokedSourceIds.isEmpty());

        watchService.drainPendingSources();
        assertEquals(List.of(SOURCE_ID), pollingService.invokedSourceIds);
        watchService.shutdown();
    }

    @Test
    void drainStillInvokesIdleSourceWhenSchedulerIsAlreadyRunning() throws Exception {
        SchedulerCompatiblePollingService pollingService = new SchedulerCompatiblePollingService();
        ImapIdleWatchService watchService = newWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
        watchService.mailSessionFactory = secureMailSessionFactory();

        watchService.refreshWatches();
        waitForCondition(() -> watchService.imapIdleHealthService.isHealthy(SOURCE_ID));
        pollingService.invokedSourceIds.clear();
        pollingService.running = true;

        watchService.drainPendingSources();

        assertEquals(List.of(SOURCE_ID), pollingService.invokedSourceIds);
        watchService.shutdown();
    }

    private RuntimeEmailAccount runtimeAccount(SourceFetchMode fetchMode) {
        return runtimeAccount("INBOX", fetchMode);
    }

    private RuntimeEmailAccount runtimeAccount(String sourceFolders, SourceFetchMode fetchMode) {
        return new RuntimeEmailAccount(
                SOURCE_ID,
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                HOST,
                sourceMail.getImaps().getPort(),
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                SOURCE_USERNAME,
                SOURCE_PASSWORD,
                "",
                Optional.of(sourceFolders),
                false,
                fetchMode,
                Optional.of("Imported/Test"),
                null);
    }

    private void appendMessage(String subject, String messageId) throws Exception {
        appendMessage("INBOX", subject, messageId);
    }

    private void appendMessage(String folderName, String subject, String messageId) throws Exception {
        ensureFolderExists(folderName);
        Session session = secureImapSession();
        Store store = session.getStore("imaps");
        Folder folder = null;
        try {
            store.connect(HOST, sourceMail.getImaps().getPort(), SOURCE_USERNAME, SOURCE_PASSWORD);
            folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("sender@example.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(SOURCE_USERNAME));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText("IDLE test message", StandardCharsets.UTF_8.name());
            message.setHeader("Message-ID", messageId);
            message.setSentDate(java.util.Date.from(Instant.now()));
            message.saveChanges();
            folder.appendMessages(new Message[] { message });
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void ensureFolderExists(String folderName) throws Exception {
        Session session = secureImapSession();
        Store store = session.getStore("imaps");
        Folder folder = null;
        try {
            store.connect(HOST, sourceMail.getImaps().getPort(), SOURCE_USERNAME, SOURCE_PASSWORD);
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                folder.create(Folder.HOLDS_MESSAGES);
            }
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void appendMultipartMessageWithAttachment(String subject, String messageId) throws Exception {
        Session session = secureImapSession();
        Store store = session.getStore("imaps");
        Folder folder = null;
        try {
            store.connect(HOST, sourceMail.getImaps().getPort(), SOURCE_USERNAME, SOURCE_PASSWORD);
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("sender@example.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(SOURCE_USERNAME));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setHeader("Message-ID", messageId);
            message.setSentDate(java.util.Date.from(Instant.now()));

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Message body with attachment", StandardCharsets.UTF_8.name());

            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setFileName("sample.txt");
            attachmentPart.setContent("Attachment payload", "text/plain; charset=UTF-8");
            attachmentPart.setDisposition(jakarta.mail.Part.ATTACHMENT);

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachmentPart);
            message.setContent(multipart);
            message.saveChanges();
            folder.appendMessages(new Message[] { message });
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void waitForInvocations(RecordingPollingService pollingService, int expectedCount) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (System.currentTimeMillis() < deadline) {
            if (pollingService.watchService.imapIdleHealthService.isHealthy(SOURCE_ID)) {
                watchAndDrain(pollingService.watchService);
            }
            if (pollingService.invokedSourceIds.size() >= expectedCount) {
                return;
            }
            Thread.sleep(100L);
        }
        fail("Timed out waiting for IMAP IDLE watcher to trigger a poll");
    }

    private MailSessionFactory secureMailSessionFactory() {
        MailSessionFactory factory = new MailSessionFactory() {
            @Override
            public Session idleImapSession(RuntimeEmailAccount account) {
                Session session = super.idleImapSession(account);
                session.getProperties().put("mail.imap.ssl.checkserveridentity", "false");
                session.getProperties().put("mail.imaps.ssl.checkserveridentity", "false");
                session.getProperties().put("mail.imaps.ssl.trust", "*");
                return session;
            }
        };
        factory.setMailClientConfig(mailClientConfig());
        return factory;
    }

    private Session secureImapSession() {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imap.ssl.checkserveridentity", "false");
        properties.put("mail.imaps.ssl.checkserveridentity", "false");
        properties.put("mail.imaps.ssl.trust", "*");
        return Session.getInstance(properties);
    }

    private MailClientConfig mailClientConfig() {
        return new MailClientConfig() {
            @Override
            public java.time.Duration idleRenewalInterval() { return java.time.Duration.ofMinutes(25); }
            @Override
            public java.time.Duration idleRenewalTimeout() { return java.time.Duration.ofSeconds(20); }
            @Override
            public java.time.Duration idleSafetyPollInterval() { return java.time.Duration.ofMinutes(30); }
            @Override
            public boolean idleSafetyPollingEnabled() { return true; }

            @Override
            public java.time.Duration connectionTimeout() {
                return java.time.Duration.ofSeconds(5);
            }

            @Override
            public java.time.Duration operationTimeout() {
                return java.time.Duration.ofSeconds(5);
            }

            @Override
            public java.time.Duration idleOperationTimeout() {
                return java.time.Duration.ofSeconds(5);
            }
        };
    }

    private void watchAndDrain(ImapIdleWatchService watchService) {
        if (watchService != null) {
            watchService.drainPendingSources();
        }
    }

    private static void closeQuietly(Folder folder) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            if (store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static final class FixedRuntimeEmailAccountService extends RuntimeEmailAccountService {
        private final List<RuntimeEmailAccount> emailAccounts;

        private FixedRuntimeEmailAccountService(List<RuntimeEmailAccount> emailAccounts) {
            this.emailAccounts = emailAccounts;
        }

        @Override
        public List<RuntimeEmailAccount> listEnabledForPolling() {
            return emailAccounts;
        }
    }

    private static final class RecordingPollingService extends PollingService {
        private final List<String> invokedSourceIds = new CopyOnWriteArrayList<>();
        private ImapIdleWatchService watchService;
        private final List<String> reasons = new CopyOnWriteArrayList<>();

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleCatchUpForSource(RuntimeEmailAccount emailAccount) {
            reasons.add("catchup");
            invokedSourceIds.add(emailAccount.id());
            return new dev.inboxbridge.dto.PollRunResult();
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleTriggeredPollForSource(RuntimeEmailAccount emailAccount) {
            reasons.add("notification");
            invokedSourceIds.add(emailAccount.id());
            return new dev.inboxbridge.dto.PollRunResult();
        }
    }

    private static final class BusyOncePollingService extends PollingService {
        private final List<String> invokedSourceIds = new CopyOnWriteArrayList<>();
        private boolean firstCall = true;

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleCatchUpForSource(RuntimeEmailAccount emailAccount) {
            return runIdleTriggeredPollForSource(emailAccount);
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleTriggeredPollForSource(RuntimeEmailAccount emailAccount) {
            if (firstCall) {
                firstCall = false;
                dev.inboxbridge.dto.PollRunResult busy = new dev.inboxbridge.dto.PollRunResult();
                busy.addError(new dev.inboxbridge.dto.PollRunError("poll_busy", emailAccount.id(), "busy", null));
                busy.finish();
                return busy;
            }
            invokedSourceIds.add(emailAccount.id());
            return new dev.inboxbridge.dto.PollRunResult();
        }
    }

    private static final class SchedulerCompatiblePollingService extends PollingService {
        private final List<String> invokedSourceIds = new CopyOnWriteArrayList<>();
        private boolean running;

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleCatchUpForSource(RuntimeEmailAccount emailAccount) {
            return runIdleTriggeredPollForSource(emailAccount);
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleTriggeredPollForSource(RuntimeEmailAccount emailAccount) {
            invokedSourceIds.add(emailAccount.id());
            return new dev.inboxbridge.dto.PollRunResult();
        }
    }
}
