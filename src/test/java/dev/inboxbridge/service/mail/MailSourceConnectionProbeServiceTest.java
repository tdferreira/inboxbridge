package dev.inboxbridge.service.mail;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FetchProfile;
import jakarta.mail.Message;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;

class MailSourceConnectionProbeServiceTest {

    @Test
    void imapProbeAggregatesUnreadCountsAndMaterializesSampleMessage() {
        ProbeStoreState state = new ProbeStoreState();
        ProbeFolder inbox = state.folder("INBOX");
        ProbeFolder projects = state.folder("Projects/2026");
        inbox.setMessages(List.of(ProbeMessage.read("inbox-read")));
        projects.setMessages(List.of(ProbeMessage.unread("project-unread")));
        projects.permanentFlags = new Flags();
        projects.permanentFlags.add("$Forwarded");

        MailSourceConnectionProbeService service = service(state, true);

        EmailAccountConnectionTestResult result = service.testConnection(runtimeImapAccount(true));

        assertTrue(result.success());
        assertEquals("INBOX, Projects/2026", result.folder());
        assertEquals(2, result.visibleMessageCount());
        assertEquals(1, result.unreadMessageCount());
        assertTrue(result.sampleMessageAvailable());
        assertTrue(result.sampleMessageMaterialized());
        assertTrue(result.forwardedMarkerSupported());
        assertTrue(result.message().contains("sample message was materialized successfully"));
    }

    @Test
    void pop3ProbeExplainsUnreadFilteringIsUnsupported() {
        ProbeStoreState state = new ProbeStoreState();
        state.folder("INBOX").setMessages(List.of());

        MailSourceConnectionProbeService service = service(state, false);

        EmailAccountConnectionTestResult result = service.testConnection(runtimePop3Account(true));

        assertTrue(result.success());
        assertEquals("INBOX", result.folder());
        assertEquals(0, result.visibleMessageCount());
        assertFalse(result.sampleMessageAvailable());
        assertNull(result.sampleMessageMaterialized());
        assertEquals(Boolean.FALSE, result.unreadFilterSupported());
        assertEquals(Boolean.FALSE, result.unreadFilterValidated());
        assertTrue(result.message().contains("Server-side unread filtering is not supported for this protocol."));
    }

    private static MailSourceConnectionProbeService service(ProbeStoreState state, boolean materializeMessages) {
        return new MailSourceConnectionProbeService(
                new ProbeMailSessionFactory(state),
                new ProbeConnectionService(),
                new MailSourceFolderService(),
                new ProbeMessageMapper(materializeMessages),
                null);
    }

