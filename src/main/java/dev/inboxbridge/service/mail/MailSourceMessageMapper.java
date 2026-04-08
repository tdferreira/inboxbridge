package dev.inboxbridge.service.mail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.pop3.POP3Folder;
import org.jboss.logging.Logger;

import dev.inboxbridge.domain.FetchedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.HeaderTerm;

/**
 * Materializes fetched source messages and keeps source-identity key handling
 * in one place so polling code does not need to know mail-protocol details.
 */
@ApplicationScoped
public class MailSourceMessageMapper {

    private static final Logger LOG = Logger.getLogger(MailSourceMessageMapper.class);

    @Inject
    MimeHashService mimeHashService;

    public List<FetchedMessage> toFetchedMessages(String sourceId, Folder folder, Message[] messages) {
        List<Message> sorted = new ArrayList<>(Arrays.asList(messages));
        if (!(folder instanceof POP3Folder)) {
            sorted.sort(Comparator.comparing(this::messageInstant));
        }

        List<FetchedMessage> fetchedMessages = new ArrayList<>();
        Exception firstFailure = null;
        int failedMessages = 0;
        String folderName = safeFolderName(folder);
        Long uidValidity = resolveUidValidity(folder);
        for (Message message : sorted) {
            try {
                MessageMetadata metadata = captureMetadata(message);
                byte[] raw = toRawBytes(message);
                String sha = mimeHashService().sha256Hex(raw);
                String sourceMessageKey = resolveSourceMessageKey(sourceId, metadata, sha);
                Optional<String> messageId = Optional.ofNullable(metadata.messageId());
                fetchedMessages.add(new FetchedMessage(
                        sourceId,
                        sourceMessageKey,
                        messageId,
                        metadata.instant(),
                        Optional.ofNullable(folderName),
                        uidValidity,
                        metadata.uid(),
                        metadata.popUidl(),
                        raw));
            } catch (MessagingException | IOException e) {
                failedMessages++;
                if (firstFailure == null) {
                    firstFailure = e;
                }
                LOG.warnf(e,
                        "Skipping message %s from source %s after materialization failure",
                        safeMessageNumber(message),
                        sourceId);
            }
        }
        if (failedMessages > 0 && fetchedMessages.isEmpty() && firstFailure != null) {
            throw new IllegalStateException("Failed to materialize message from source " + sourceId, firstFailure);
        }
        if (failedMessages > 0) {
            LOG.warnf(
                    "Skipped %d message(s) from source %s because they could not be materialized",
                    failedMessages,
                    sourceId);
        }
        return fetchedMessages;
    }

    public List<FetchedMessage> toFetchedMessages(String sourceId, Message[] messages) {
        return toFetchedMessages(sourceId, inferredFolder(messages), messages);
    }

    public Message resolveSourceMessage(Folder folder, FetchedMessage message) throws MessagingException {
        Long imapUid = extractImapUidFromSourceKey(message);
        if (imapUid != null && folder instanceof UIDFolder uidFolder) {
            return uidFolder.getMessageByUID(imapUid);
        }
        Optional<String> messageId = message.messageIdHeader()
                .filter(header -> !header.isBlank())
                .or(() -> extractMessageIdFromSourceKey(message));
        if (messageId.isEmpty()) {
            return null;
        }
        Message[] matches = folder.search(new HeaderTerm("Message-ID", messageId.get()));
        if (matches.length == 0) {
            return null;
        }
        prefetchMessageMetadata(folder, matches);
        return matches[matches.length - 1];
    }

    String imapSourceMessageKey(String sourceId, String folderName, Long uidValidity, long uid) {
        if (folderName != null && !folderName.isBlank() && uidValidity != null && uidValidity > 0L) {
            return sourceId + ":imap-folder-uid:" + encodeFolderName(folderName) + ":" + uidValidity + ":" + uid;
        }
        if (uidValidity != null && uidValidity > 0L) {
            return sourceId + ":imap-uid:" + uidValidity + ":" + uid;
        }
        return sourceId + ":uid:" + uid;
    }

