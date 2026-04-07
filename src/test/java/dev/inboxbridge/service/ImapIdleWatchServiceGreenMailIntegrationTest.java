package dev.inboxbridge.service;

import dev.inboxbridge.service.polling.PollingService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
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

    private static final String SOURCE_ID = "idle-greenmail-source";
    private static final String SOURCE_USERNAME = "idle-source@example.com";
    private static final String SOURCE_PASSWORD = "Source#123";

    @RegisterExtension
    final GreenMailExtension sourceMail = new GreenMailExtension(new ServerSetup(0, null, ServerSetup.PROTOCOL_IMAP));

    @BeforeEach
    void setUp() {
        sourceMail.setUser(SOURCE_USERNAME, SOURCE_PASSWORD);
    }

    @Test
    void idleWatcherQueuesAndRunsAnIdleTriggeredPollWhenNewMailArrives() throws Exception {
        RecordingPollingService pollingService = new RecordingPollingService();
        ImapIdleWatchService watchService = new ImapIdleWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
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
        ImapIdleWatchService watchService = new ImapIdleWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
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
        ImapIdleWatchService watchService = new ImapIdleWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount("INBOX, Projects/2026", SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;
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
    void idleWatcherRequeuesBusyRunsUntilThePollRunnerAcceptsThem() {
        BusyOncePollingService pollingService = new BusyOncePollingService();
        ImapIdleWatchService watchService = new ImapIdleWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;

        watchService.refreshWatches();
        watchService.drainPendingSources();
        assertTrue(pollingService.invokedSourceIds.isEmpty());

        watchService.drainPendingSources();
        assertEquals(List.of(SOURCE_ID), pollingService.invokedSourceIds);
        watchService.shutdown();
    }

    @Test
    void drainStillInvokesIdleSourceWhenSchedulerIsAlreadyRunning() {
        SchedulerCompatiblePollingService pollingService = new SchedulerCompatiblePollingService();
        ImapIdleWatchService watchService = new ImapIdleWatchService();
        watchService.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount(SourceFetchMode.IDLE)));
        watchService.pollingService = pollingService;

        watchService.refreshWatches();
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
                "127.0.0.1",
                sourceMail.getImap().getPort(),
                false,
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
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", sourceMail.getImap().getPort(), SOURCE_USERNAME, SOURCE_PASSWORD);
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
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", sourceMail.getImap().getPort(), SOURCE_USERNAME, SOURCE_PASSWORD);
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
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", sourceMail.getImap().getPort(), SOURCE_USERNAME, SOURCE_PASSWORD);
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
            watchAndDrain(pollingService.watchService);
            if (pollingService.invokedSourceIds.size() >= expectedCount) {
                return;
            }
            Thread.sleep(100L);
        }
        fail("Timed out waiting for IMAP IDLE watcher to trigger a poll");
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

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public dev.inboxbridge.dto.PollRunResult runIdleTriggeredPollForSource(RuntimeEmailAccount emailAccount) {
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
        public dev.inboxbridge.dto.PollRunResult runIdleTriggeredPollForSource(RuntimeEmailAccount emailAccount) {
            invokedSourceIds.add(emailAccount.id());
            return new dev.inboxbridge.dto.PollRunResult();
        }
    }
}
