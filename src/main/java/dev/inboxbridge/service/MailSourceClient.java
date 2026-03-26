package dev.inboxbridge.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeBridge;
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

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    public List<FetchedMessage> fetch(BridgeConfig.Source source) {
        return switch (source.protocol()) {
            case IMAP -> fetchImap(source);
            case POP3 -> fetchPop3(source);
        };
    }

    public List<FetchedMessage> fetch(RuntimeBridge bridge) {
        return switch (bridge.protocol()) {
            case IMAP -> fetchImap(bridge);
            case POP3 -> fetchPop3(bridge);
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
        if (usesMicrosoftOAuth(source)) {
            configureImapMicrosoftOAuth(properties);
        } else {
            requireSupportedAuth(source);
        }

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(source.tls() ? "imaps" : "imap");
            connectStore(store, source);
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

    private List<FetchedMessage> fetchImap(RuntimeBridge bridge) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", bridge.tls() ? "imaps" : "imap");
        properties.put("mail.imap.ssl.enable", bridge.tls());
        properties.put("mail.imaps.ssl.enable", bridge.tls());
        properties.put("mail.imap.ssl.checkserveridentity", "true");
        properties.put("mail.imaps.ssl.checkserveridentity", "true");
        properties.put("mail.imap.timeout", "20000");
        properties.put("mail.imaps.timeout", "20000");
        properties.put("mail.imap.connectiontimeout", "20000");
        properties.put("mail.imaps.connectiontimeout", "20000");
        if (usesMicrosoftOAuth(bridge)) {
            configureImapMicrosoftOAuth(properties);
        } else {
            requireSupportedAuth(bridge);
        }

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(bridge.tls() ? "imaps" : "imap");
            connectStore(store, bridge);
            folder = store.getFolder(bridge.folder().orElse("INBOX"));
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = bridge.unreadOnly()
                    ? trimTailMessages(folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)))
                    : selectTailMessages(folder);
            return toFetchedMessages(bridge.id(), candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch IMAP mail for source " + bridge.id(), e);
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
        if (usesMicrosoftOAuth(source)) {
            configurePop3MicrosoftOAuth(properties);
        } else {
            requireSupportedAuth(source);
        }

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(source.tls() ? "pop3s" : "pop3");
            connectStore(store, source);
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

    private List<FetchedMessage> fetchPop3(RuntimeBridge bridge) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", bridge.tls() ? "pop3s" : "pop3");
        properties.put("mail.pop3.ssl.enable", bridge.tls());
        properties.put("mail.pop3s.ssl.enable", bridge.tls());
        properties.put("mail.pop3.ssl.checkserveridentity", "true");
        properties.put("mail.pop3s.ssl.checkserveridentity", "true");
        properties.put("mail.pop3.timeout", "20000");
        properties.put("mail.pop3.connectiontimeout", "20000");
        properties.put("mail.pop3s.timeout", "20000");
        properties.put("mail.pop3s.connectiontimeout", "20000");
        if (usesMicrosoftOAuth(bridge)) {
            configurePop3MicrosoftOAuth(properties);
        } else {
            requireSupportedAuth(bridge);
        }

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(bridge.tls() ? "pop3s" : "pop3");
            connectStore(store, bridge);
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = selectTailMessages(folder);
            return toFetchedMessages(bridge.id(), candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch POP3 mail for source " + bridge.id(), e);
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
        return toFetchedMessages(source.id(), messages);
    }

    private List<FetchedMessage> toFetchedMessages(String sourceId, Message[] messages) {
        List<Message> sorted = new ArrayList<>(Arrays.asList(messages));
        sorted.sort(Comparator.comparing(this::messageInstant));

        List<FetchedMessage> fetchedMessages = new ArrayList<>();
        for (Message message : sorted) {
            try {
                byte[] raw = toRawBytes(message);
                String sha = mimeHashService.sha256Hex(raw);
                String sourceMessageKey = resolveSourceMessageKey(sourceId, message, sha);
                Optional<String> messageId = Optional.ofNullable(firstHeader(message, "Message-ID"));
                fetchedMessages.add(new FetchedMessage(
                        sourceId,
                        sourceMessageKey,
                        messageId,
                        messageInstant(message),
                        raw));
            } catch (MessagingException | IOException e) {
                throw new IllegalStateException("Failed to materialize message from source " + sourceId, e);
            }
        }
        return fetchedMessages;
    }

    private String resolveSourceMessageKey(BridgeConfig.Source source, Message message, String sha) throws MessagingException {
        return resolveSourceMessageKey(source.id(), message, sha);
    }

    private String resolveSourceMessageKey(String sourceId, Message message, String sha) throws MessagingException {
        if (message.getFolder() instanceof UIDFolder uidFolder) {
            long uid = uidFolder.getUID(message);
            if (uid > 0) {
                return sourceId + ":uid:" + uid;
            }
        }
        String messageIdHeader = firstHeader(message, "Message-ID");
        if (messageIdHeader != null && !messageIdHeader.isBlank()) {
            return sourceId + ":message-id:" + messageIdHeader;
        }
        return mimeHashService.fallbackMessageKey(sourceId, sha);
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

    private void connectStore(Store store, BridgeConfig.Source source) throws MessagingException {
        if (usesMicrosoftOAuth(source)) {
            String accessToken = microsoftOAuthService.getAccessToken(source);
            store.connect(source.host(), source.port(), source.username(), accessToken);
            return;
        }
        store.connect(source.host(), source.port(), source.username(), source.password());
    }

    private void connectStore(Store store, RuntimeBridge bridge) throws MessagingException {
        if (usesMicrosoftOAuth(bridge)) {
            String accessToken = microsoftOAuthService.getAccessToken(bridge);
            store.connect(bridge.host(), bridge.port(), bridge.username(), accessToken);
            return;
        }
        store.connect(bridge.host(), bridge.port(), bridge.username(), bridge.password());
    }

    private boolean usesMicrosoftOAuth(BridgeConfig.Source source) {
        return source.authMethod() == BridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == BridgeConfig.OAuthProvider.MICROSOFT;
    }

    private boolean usesMicrosoftOAuth(RuntimeBridge bridge) {
        return bridge.authMethod() == BridgeConfig.AuthMethod.OAUTH2
                && bridge.oauthProvider() == BridgeConfig.OAuthProvider.MICROSOFT;
    }

    private void requireSupportedAuth(BridgeConfig.Source source) {
        if (source.authMethod() == BridgeConfig.AuthMethod.OAUTH2) {
            throw new IllegalStateException("OAuth2 is only implemented for Microsoft sources at the moment");
        }
    }

    private void requireSupportedAuth(RuntimeBridge bridge) {
        if (bridge.authMethod() == BridgeConfig.AuthMethod.OAUTH2) {
            throw new IllegalStateException("OAuth2 is only implemented for Microsoft sources at the moment");
        }
    }

    private void configureImapMicrosoftOAuth(Properties properties) {
        properties.put("mail.imap.auth.mechanisms", "XOAUTH2");
        properties.put("mail.imaps.auth.mechanisms", "XOAUTH2");
        properties.put("mail.imap.auth.login.disable", "true");
        properties.put("mail.imaps.auth.login.disable", "true");
        properties.put("mail.imap.auth.plain.disable", "true");
        properties.put("mail.imaps.auth.plain.disable", "true");
        properties.put("mail.imap.auth.xoauth2.disable", "false");
        properties.put("mail.imaps.auth.xoauth2.disable", "false");
    }

    private void configurePop3MicrosoftOAuth(Properties properties) {
        properties.put("mail.pop3.auth.mechanisms", "XOAUTH2");
        properties.put("mail.pop3s.auth.mechanisms", "XOAUTH2");
        properties.put("mail.pop3.auth.login.disable", "true");
        properties.put("mail.pop3s.auth.login.disable", "true");
        properties.put("mail.pop3.auth.plain.disable", "true");
        properties.put("mail.pop3s.auth.plain.disable", "true");
        properties.put("mail.pop3.auth.xoauth2.disable", "false");
        properties.put("mail.pop3s.auth.xoauth2.disable", "false");
        properties.put("mail.pop3.auth.xoauth2.two.line.authentication.format", "true");
        properties.put("mail.pop3s.auth.xoauth2.two.line.authentication.format", "true");
    }

}
