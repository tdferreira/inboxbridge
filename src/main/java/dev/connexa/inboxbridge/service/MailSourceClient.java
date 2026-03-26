package dev.connexa.inboxbridge.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import dev.connexa.inboxbridge.config.BridgeConfig;
import dev.connexa.inboxbridge.domain.FetchedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;

@ApplicationScoped
public class MailSourceClient {

    @Inject
    BridgeConfig bridgeConfig;

    @Inject
    MimeHashService mimeHashService;

    public List<FetchedMessage> fetch(BridgeConfig.Source source) {
        return switch (source.protocol()) {
            case IMAP -> fetchImap(source);
            case POP3 -> fetchPop3(source);
        };
    }

    private List<FetchedMessage> fetchImap(BridgeConfig.Source source) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", source.tls() ? "imaps" : "imap");
        properties.put("mail.imap.ssl.enable", source.tls());
        properties.put("mail.imaps.ssl.enable", source.tls());
        properties.put("mail.imap.ssl.checkserveridentity", "true");
        properties.put("mail.imaps.ssl.checkserveridentity", "true");
        properties.put("mail.imap.timeout", "20000");
        properties.put("mail.imaps.timeout", "20000");
        properties.put("mail.imap.connectiontimeout", "20000");
        properties.put("mail.imaps.connectiontimeout", "20000");

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(source.tls() ? "imaps" : "imap");
            store.connect(source.host(), source.port(), source.username(), source.password());
            folder = store.getFolder(source.folder().orElse("INBOX"));
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = source.unreadOnly()
                    ? trimTailMessages(folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)))
                    : selectTailMessages(folder);
            return toFetchedMessages(source, candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch IMAP mail for source " + source.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private List<FetchedMessage> fetchPop3(BridgeConfig.Source source) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", source.tls() ? "pop3s" : "pop3");
        properties.put("mail.pop3.ssl.enable", source.tls());
        properties.put("mail.pop3s.ssl.enable", source.tls());
        properties.put("mail.pop3.ssl.checkserveridentity", "true");
        properties.put("mail.pop3s.ssl.checkserveridentity", "true");
        properties.put("mail.pop3.timeout", "20000");
        properties.put("mail.pop3.connectiontimeout", "20000");
        properties.put("mail.pop3s.timeout", "20000");
        properties.put("mail.pop3s.connectiontimeout", "20000");

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(source.tls() ? "pop3s" : "pop3");
            store.connect(source.host(), source.port(), source.username(), source.password());
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = selectTailMessages(folder);
            return toFetchedMessages(source, candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch POP3 mail for source " + source.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private Message[] selectTailMessages(Folder folder) throws MessagingException {
        int count = folder.getMessageCount();
        if (count == 0) {
            return new Message[0];
        }
        int fetchWindow = Math.max(1, bridgeConfig.fetchWindow());
        int start = Math.max(1, count - fetchWindow + 1);
        return folder.getMessages(start, count);
    }

    private Message[] trimTailMessages(Message[] messages) {
        int fetchWindow = Math.max(1, bridgeConfig.fetchWindow());
        if (messages.length <= fetchWindow) {
            return messages;
        }
        return Arrays.copyOfRange(messages, messages.length - fetchWindow, messages.length);
    }

    private List<FetchedMessage> toFetchedMessages(BridgeConfig.Source source, Message[] messages) {
        List<Message> sorted = new ArrayList<>(Arrays.asList(messages));
        sorted.sort(Comparator.comparing(this::messageInstant));

        List<FetchedMessage> fetchedMessages = new ArrayList<>();
        for (Message message : sorted) {
            try {
                byte[] raw = toRawBytes(message);
                String sha = mimeHashService.sha256Hex(raw);
                String sourceMessageKey = resolveSourceMessageKey(source, message, sha);
                Optional<String> messageId = Optional.ofNullable(firstHeader(message, "Message-ID"));
                fetchedMessages.add(new FetchedMessage(
                        source.id(),
                        sourceMessageKey,
                        messageId,
                        messageInstant(message),
                        raw));
            } catch (MessagingException | IOException e) {
                throw new IllegalStateException("Failed to materialize message from source " + source.id(), e);
            }
        }
        return fetchedMessages;
    }

    private String resolveSourceMessageKey(BridgeConfig.Source source, Message message, String sha) throws MessagingException {
        if (message.getFolder() instanceof UIDFolder uidFolder) {
            long uid = uidFolder.getUID(message);
            if (uid > 0) {
                return source.id() + ":uid:" + uid;
            }
        }
        String messageIdHeader = firstHeader(message, "Message-ID");
        if (messageIdHeader != null && !messageIdHeader.isBlank()) {
            return source.id() + ":message-id:" + messageIdHeader;
        }
        return mimeHashService.fallbackMessageKey(source.id(), sha);
    }

    private String firstHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private byte[] toRawBytes(Message message) throws MessagingException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        return outputStream.toByteArray();
    }

    private Instant messageInstant(Message message) {
        try {
            if (message.getReceivedDate() != null) {
                return message.getReceivedDate().toInstant();
            }
            if (message.getSentDate() != null) {
                return message.getSentDate().toInstant();
            }
            return Instant.EPOCH;
        } catch (MessagingException e) {
            return Instant.EPOCH;
        }
    }

    private void closeQuietly(Folder folder) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (MessagingException ignored) {
            // ignored on shutdown
        }
    }

    private void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // ignored on shutdown
        }
    }

}
