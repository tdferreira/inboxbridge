package dev.inboxbridge.service.mail;

import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.ImapCheckpoint;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.UIDFolder;

class MailSourceCheckpointSelectorTest {

    @Test
    void imapCheckpointSelectionReturnsOnlyMessagesAfterCheckpointUid() throws Exception {
        MailSourceCheckpointSelector selector = new MailSourceCheckpointSelector();
        FakeUidFolder folder = new FakeUidFolder(44L,
                fakeMessage("one"),
                fakeMessage("two"),
                fakeMessage("three"));

        Message[] selected = selector.selectImapCandidateMessages(
                Optional.of(new ImapCheckpoint("INBOX", 44L, 2L)),
                false,
                10,
                folder);

        assertEquals(List.of("three"), subjects(selected));
    }

    @Test
    void imapCheckpointSelectionFiltersUnreadMessagesAfterCheckpointWhenRequested() throws Exception {
        MailSourceCheckpointSelector selector = new MailSourceCheckpointSelector();
        TestMimeMessage seen = fakeMessage("seen");
        seen.setFlag(Flags.Flag.SEEN, true);
        TestMimeMessage unread = fakeMessage("unread");
        FakeUidFolder folder = new FakeUidFolder(44L, seen, unread);

        Message[] selected = selector.selectImapCandidateMessages(
                Optional.of(new ImapCheckpoint("INBOX", 44L, 0L)),
                true,
                10,
                folder);

        assertEquals(List.of("unread"), subjects(selected));
    }

    @Test
    void imapCheckpointSelectionFallsBackToTailWindowWhenUidValidityChanges() throws Exception {
        MailSourceCheckpointSelector selector = new MailSourceCheckpointSelector();
        FakeUidFolder folder = new FakeUidFolder(45L,
                fakeMessage("one"),
                fakeMessage("two"),
                fakeMessage("three"));

        Message[] selected = selector.selectImapCandidateMessages(
                Optional.of(new ImapCheckpoint("INBOX", 44L, 2L)),
                false,
                2,
                folder);

        assertEquals(List.of("two", "three"), subjects(selected));
    }

    private static TestMimeMessage fakeMessage(String subject) throws MessagingException {
        TestMimeMessage message = new TestMimeMessage();
        message.setSubject(subject);
        return message;
    }

    private static List<String> subjects(Message[] messages) throws MessagingException {
        return java.util.Arrays.stream(messages)
                .map(message -> {
                    try {
                        return message.getSubject();
                    } catch (MessagingException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    private static final class FakeUidFolder extends Folder implements UIDFolder {
        private final long uidValidity;
        private final TestMimeMessage[] messages;

        private FakeUidFolder(long uidValidity, TestMimeMessage... messages) {
            super(new FakeStore());
            this.uidValidity = uidValidity;
            this.messages = messages;
            for (int index = 0; index < messages.length; index++) {
                messages[index].bind(this, index + 1);
            }
        }

        @Override
        public String getName() {
            return "INBOX";
        }

        @Override
        public String getFullName() {
            return "INBOX";
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
        }

        @Override
        public void close(boolean expunge) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public Flags getPermanentFlags() {
            return new Flags();
        }

        @Override
        public int getMessageCount() {
            return messages.length;
        }

        @Override
        public Message getMessage(int msgnum) {
            return messages[msgnum - 1];
        }

        @Override
        public Message[] getMessages(int start, int end) {
            return java.util.Arrays.copyOfRange(messages, start - 1, end);
        }

        @Override
        public Message[] search(SearchTerm term) throws MessagingException {
            java.util.List<Message> matches = new java.util.ArrayList<>();
            for (Message message : messages) {
                if (term.match(message)) {
                    matches.add(message);
                }
            }
            return matches.toArray(Message[]::new);
        }

        @Override
        public void appendMessages(Message[] msgs) {
        }

        @Override
        public Message[] expunge() {
            return new Message[0];
        }

        @Override
        public long getUIDValidity() {
            return uidValidity;
        }

        @Override
        public Message getMessageByUID(long uid) {
            int index = (int) uid - 1;
            return index >= 0 && index < messages.length ? messages[index] : null;
        }

        @Override
        public Message[] getMessagesByUID(long start, long end) {
            int normalizedStart = (int) Math.max(1L, start);
            int normalizedEnd = end == LASTUID ? messages.length : (int) Math.min(end, messages.length);
            if (normalizedStart > normalizedEnd) {
                return new Message[0];
            }
            return java.util.Arrays.copyOfRange(messages, normalizedStart - 1, normalizedEnd);
        }

        @Override
        public Message[] getMessagesByUID(long[] uids) {
            return java.util.Arrays.stream(uids)
                    .mapToObj(this::getMessageByUID)
                    .filter(java.util.Objects::nonNull)
                    .toArray(Message[]::new);
        }

        @Override
        public long getUID(Message message) {
            return message.getMessageNumber();
        }

        @Override
        public long getUIDNext() {
            return messages.length + 1L;
        }
    }

    private static final class TestMimeMessage extends MimeMessage {
        private TestMimeMessage() {
            super(Session.getInstance(new Properties()));
        }

        private void bind(Folder folder, int messageNumber) {
            this.folder = folder;
            this.msgnum = messageNumber;
        }
    }

    private static final class FakeStore extends Store {
        private FakeStore() {
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
