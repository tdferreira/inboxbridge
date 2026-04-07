package dev.inboxbridge.service.mail;

import dev.inboxbridge.service.destination.*;

import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.inboxbridge.domain.ImapCheckpoint;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;

class MailSourceFetchServiceTest {

    @Test
    void runtimeImapFetchReadsDestinationScopedCheckpointsAndSortsMessagesAcrossFolders() {
        FetchStoreState state = new FetchStoreState();
        state.folder("INBOX").setMessages(List.of(
                new FetchMessage("inbox-late", Instant.parse("2026-04-06T12:00:00Z")),
                new FetchMessage("inbox-latest", Instant.parse("2026-04-06T13:00:00Z"))));
        state.folder("Projects/2026").setMessages(List.of(
                new FetchMessage("project-early", Instant.parse("2026-04-06T11:00:00Z"))));

        RecordingSourcePollingStateService pollingStateService = new RecordingSourcePollingStateService();
        RecordingCheckpointSelector checkpointSelector = new RecordingCheckpointSelector();
        MailSourceFetchService service = service(state, pollingStateService, checkpointSelector);
        RuntimeEmailAccount account = runtimeImapAccount();
        String destinationKey = DestinationIdentityKeys.forTarget(account.destination());

        List<FetchedMessage> fetched = service.fetch(account, 10);

        assertEquals(List.of("project-early", "inbox-late", "inbox-latest"), subjects(fetched));
        assertEquals(
                List.of("source-1|" + destinationKey + "|INBOX", "source-1|" + destinationKey + "|Projects/2026"),
                pollingStateService.imapCheckpointRequests);
        assertEquals(List.of("INBOX", "Projects/2026"), checkpointSelector.imapFolderRequests);
    }

    @Test
    void runtimePopFetchUsesDestinationScopedCheckpoint() {
        FetchStoreState state = new FetchStoreState();
        state.folder("INBOX").setMessages(List.of(
                new FetchMessage("alpha", Instant.parse("2026-04-06T10:00:00Z")),
                new FetchMessage("beta", Instant.parse("2026-04-06T11:00:00Z"))));

        RecordingSourcePollingStateService pollingStateService = new RecordingSourcePollingStateService();
        RecordingCheckpointSelector checkpointSelector = new RecordingCheckpointSelector();
        MailSourceFetchService service = service(state, pollingStateService, checkpointSelector);
        RuntimeEmailAccount account = runtimePop3Account();
        String destinationKey = DestinationIdentityKeys.forTarget(account.destination());

        List<FetchedMessage> fetched = service.fetch(account, 10);

        assertEquals(List.of("alpha", "beta"), subjects(fetched));
        assertEquals(List.of("source-pop|" + destinationKey), pollingStateService.popCheckpointRequests);
        assertEquals(List.of(Optional.of("pop-checkpoint")), checkpointSelector.popCheckpoints);
    }

    private static MailSourceFetchService service(
            FetchStoreState state,
            RecordingSourcePollingStateService pollingStateService,
            RecordingCheckpointSelector checkpointSelector) {
        return new MailSourceFetchService(
                new FetchMailSessionFactory(state),
                new FetchConnectionService(),
                checkpointSelector,
                new FetchMessageMapper(),
                pollingStateService,
                null);
    }

    private static List<String> subjects(List<FetchedMessage> messages) {
        return messages.stream().map(message -> new String(message.rawMessage())).toList();
    }