    private static RuntimeEmailAccount runtimeImapAccount(boolean unreadOnly) {
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
                Optional.of("INBOX, Projects/2026"),
                unreadOnly,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);
    }

    private static RuntimeEmailAccount runtimePop3Account(boolean unreadOnly) {
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
                unreadOnly,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);
    }

    private static final class ProbeMailSessionFactory extends MailSessionFactory {
        private final ProbeStoreState state;

        private ProbeMailSessionFactory(ProbeStoreState state) {
            this.state = state;
        }

        @Override
        public Session sourceImapSession(RuntimeEmailAccount account) {
            return sessionWithProvider("testimap", ProbeStore.class, state);
        }

        @Override
        public Session sourcePop3Session(RuntimeEmailAccount account) {
            return sessionWithProvider("testpop3", ProbeStore.class, state);
        }

        @Override
        public String imapStoreProtocol(boolean tls) {
            return "testimap";
        }

        @Override
        public String pop3StoreProtocol(boolean tls) {
            return "testpop3";
        }

        private Session sessionWithProvider(String protocol, Class<? extends Store> storeType, ProbeStoreState state) {
            Properties properties = new Properties();
            Session session = Session.getInstance(properties);
            session.addProvider(new Provider(Provider.Type.STORE, protocol, storeType.getName(), "InboxBridge", "1.0"));
            ProbeStore.currentState = state;
            return session;
        }
    }

    public static final class ProbeStore extends Store {
        private static ProbeStoreState currentState;

        public ProbeStore(Session session, URLName urlName) {
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
            return currentState.folder(url.getFile());
        }

        @Override
        protected boolean protocolConnect(String host, int port, String user, String password) {
            return true;
        }
    }

    private static final class ProbeConnectionService extends MailSourceConnectionService {
        private ProbeConnectionService() {
            super(null, null);
        }

        @Override
        public void connectStore(Store store, RuntimeEmailAccount bridge) {
            // no-op for fake store
        }
    }

    private static final class ProbeMessageMapper extends MailSourceMessageMapper {
        private final boolean materializeMessages;

        private ProbeMessageMapper(boolean materializeMessages) {
            this.materializeMessages = materializeMessages;
        }

        @Override
        public List<FetchedMessage> toFetchedMessages(String sourceId, Folder folder, Message[] messages) {
            if (!materializeMessages || messages.length == 0) {
                return List.of();
            }
            return List.of(new FetchedMessage(
                    sourceId,
                    sourceId + ":probe",
                    Optional.of("<probe@example.com>"),
                    Instant.parse("2026-04-06T00:00:00Z"),
                    Optional.of(folder.getFullName()),
                    null,
                    null,
                    null,
                    "raw".getBytes()));
        }

        @Override
        public List<FetchedMessage> toFetchedMessages(String sourceId, Message[] messages) {
            if (!materializeMessages || messages.length == 0) {
                return List.of();
            }
            return List.of(new FetchedMessage(
                    sourceId,
                    sourceId + ":probe",
                    Optional.of("<probe@example.com>"),
                    Instant.parse("2026-04-06T00:00:00Z"),
                    "raw".getBytes()));
        }
    }

    private static final class ProbeStoreState {
        private final BareStore supportStore = new BareStore();
        private final Map<String, ProbeFolder> folders = new HashMap<>();

        private ProbeFolder folder(String name) {
            return folders.computeIfAbsent(name, folderName -> new ProbeFolder(supportStore, folderName));
        }
    }

    private static final class ProbeFolder extends Folder {
        private final String name;
        private final List<Message> messages = new ArrayList<>();
        private Flags permanentFlags = new Flags();
        private boolean open;

        private ProbeFolder(Store store, String name) {
            super(store);
            this.name = name;
        }

        private void setMessages(List<ProbeMessage> messages) {
            this.messages.clear();
            for (int index = 0; index < messages.size(); index++) {
                ProbeMessage message = messages.get(index);
                message.bind(this, index + 1);
                this.messages.add(message);
            }
        }

        @Override
        public String getName() {
            int slash = name.lastIndexOf('/');
            return slash >= 0 ? name.substring(slash + 1) : name;
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
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public Flags getPermanentFlags() {
            return permanentFlags;
        }

        @Override
        public int getMessageCount() {
            return messages.size();
        }

        @Override
        public Message getMessage(int msgnum) {
            return messages.get(msgnum - 1);
        }

        @Override
        public Message[] getMessages(int start, int end) {
            return messages.subList(start - 1, end).toArray(Message[]::new);
        }

        @Override
        public Message[] search(jakarta.mail.search.SearchTerm term) {
            return messages.stream().filter(term::match).toArray(Message[]::new);
        }

        @Override
        public void appendMessages(Message[] msgs) {
        }

        @Override
        public Message[] expunge() {
            return new Message[0];
        }

        @Override
        public void fetch(Message[] msgs, FetchProfile fp) {
        }
    }

    private static final class ProbeMessage extends Message {
        private final String subject;
        private final boolean seen;

        private ProbeMessage(String subject, boolean seen) {
            this.subject = subject;
            this.seen = seen;
        }

        private static ProbeMessage read(String subject) {
            return new ProbeMessage(subject, true);
        }

        private static ProbeMessage unread(String subject) {
            return new ProbeMessage(subject, false);
        }

        private void bind(Folder folder, int messageNumber) {
            this.folder = folder;
            this.msgnum = messageNumber;
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
            return subject;
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
            return flags;
        }

        @Override
        public boolean isSet(Flags.Flag flag) {
            return flag == Flags.Flag.SEEN && seen;
        }

        @Override
        public void setFlags(Flags flag, boolean set) {
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
            return 1;
        }

        @Override
        public int getLineCount() {
            return 1;
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
            return subject;
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
            return msgnum;
        }

        @Override
        public void setMessageNumber(int msgnum) {
            this.msgnum = msgnum;
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
}
