package dev.inboxbridge.service.destination;

import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;

class ImapAppendMailDestinationServiceTest {

    private static final String USERNAME = "owner@example.com";
    private static final String PASSWORD = "Secret#123";

    private GreenMail greenMail;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(ServerSetupTest.IMAP);
        greenMail.start();
        greenMail.setUser(USERNAME, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    void listFoldersReturnsInboxFirstAndIncludesNestedFolders() throws Exception {
        createTestFolders();
        ImapAppendMailDestinationService service = new ImapAppendMailDestinationService();

        List<String> folders = service.listFolders(new ImapAppendDestinationTarget(
                "user-destination:7",
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "127.0.0.1",
                greenMail.getImap().getPort(),
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                USERNAME,
                PASSWORD,
                "INBOX"));

        assertEquals("INBOX", folders.getFirst());
        assertTrue(folders.contains("Archive"));
        assertTrue(folders.stream().anyMatch((folder) -> folder.toLowerCase().contains("2026")));
    }

    @Test
    void testConnectionReturnsSuccessfulImapProbe() throws Exception {
        createTestFolders();
        ImapAppendMailDestinationService service = new ImapAppendMailDestinationService();

        EmailAccountConnectionTestResult result = service.testConnection(new ImapAppendDestinationTarget(
                "user-destination:7",
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "127.0.0.1",
                greenMail.getImap().getPort(),
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                USERNAME,
                PASSWORD,
                "INBOX"));

        assertTrue(result.success());
        assertEquals("IMAP", result.protocol());
        assertEquals("INBOX", result.folder());
        assertTrue(result.folderAccessible());
    }

    @Test
    void importMessageAllowsConcurrentCreationOfTheDestinationFolder() throws Exception {
        ImapAppendMailDestinationService service = new ImapAppendMailDestinationService();
        for (int attempt = 0; attempt < 10; attempt++) {
            String folderName = "Imported-" + attempt;
            ImapAppendDestinationTarget target = new ImapAppendDestinationTarget(
                    "user-destination:7",
                    7L,
                    "alice",
                    UserMailDestinationConfigService.PROVIDER_CUSTOM,
                    "127.0.0.1",
                    greenMail.getImap().getPort(),
                    false,
                    InboxBridgeConfig.AuthMethod.PASSWORD,
                    InboxBridgeConfig.OAuthProvider.NONE,
                    USERNAME,
                    PASSWORD,
                    folderName);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                int runNumber = attempt;
                var first = executor.submit(() -> {
                    try {
                        importConcurrently(service, target, ready, start, messageBytes("alpha-" + runNumber, "<alpha-" + runNumber + "@example.com>"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                var second = executor.submit(() -> {
                    try {
                        importConcurrently(service, target, ready, start, messageBytes("beta-" + runNumber, "<beta-" + runNumber + "@example.com>"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            }

            assertEquals(List.of("alpha-" + attempt, "beta-" + attempt), listSubjects(folderName));
        }
    }

    private void createTestFolders() throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), USERNAME, PASSWORD);
            Folder defaultFolder = store.getDefaultFolder();
            char separator = defaultFolder.getSeparator();

            Folder archive = store.getFolder("Archive");
            if (!archive.exists()) {
                archive.create(Folder.HOLDS_MESSAGES);
            }

            Folder parent = store.getFolder("Projects");
            if (!parent.exists()) {
                parent.create(Folder.HOLDS_FOLDERS);
            }

            Folder nested = store.getFolder("Projects" + separator + "2026");
            if (!nested.exists()) {
                nested.create(Folder.HOLDS_MESSAGES);
            }
        } finally {
            store.close();
        }
    }

    private void importConcurrently(
            ImapAppendMailDestinationService service,
            ImapAppendDestinationTarget target,
            CountDownLatch ready,
            CountDownLatch start,
            byte[] rawMessage)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        service.importMessage(
                target,
                null,
                new FetchedMessage("source", "message-key", Optional.empty(), Instant.now(), rawMessage));
    }

    private byte[] messageBytes(String subject, String messageId) {
        return ("Subject: " + subject + "\r\n"
                + "Message-ID: " + messageId + "\r\n"
                + "From: sender@example.com\r\n"
                + "To: " + USERNAME + "\r\n"
                + "Date: Tue, 01 Apr 2025 12:00:00 +0000\r\n"
                + "\r\n"
                + "Hello " + subject + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private List<String> listSubjects(String folderName) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), USERNAME, PASSWORD);
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            Message[] messages = folder.getMessages();
            return java.util.Arrays.stream(messages)
                    .map((message) -> {
                        try {
                            return message.getSubject();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sorted()
                    .toList();
        } finally {
            store.close();
        }
    }
}