    private static RuntimeEmailAccount runtimeImapAccount() {
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
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                destinationTarget("user-destination:7"));
    }

    private static RuntimeEmailAccount runtimePop3Account() {
        return new RuntimeEmailAccount(
                "source-pop",
                "USER",
                8L,
                "bob",
                true,
                InboxBridgeConfig.Protocol.POP3,
                "pop.example.test",
                995,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "bob@example.test",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                destinationTarget("user-destination:8"));
    }

    private static ImapAppendDestinationTarget destinationTarget(String subjectKey) {
        return new ImapAppendDestinationTarget(
                subjectKey,
                99L,
                "owner",
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

    private static final class RecordingSourcePollingStateService extends SourcePollingStateService {
        private final List<String> imapCheckpointRequests = new ArrayList<>();
        private final List<String> popCheckpointRequests = new ArrayList<>();

        @Override
        public Optional<ImapCheckpoint> imapCheckpoint(String sourceId, String destinationKey, String folderName) {
            imapCheckpointRequests.add(sourceId + "|" + destinationKey + "|" + folderName);
            return Optional.of(new ImapCheckpoint(folderName, 44L, 20L));
        }

        @Override
        public Optional<String> popCheckpoint(String sourceId, String destinationKey) {
            popCheckpointRequests.add(sourceId + "|" + destinationKey);
            return Optional.of("pop-checkpoint");
        }
    }

    private static final class RecordingCheckpointSelector extends MailSourceCheckpointSelector {
        private final List<String> imapFolderRequests = new ArrayList<>();
        private final List<Optional<String>> popCheckpoints = new ArrayList<>();

        @Override
        public Message[] selectImapCandidateMessages(
                Optional<ImapCheckpoint> checkpoint,
                boolean unreadOnly,
                int fetchWindow,
                Folder folder) {
            imapFolderRequests.add(folder.getFullName());
            return ((FetchFolder) folder).messages.toArray(Message[]::new);
        }

        @Override
        public Message[] selectPop3CandidateMessages(
                Optional<String> checkpoint,
                int fetchWindow,
                Folder folder) {
            popCheckpoints.add(checkpoint);
            return ((FetchFolder) folder).messages.toArray(Message[]::new);
        }
    }

    private static final class FetchMailSessionFactory extends MailSessionFactory {
        private final FetchStoreState state;

        private FetchMailSessionFactory(FetchStoreState state) {
            this.state = state;
        }

        @Override
        public Session sourceImapSession(RuntimeEmailAccount account) {
            return sessionWithProvider("testimap", state);
        }

        @Override
        public Session sourcePop3Session(RuntimeEmailAccount account) {
            return sessionWithProvider("testpop3", state);
        }

        @Override
        public Session sourceImapSession(InboxBridgeConfig.Source source) {
            return sessionWithProvider("testimap", state);
        }

        @Override
        public Session sourcePop3Session(InboxBridgeConfig.Source source) {
            return sessionWithProvider("testpop3", state);
        }

        @Override
        public String imapStoreProtocol(boolean tls) {
            return "testimap";
        }

        @Override
        public String pop3StoreProtocol(boolean tls) {
            return "testpop3";
        }

        private Session sessionWithProvider(String protocol, FetchStoreState state) {
            Properties properties = new Properties();
            Session session = Session.getInstance(properties);
            session.addProvider(new Provider(Provider.Type.STORE, protocol, FetchStore.class.getName(), "InboxBridge", "1.0"));
            FetchStore.currentState = state;
            return session;
        }
    }

    public static final class FetchStore extends Store {
        private static FetchStoreState currentState;

        public FetchStore(Session session, URLName urlName) {
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

    private static final class FetchConnectionService extends MailSourceConnectionService {
        private FetchConnectionService() {
            super(null, null);
        }

        @Override
        public void connectStore(Store store, RuntimeEmailAccount bridge) {
            // no-op
        }

        @Override
        public void connectStore(Store store, InboxBridgeConfig.Source source) {
            // no-op
        }
    }

    private static final class FetchMessageMapper extends MailSourceMessageMapper {
        @Override
        public List<FetchedMessage> toFetchedMessages(String sourceId, Folder folder, Message[] messages) {
            return java.util.Arrays.stream(messages)
                    .map(message -> {
                        FetchMessage fetchMessage = (FetchMessage) message;
                        return new FetchedMessage(
                                sourceId,
                                sourceId + ":" + fetchMessage.subject,
                                Optional.of("<" + fetchMessage.subject + "@example.com>"),
                                fetchMessage.instant,
                                Optional.of(folder.getFullName()),
                                null,
                                null,
                                null,
                                fetchMessage.subject.getBytes());
                    })
                    .toList();
        }

        @Override
        public List<FetchedMessage> toFetchedMessages(String sourceId, Message[] messages) {
            return java.util.Arrays.stream(messages)
                    .map(message -> {
                        FetchMessage fetchMessage = (FetchMessage) message;
                        return new FetchedMessage(
                                sourceId,
                                sourceId + ":" + fetchMessage.subject,
                                Optional.of("<" + fetchMessage.subject + "@example.com>"),
                                fetchMessage.instant,
                                fetchMessage.subject.getBytes());
                    })
                    .toList();
        }
    }

    private static final class FetchStoreState {
        private final BareStore supportStore = new BareStore();
        private final Map<String, FetchFolder> folders = new HashMap<>();

        private FetchFolder folder(String name) {
            return folders.computeIfAbsent(name, folderName -> new FetchFolder(supportStore, folderName));
        }
    }

    private static final class FetchFolder extends Folder {
        private final String name;
        private final List<Message> messages = new ArrayList<>();
        private boolean open;

        private FetchFolder(Store store, String name) {
            super(store);
            this.name = name;
        }

        private void setMessages(List<FetchMessage> messages) {
            this.messages.clear();
            for (int index = 0; index < messages.size(); index++) {
                FetchMessage message = messages.get(index);
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
            return new Flags();
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

    private static final class FetchMessage extends Message {
        private final String subject;
        private final Instant instant;

        private FetchMessage(String subject, Instant instant) {
            this.subject = subject;
            this.instant = instant;
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
            return java.util.Date.from(instant);
        }

        @Override
        public void setSentDate(java.util.Date date) {
        }

        @Override
        public java.util.Date getReceivedDate() {
            return java.util.Date.from(instant);
        }

        @Override
        public Flags getFlags() {
            return new Flags();
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
