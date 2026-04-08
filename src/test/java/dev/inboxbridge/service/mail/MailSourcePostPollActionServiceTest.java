package dev.inboxbridge.service.mail;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.domain.SourcePostPollSettings;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;

class MailSourcePostPollActionServiceTest {

    @Test
    void moveActionMarksAsReadCopiesToTargetAndExpungesSourceFolder() {
        TestStoreState state = new TestStoreState();
        TestFolder sourceFolder = state.folder("INBOX");
        TestFolder archiveFolder = state.folder("Archive");
        TestMessage sourceMessage = new TestMessage(sourceFolder);
        sourceFolder.message = sourceMessage;

        MailSourcePostPollActionService service = service(state, sourceMessage);

        service.apply(
                runtimeAccount(new SourcePostPollSettings(true, SourcePostPollAction.MOVE, Optional.of("Archive"))),
                fetchedMessage("INBOX"));

        assertTrue(sourceMessage.seen);
        assertTrue(sourceMessage.deleted);
        assertEquals(1, sourceFolder.copyTargets.size());
        assertEquals("Archive", sourceFolder.copyTargets.getFirst());
        assertTrue(sourceFolder.closedWithExpunge);
        assertFalse(archiveFolder.closedWithExpunge);
    }

    @Test
    void deleteActionMarksMessageDeletedAndExpungesSourceFolder() {
        TestStoreState state = new TestStoreState();
        TestFolder sourceFolder = state.folder("INBOX");
        TestMessage sourceMessage = new TestMessage(sourceFolder);
        sourceFolder.message = sourceMessage;

        MailSourcePostPollActionService service = service(state, sourceMessage);

        service.apply(
                runtimeAccount(new SourcePostPollSettings(false, SourcePostPollAction.DELETE, Optional.empty())),
                fetchedMessage("INBOX"));

        assertTrue(sourceMessage.deleted);
        assertTrue(sourceFolder.closedWithExpunge);
        assertTrue(sourceFolder.copyTargets.isEmpty());
    }

    @Test
    void nonImapAccountsAreRejectedBeforeApplyingActions() {
        TestStoreState state = new TestStoreState();
        MailSourcePostPollActionService service = service(state, null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.apply(runtimePop3Account(), fetchedMessage("INBOX")));

        assertEquals("Source-side message actions are only supported for IMAP accounts", error.getMessage());
    }

    private static MailSourcePostPollActionService service(TestStoreState state, Message resolvedMessage) {
        return new MailSourcePostPollActionService(
                new TestMailSessionFactory(state),
                new TestMailSourceConnectionService(),
                new TestMailSourceMessageMapper(resolvedMessage),
                null);
    }

    private static RuntimeEmailAccount runtimeAccount(SourcePostPollSettings postPollSettings) {
        return new RuntimeEmailAccount(
                "source-1",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.test",
                993,
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
                postPollSettings,
                null);
    }

    private static RuntimeEmailAccount runtimePop3Account() {
        return new RuntimeEmailAccount(
                "source-1",
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
                new SourcePostPollSettings(false, SourcePostPollAction.DELETE, Optional.empty()),
                null);
    }

    private static FetchedMessage fetchedMessage(String folderName) {
        return new FetchedMessage(
                "source-1",
                "source-key",
                Optional.of("<message@example.com>"),
                java.time.Instant.parse("2026-04-06T00:00:00Z"),
                Optional.of(folderName),
                null,
                null,
                null,
                "raw".getBytes());
    }

    private static final class TestMailSessionFactory extends MailSessionFactory {
        private final TestStoreState state;

        private TestMailSessionFactory(TestStoreState state) {
            this.state = state;
        }

        @Override
        public Session sourceImapSession(RuntimeEmailAccount account) {
            Properties properties = new Properties();
            Session session = Session.getInstance(properties);
            session.addProvider(new Provider(Provider.Type.STORE, "testimap", TestStore.class.getName(), "InboxBridge", "1.0"));
            TestStore.currentState = state;
            return session;
        }

