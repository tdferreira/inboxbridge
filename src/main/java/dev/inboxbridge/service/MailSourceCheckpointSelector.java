package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.angus.mail.pop3.POP3Folder;

import dev.inboxbridge.domain.ImapCheckpoint;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;

/**
 * Encapsulates checkpoint-aware candidate message selection so polling can keep
 * its folder iteration logic separate from resume-window heuristics.
 */
@ApplicationScoped
public class MailSourceCheckpointSelector {

    public Message[] selectImapCandidateMessages(
            Optional<ImapCheckpoint> checkpoint,
            boolean unreadOnly,
            int fetchWindow,
            Folder folder) throws MessagingException {
        Message[] checkpointMessages = selectMessagesFromCheckpoint(folder, checkpoint, unreadOnly);
        if (checkpointMessages != null) {
            return checkpointMessages;
        }
        return unreadOnly
                ? trimTailMessages(folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)), fetchWindow)
                : selectTailMessages(folder, fetchWindow);
    }

    public Message[] selectPop3CandidateMessages(
            Optional<String> checkpoint,
            int fetchWindow,
            Folder folder) throws MessagingException {
        Message[] checkpointMessages = selectMessagesFromPopCheckpoint(folder, checkpoint);
        if (checkpointMessages != null) {
            return checkpointMessages;
        }
        return selectTailMessages(folder, fetchWindow);
    }

    private Message[] selectMessagesFromCheckpoint(
            Folder folder,
            Optional<ImapCheckpoint> checkpoint,
            boolean unreadOnly) throws MessagingException {
        if (checkpoint.isEmpty()) {
            return null;
        }
        if (!(folder instanceof UIDFolder uidFolder)) {
            return null;
        }
        Long uidValidity = resolveUidValidity(folder);
        if (uidValidity == null || !uidValidity.equals(checkpoint.get().uidValidity())) {
            return null;
        }
        long startUid = checkpoint.get().lastSeenUid() == null ? -1L : checkpoint.get().lastSeenUid();
        if (startUid < 0L) {
            return null;
        }
        Message[] messages = uidFolder.getMessagesByUID(startUid + 1L, UIDFolder.LASTUID);
        if (!unreadOnly || messages.length == 0) {
            return messages;
        }
        List<Message> unreadMessages = new ArrayList<>();
        for (Message message : messages) {
            if (!message.isSet(Flags.Flag.SEEN)) {
                unreadMessages.add(message);
            }
        }
        return unreadMessages.toArray(Message[]::new);
    }

    private Message[] selectMessagesFromPopCheckpoint(Folder folder, Optional<String> checkpoint) throws MessagingException {
        if (checkpoint.isEmpty() || !(folder instanceof POP3Folder pop3Folder)) {
            return null;
        }
        int count = folder.getMessageCount();
        if (count <= 0) {
            return new Message[0];
        }
        Message[] messages = folder.getMessages(1, count);
        for (int index = messages.length - 1; index >= 0; index--) {
            String uidl = resolvePopUidl(pop3Folder, messages[index]);
            if (checkpoint.get().equals(uidl)) {
                if (index + 1 >= messages.length) {
                    return new Message[0];
                }
                return Arrays.copyOfRange(messages, index + 1, messages.length);
            }
        }
        return null;
    }

    private String resolvePopUidl(POP3Folder folder, Message message) {
        try {
            return folder.getUID(message);
        } catch (MessagingException e) {
            return null;
        }
    }

    private Message[] selectTailMessages(Folder folder, int fetchWindow) throws MessagingException {
        int count = folder.getMessageCount();
        if (count == 0) {
            return new Message[0];
        }
        int normalizedFetchWindow = Math.max(1, fetchWindow);
        int start = Math.max(1, count - normalizedFetchWindow + 1);
        return folder.getMessages(start, count);
    }

    private Message[] trimTailMessages(Message[] messages, int fetchWindow) {
        int normalizedFetchWindow = Math.max(1, fetchWindow);
        if (messages.length <= normalizedFetchWindow) {
            return messages;
        }
        return Arrays.copyOfRange(messages, messages.length - normalizedFetchWindow, messages.length);
    }

    private Long resolveUidValidity(Folder folder) {
        if (folder instanceof UIDFolder uidFolder) {
            try {
                return uidFolder.getUIDValidity();
            } catch (MessagingException ignored) {
                return null;
            }
        }
        return null;
    }
}
