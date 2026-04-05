package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.URLName;
import jakarta.mail.Session;

class MailSourceClientTest {

    @Test
    void retryableMicrosoftOAuthFailureRecognizesInvalidSessionMessages() {
        MessagingException error = new MessagingException(
                "* BYE Session invalidated - Invalid",
                new MessagingException("AUTHENTICATE failed"));

        assertTrue(MailSourceClient.isRetryableMicrosoftOAuthFailure(error));
    }

    @Test
    void retryableMicrosoftOAuthFailureIgnoresUnrelatedErrors() {
        MessagingException error = new MessagingException("Connection timed out");

        assertFalse(MailSourceClient.isRetryableMicrosoftOAuthFailure(error));
    }

    @Test
    void retryableMicrosoftOAuthFailureDoesNotTreatGenericInvalidMessagesAsOauthFailures() {
        MessagingException error = new MessagingException("Invalid folder name");

        assertFalse(MailSourceClient.isRetryableMicrosoftOAuthFailure(error));
    }

    @Test
    void toRawBytesRetriesAfterFolderClosedException() throws Exception {
        MailSourceClient client = new MailSourceClient();
        FakeFolder folder = new FakeFolder();
        FakeMessage message = new FakeMessage(folder, "hello world".getBytes());
        folder.setMessage(message);
        folder.forceClosed();

        byte[] raw = invokeToRawBytes(client, message);

        assertArrayEquals("hello world".getBytes(), raw);
    }

    @Test
    void toRawBytesFailsWhenFolderCannotBeReopened() {
        MailSourceClient client = new MailSourceClient();
        FakeFolder folder = new FakeFolder();
        folder.failOnOpen = true;
        FakeMessage message = new FakeMessage(folder, "hello world".getBytes());
        folder.setMessage(message);
        folder.forceClosed();

        assertThrows(FolderClosedException.class, () -> invokeToRawBytes(client, message));
    }

    @Test
    void locateSpamOrJunkFolderPrefersImapSpecialUseAttributes() throws Exception {
        MailSourceClient client = new MailSourceClient();
        Folder root = new TreeFolder("", List.of(
                new SpecialUseFolder("Trash", List.of("\\Trash")),
                new SpecialUseFolder("Whatever", List.of("\\Junk")),
                new TreeFolder("Spam", List.of())
        ));
        FakeTreeStore store = new FakeTreeStore(root);

        Folder folder = invokeLocateSpamOrJunkFolder(client, store);

        assertTrue(folder instanceof SpecialUseFolder);
        assertTrue("Whatever".equals(folder.getFullName()));
    }

    @Test
    void locateSpamOrJunkFolderFallsBackToLocalizedNameMatching() throws Exception {
        MailSourceClient client = new MailSourceClient();
        Folder root = new TreeFolder("", List.of(
                new TreeFolder("Arquivo", List.of()),
                new TreeFolder("Correo no deseado", List.of())
        ));
        FakeTreeStore store = new FakeTreeStore(root);

        Folder folder = invokeLocateSpamOrJunkFolder(client, store);

        assertTrue(folder instanceof TreeFolder);
        assertTrue("Correo no deseado".equals(folder.getFullName()));
    }

    @Test
    void resolveForwardedMarkerSupportAcceptsExplicitForwardedFlag() {
        Flags permanentFlags = new Flags();
        permanentFlags.add("$Forwarded");

        assertTrue(MailSourceClient.resolveForwardedMarkerSupport(new PermanentFlagsFolder(permanentFlags)));
    }

    @Test
    void resolveForwardedMarkerSupportAcceptsGenericUserFlags() {
        Flags permanentFlags = new Flags();
        permanentFlags.add(Flags.Flag.USER);

        assertTrue(MailSourceClient.resolveForwardedMarkerSupport(new PermanentFlagsFolder(permanentFlags)));
    }

    @Test
    void resolveForwardedMarkerSupportReturnsFalseWhenUserFlagsAreUnavailable() {
        assertFalse(MailSourceClient.resolveForwardedMarkerSupport(new PermanentFlagsFolder(new Flags())));
    }