        @Override
        public String imapStoreProtocol(boolean tls) {
            return "testimap";
        }
    }

    private static final class TestMailSourceConnectionService extends MailSourceConnectionService {
        private TestMailSourceConnectionService() {
            super(null, null);
        }

        @Override
        public void connectStore(Store store, RuntimeEmailAccount bridge) {
            ((TestStore) store).connected = true;
        }
    }

    private static final class TestMailSourceMessageMapper extends MailSourceMessageMapper {
        private final Message resolvedMessage;

        private TestMailSourceMessageMapper(Message resolvedMessage) {
            this.resolvedMessage = resolvedMessage;
        }

        @Override
        public Message resolveSourceMessage(Folder folder, FetchedMessage message) {
            return resolvedMessage;
        }
    }

    public static final class TestStore extends Store {
        private static TestStoreState currentState;
        private boolean connected;

        public TestStore(Session session, URLName urlName) {
            super(session, urlName);
        }

        @Override
        public Folder getDefaultFolder() {
            return currentState.folder("INBOX");
        }

        @Override
        public Folder getFolder(String name) {
            return currentState.folder(name);
        }

        @Override
        public Folder getFolder(URLName url) {
            return getFolder(url.getFile());
        }

        @Override
        protected boolean protocolConnect(String host, int port, String user, String password) {
            connected = true;
            return true;
        }
    }

    private static final class TestStoreState {
        private final BareStore supportStore = new BareStore();
        private final Map<String, TestFolder> folders = new HashMap<>();

        private TestFolder folder(String name) {
            return folders.computeIfAbsent(name, folderName -> new TestFolder(supportStore, folderName));
        }
    }

    private static final class TestFolder extends Folder {
        private final String name;
        private boolean open;
        private boolean closedWithExpunge;
        private TestMessage message;
        private final java.util.LinkedList<String> copyTargets = new java.util.LinkedList<>();