    Long extractImapUidFromSourceKey(FetchedMessage message) {
        String sourceMessageKey = message.sourceMessageKey();
        if (sourceMessageKey == null) {
            return null;
        }
        String imapFolderUidPrefix = message.sourceAccountId() + ":imap-folder-uid:";
        if (sourceMessageKey.startsWith(imapFolderUidPrefix)) {
            int separatorIndex = sourceMessageKey.lastIndexOf(':');
            if (separatorIndex > imapFolderUidPrefix.length()) {
                try {
                    return Long.parseLong(sourceMessageKey.substring(separatorIndex + 1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        String imapUidPrefix = message.sourceAccountId() + ":imap-uid:";
        if (sourceMessageKey.startsWith(imapUidPrefix)) {
            int separatorIndex = sourceMessageKey.lastIndexOf(':');
            if (separatorIndex > imapUidPrefix.length()) {
                try {
                    return Long.parseLong(sourceMessageKey.substring(separatorIndex + 1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        String legacyUidPrefix = message.sourceAccountId() + ":uid:";
        if (!sourceMessageKey.startsWith(legacyUidPrefix)) {
            return null;
        }
        try {
            return Long.parseLong(sourceMessageKey.substring(legacyUidPrefix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Optional<String> extractMessageIdFromSourceKey(FetchedMessage message) {
        String sourceMessageKey = message.sourceMessageKey();
        String messageIdPrefix = message.sourceAccountId() + ":message-id:";
        if (sourceMessageKey == null || !sourceMessageKey.startsWith(messageIdPrefix)) {
            return Optional.empty();
        }
        return Optional.of(sourceMessageKey.substring(messageIdPrefix.length()));
    }

    private MessageMetadata captureMetadata(Message message) {
        Long uid = null;
        String popUidl = null;
        String messageId = null;
        try {
            if (message.getFolder() instanceof UIDFolder uidFolder) {
                long resolved = uidFolder.getUID(message);
                if (resolved > 0) {
                    uid = resolved;
                }
            }
        } catch (MessagingException e) {
            LOG.debugf(e, "Unable to resolve UID for message %s", safeMessageNumber(message));
        }
        try {
            if (message.getFolder() instanceof POP3Folder pop3Folder) {
                popUidl = pop3Folder.getUID(message);
            }
        } catch (MessagingException e) {
            LOG.debugf(e, "Unable to resolve POP UIDL for message %s", safeMessageNumber(message));
        }
        try {
            messageId = firstHeader(message, "Message-ID");
        } catch (MessagingException e) {
            LOG.debugf(e, "Unable to resolve Message-ID for message %s", safeMessageNumber(message));
        }
        return new MessageMetadata(uid, resolveUidValidity(message.getFolder()), popUidl, messageId, messageInstant(message), safeFolderName(message.getFolder()));
    }

    private String resolveSourceMessageKey(String sourceId, MessageMetadata metadata, String sha) {
        if (metadata.uid() != null && metadata.uid() > 0) {
            return imapSourceMessageKey(sourceId, metadata.folderName(), metadata.uidValidity(), metadata.uid());
        }
        if (metadata.popUidl() != null && !metadata.popUidl().isBlank()) {
            return sourceId + ":uidl:" + metadata.popUidl();
        }
        if (metadata.messageId() != null && !metadata.messageId().isBlank()) {
            return sourceId + ":message-id:" + metadata.messageId();
        }
        return mimeHashService().fallbackMessageKey(sourceId, sha);
    }

    private String encodeFolderName(String folderName) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(folderName.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] toRawBytes(Message message) throws MessagingException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            message.writeTo(outputStream);
        } catch (FolderClosedException closed) {
            Message reopenedMessage = reopenMessage(message);
            if (reopenedMessage == null) {
                throw closed;
            }
            outputStream.reset();
            reopenedMessage.writeTo(outputStream);
        }
        return outputStream.toByteArray();
    }

    private void prefetchMessageMetadata(Folder folder, Message[] messages) throws MessagingException {
        if (messages.length == 0) {
            return;
        }
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
        fetchProfile.add(FetchProfile.Item.FLAGS);
        fetchProfile.add(UIDFolder.FetchProfileItem.UID);
        fetchProfile.add("Message-ID");
        folder.fetch(messages, fetchProfile);
    }

    private Message reopenMessage(Message originalMessage) {
        try {
            Folder folder = originalMessage.getFolder();
            if (folder == null) {
                return null;
            }
            if (!folder.isOpen()) {
                folder.open(Folder.READ_ONLY);
            }
            int messageNumber = originalMessage.getMessageNumber();
            if (messageNumber <= 0 || messageNumber > folder.getMessageCount()) {
                return null;
            }
            return folder.getMessage(messageNumber);
        } catch (MessagingException reopenFailure) {
            LOG.warn("Unable to reopen IMAP folder after a message materialization failure", reopenFailure);
            return null;
        }
    }

    private Folder inferredFolder(Message[] messages) {
        if (messages == null || messages.length == 0) {
            return null;
        }
        return messages[0].getFolder();
    }

    private String firstHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private String safeFolderName(Folder folder) {
        return folder == null ? null : folder.getFullName();
    }

    private Long resolveUidValidity(Folder folder) {
        if (folder instanceof IMAPFolder imapFolder) {
            try {
                return imapFolder.getUIDValidity();
            } catch (MessagingException ignored) {
                return null;
            }
        }
        return null;
    }

    private int safeMessageNumber(Message message) {
        try {
            return message.getMessageNumber();
        } catch (RuntimeException ignored) {
            return -1;
        }
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

    private MimeHashService mimeHashService() {
        if (mimeHashService == null) {
            mimeHashService = new MimeHashService();
        }
        return mimeHashService;
    }

    private record MessageMetadata(Long uid, Long uidValidity, String popUidl, String messageId, Instant instant, String folderName) {
    }
}