    private byte[] invokeToRawBytes(MailSourceClient client, Message message) throws Exception {
        Method method = MailSourceClient.class.getDeclaredMethod("toRawBytes", Message.class);
        method.setAccessible(true);
        try {
            return (byte[]) method.invoke(client, message);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private Folder invokeLocateSpamOrJunkFolder(MailSourceClient client, Store store) throws Exception {
        Method method = MailSourceClient.class.getDeclaredMethod("locateSpamOrJunkFolder", Store.class);
        method.setAccessible(true);
        try {
            return (Folder) method.invoke(client, store);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static final class FakeFolder extends Folder {
        private boolean open = true;
        private FakeMessage message;
        private boolean failOnOpen;

        private FakeFolder() {
            super(new FakeStore());
        }

        private void setMessage(FakeMessage message) {
            this.message = message;
        }

        private void forceClosed() {
            this.open = false;
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
        public void open(int mode) throws MessagingException {
            if (failOnOpen) {
                throw new MessagingException("boom");
            }
            this.open = true;
        }

        @Override
        public void close(boolean expunge) {
            this.open = false;
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
            throw new UnsupportedOperationException();
        }

        @Override
        public Message[] expunge() {
            return new Message[0];
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

    private static final class FakeTreeStore extends Store {
        private final Folder defaultFolder;

        private FakeTreeStore(Folder defaultFolder) {
            super(Session.getInstance(new Properties()), (URLName) null);
            this.defaultFolder = defaultFolder;
        }

        @Override
        public Folder getDefaultFolder() {
            return defaultFolder;
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

    private static class TreeFolder extends Folder {
        private final String fullName;
        private final List<Folder> children = new ArrayList<>();

        private TreeFolder(String fullName, List<? extends Folder> children) {
            super(new FakeStore());
            this.fullName = fullName;
            this.children.addAll(children);
        }

        @Override
        public String getName() {
            if (fullName == null || fullName.isBlank()) {
                return "";
            }
            int slash = fullName.lastIndexOf('/');
            return slash >= 0 ? fullName.substring(slash + 1) : fullName;
        }

        @Override
        public String getFullName() {
            return fullName;
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
            return children.toArray(Folder[]::new);
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
            return false;
        }

        @Override
        public Flags getPermanentFlags() {
            return new Flags();
        }

        @Override
        public int getMessageCount() {
            return 0;
        }

        @Override
        public Message getMessage(int msgnum) {
            return null;
        }

        @Override
        public void appendMessages(Message[] msgs) {
        }

        @Override
        public Message[] expunge() {
            return new Message[0];
        }
    }

    private static final class SpecialUseFolder extends TreeFolder {
        private final String[] attributes;

        private SpecialUseFolder(String fullName, List<String> attributes) {
            super(fullName, List.of());
            this.attributes = attributes.toArray(String[]::new);
        }

        public String[] getAttributes() {
            return attributes;
        }
    }

    private static final class PermanentFlagsFolder extends TreeFolder {
        private final Flags permanentFlags;

        private PermanentFlagsFolder(Flags permanentFlags) {
            super("INBOX", List.of());
            this.permanentFlags = permanentFlags;
        }

        @Override
        public Flags getPermanentFlags() {
            return permanentFlags;
        }
    }


    private static final class FakeMessage extends Message {
        private final FakeFolder folder;
        private final byte[] payload;

        private FakeMessage(FakeFolder folder, byte[] payload) {
            this.folder = folder;
            this.payload = payload;
        }

        @Override
        public Address[] getFrom() {
            return new Address[0];
        }

        @Override
        public void setFrom() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFrom(Address address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addFrom(Address[] addresses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Address[] getRecipients(RecipientType type) {
            return new Address[0];
        }

        @Override
        public void setRecipients(RecipientType type, Address[] addresses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addRecipients(RecipientType type, Address[] addresses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Address[] getReplyTo() {
            return new Address[0];
        }

        @Override
        public void setReplyTo(Address[] addresses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSubject() {
            return "subject";
        }

        @Override
        public void setSubject(String subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getSentDate() {
            return new Date();
        }

        @Override
        public void setSentDate(Date date) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getReceivedDate() {
            return new Date();
        }

        @Override
        public Flags getFlags() {
            return new Flags();
        }

        @Override
        public void setFlags(Flags flag, boolean set) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Message reply(boolean replyToAll) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveChanges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSize() {
            return payload.length;
        }

        @Override
        public int getLineCount() {
            return 1;
        }

        @Override
        public String getContentType() {
            return "message/rfc822";
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
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public void setDescription(String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public void setFileName(String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public Object getContent() {
            return payload;
        }

        @Override
        public void setDataHandler(jakarta.activation.DataHandler dh) {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.activation.DataHandler getDataHandler() {
            return null;
        }

        @Override
        public void setContent(Object obj, String type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setText(String text) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContent(jakarta.mail.Multipart mp) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(java.io.OutputStream os) throws MessagingException {
            if (!folder.isOpen()) {
                throw new FolderClosedException(folder);
            }
            try {
                os.write(payload);
            } catch (java.io.IOException e) {
                throw new MessagingException("write failed", e);
            }
        }

        @Override
        public String[] getHeader(String header_name) {
            return null;
        }

        @Override
        public void setHeader(String header_name, String header_value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addHeader(String header_name, String header_value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeHeader(String header_name) {
            throw new UnsupportedOperationException();
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
