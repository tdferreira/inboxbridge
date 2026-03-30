package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import jakarta.mail.Folder;
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
}