        private TestFolder(Store store, String name) {
            super(store);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFullName() {
            return name;
        }

        @Override
        public Folder getParent() {
            return null;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Folder[] list(String pattern) {
            return new Folder[0];
        }

        @Override
        public char getSeparator() {
            return '/';
        }

        @Override
        public int getType() {
            return HOLDS_MESSAGES;
        }

        @Override
        public boolean create(int type) {
            return false;
        }

        @Override
        public boolean hasNewMessages() {
            return false;
        }

        @Override
        public Folder getFolder(String name) {
            return null;
        }

        @Override
        public boolean delete(boolean recurse) {
            return false;
        }

        @Override
        public boolean renameTo(Folder f) {
            return false;
        }

        @Override
        public void open(int mode) {
            open = true;
        }

        @Override
        public void close(boolean expunge) {
            open = false;
            closedWithExpunge = expunge;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public Flags getPermanentFlags() {
            return new Flags();
        }

        @Override
        public int getMessageCount() {
            return message == null ? 0 : 1;
        }

        @Override
        public Message getMessage(int msgnum) {
            return message;
        }

        @Override
        public void appendMessages(Message[] msgs) {
        }

        @Override
        public Message[] expunge() {
            return new Message[0];
        }

        @Override
        public void copyMessages(Message[] msgs, Folder folder) {
            copyTargets.add(folder.getFullName());
        }
    }

    private static final class BareStore extends Store {
        private BareStore() {
            super(Session.getInstance(new Properties()), (URLName) null);
        }

        @Override
        public Folder getDefaultFolder() {
            return null;
        }

        @Override
        public Folder getFolder(String name) {
            return null;
        }

        @Override
        public Folder getFolder(URLName url) {
            return null;
        }

        @Override
        protected boolean protocolConnect(String host, int port, String user, String password) {
            return true;
        }
    }

    private static final class TestMessage extends Message {
        private final Folder folder;
        private boolean seen;
        private boolean deleted;

        private TestMessage(Folder folder) {
            this.folder = folder;
        }

        @Override
        public Address[] getFrom() {
            return new Address[0];
        }

        @Override
        public void setFrom() {
        }

        @Override
        public void setFrom(Address address) {
        }

        @Override
        public void addFrom(Address[] addresses) {
        }

        @Override
        public Address[] getRecipients(RecipientType type) {
            return new Address[0];
        }

        @Override
        public void setRecipients(RecipientType type, Address[] addresses) {
        }

        @Override
        public void addRecipients(RecipientType type, Address[] addresses) {
        }

        @Override
        public Address[] getReplyTo() {
            return new Address[0];
        }

        @Override
        public void setReplyTo(Address[] addresses) {
        }

        @Override
        public String getSubject() {
            return "subject";
        }

        @Override
        public void setSubject(String subject) {
        }

        @Override
        public java.util.Date getSentDate() {
            return new java.util.Date();
        }

        @Override
        public void setSentDate(java.util.Date date) {
        }

        @Override
        public java.util.Date getReceivedDate() {
            return new java.util.Date();
        }

        @Override
        public Flags getFlags() {
            Flags flags = new Flags();
            if (seen) {
                flags.add(Flags.Flag.SEEN);
            }
            if (deleted) {
                flags.add(Flags.Flag.DELETED);
            }
            return flags;
        }

        @Override
        public void setFlags(Flags flag, boolean set) {
            if (flag.contains(Flags.Flag.SEEN)) {
                seen = set;
            }
            if (flag.contains(Flags.Flag.DELETED)) {
                deleted = set;
            }
        }

        @Override
        public void setFlag(Flags.Flag flag, boolean set) {
            if (flag == Flags.Flag.SEEN) {
                seen = set;
            } else if (flag == Flags.Flag.DELETED) {
                deleted = set;
            }
        }

        @Override
        public Message reply(boolean replyToAll) {
            return null;
        }

        @Override
        public void saveChanges() {
        }

        @Override
        public int getSize() {
            return 0;
        }

        @Override
        public int getLineCount() {
            return 0;
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public boolean isMimeType(String mimeType) {
            return true;
        }

        @Override
        public String getDisposition() {
            return null;
        }

        @Override
        public void setDisposition(String disposition) {
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public void setDescription(String description) {
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public void setFileName(String filename) {
        }

        @Override
        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public Object getContent() {
            return "";
        }

        @Override
        public void setDataHandler(jakarta.activation.DataHandler dh) {
        }

        @Override
        public jakarta.activation.DataHandler getDataHandler() {
            return null;
        }

        @Override
        public void setContent(Object obj, String type) {
        }

        @Override
        public void setText(String text) {
        }

        @Override
        public void setContent(jakarta.mail.Multipart mp) {
        }

        @Override
        public void writeTo(java.io.OutputStream os) {
        }

        @Override
        public String[] getHeader(String header_name) {
            return null;
        }

        @Override
        public void setHeader(String header_name, String header_value) {
        }

        @Override
        public void addHeader(String header_name, String header_value) {
        }

        @Override
        public void removeHeader(String header_name) {
        }

        @Override
        public java.util.Enumeration<jakarta.mail.Header> getAllHeaders() {
            return java.util.Collections.emptyEnumeration();
        }

        @Override
        public java.util.Enumeration<jakarta.mail.Header> getMatchingHeaders(String[] names) {
            return java.util.Collections.emptyEnumeration();
        }

        @Override
        public java.util.Enumeration<jakarta.mail.Header> getNonMatchingHeaders(String[] names) {
            return java.util.Collections.emptyEnumeration();
        }

        @Override
        public Folder getFolder() {
            return folder;
        }

        @Override
        public boolean isExpunged() {
            return false;
        }

        @Override
        public int getMessageNumber() {
            return 1;
        }

        @Override
        public void setMessageNumber(int msgnum) {
        }
    }
}
