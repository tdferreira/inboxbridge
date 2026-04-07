package dev.inboxbridge.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.pop3.POP3Folder;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.ImapCheckpoint;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceMailboxFolders;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.HeaderTerm;

@ApplicationScoped
public class MailSourceClient {

    private static final Logger LOG = Logger.getLogger(MailSourceClient.class);

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    @Inject
    MimeHashService mimeHashService;

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    PollCancellationService pollCancellationService;

    @Inject
    MailSessionFactory mailSessionFactory;

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source) {
        return fetch(source, pollingSettingsService.effectiveSettings().fetchWindow());
    }

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source, int fetchWindow) {
        return switch (source.protocol()) {
            case IMAP -> fetchImap(source, fetchWindow);
            case POP3 -> fetchPop3(source, fetchWindow);
        };
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge) {
        return fetch(bridge, pollingSettingsService.effectiveSettings().fetchWindow());
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
        return switch (bridge.protocol()) {
            case IMAP -> fetchImap(bridge, fetchWindow);
            case POP3 -> fetchPop3(bridge, fetchWindow);
        };
    }

    public EmailAccountConnectionTestResult testConnection(RuntimeEmailAccount bridge) {
        return switch (bridge.protocol()) {
            case IMAP -> testImapConnection(bridge);
            case POP3 -> testPop3Connection(bridge);
        };
    }

    public List<String> listFolders(RuntimeEmailAccount bridge) {
        return switch (bridge.protocol()) {
            case IMAP -> listImapFolders(bridge);
            case POP3 -> List.of();
        };
    }

    public Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        return switch (bridge.protocol()) {
            case IMAP -> probeImapSpamOrJunkFolder(bridge);
            case POP3 -> Optional.empty();
        };
    }

    public void applyPostPollSettings(RuntimeEmailAccount bridge, FetchedMessage message) {
        if (!bridge.postPollSettings().hasAnyAction()) {
            return;
        }
        if (bridge.protocol() != InboxBridgeConfig.Protocol.IMAP) {
            throw new IllegalStateException("Source-side message actions are only supported for IMAP accounts");
        }

        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        Folder sourceFolder = null;
        Folder targetFolder = null;
        boolean expunge = false;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);
            sourceFolder = store.getFolder(message.folderName().orElse(bridge.primaryFolder()));
            registerFolder(sourceFolder);
            if (!sourceFolder.exists()) {
                throw new IllegalStateException("The mailbox path " + sourceFolder.getFullName() + " does not exist on " + bridge.host() + ".");
            }
            sourceFolder.open(Folder.READ_WRITE);
            Message sourceMessage = resolveSourceMessage(sourceFolder, message);
            if (sourceMessage == null) {
                throw new IllegalStateException("Unable to find the source message to apply post-poll actions for " + bridge.id());
            }

            if (bridge.postPollSettings().markAsRead()) {
                sourceMessage.setFlag(Flags.Flag.SEEN, true);
            }
            if (bridge.postPollSettings().action() == dev.inboxbridge.domain.SourcePostPollAction.MOVE) {
                String targetFolderName = bridge.postPollSettings().targetFolder()
                        .orElseThrow(() -> new IllegalStateException("A target folder is required when moving source messages after polling"));
                targetFolder = store.getFolder(targetFolderName);
                registerFolder(targetFolder);
                if (!targetFolder.exists()) {
                    throw new IllegalStateException("The mailbox path " + targetFolderName + " does not exist on " + bridge.host() + ".");
                }
                sourceFolder.copyMessages(new Message[] { sourceMessage }, targetFolder);
                sourceMessage.setFlag(Flags.Flag.DELETED, true);
                expunge = true;
            } else if (bridge.postPollSettings().action() == dev.inboxbridge.domain.SourcePostPollAction.FORWARDED) {
                applyForwardedFlag(sourceMessage, bridge);
            } else if (bridge.postPollSettings().action() == dev.inboxbridge.domain.SourcePostPollAction.DELETE) {
                sourceMessage.setFlag(Flags.Flag.DELETED, true);
                expunge = true;
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to apply post-poll actions for source " + bridge.id(), e);
        } finally {
            closeQuietly(sourceFolder, expunge);
            closeQuietly(targetFolder);
            closeQuietly(store);
        }
    }

    private void applyForwardedFlag(Message sourceMessage, RuntimeEmailAccount bridge) throws MessagingException {
        Flags forwarded = new Flags();
        forwarded.add("$Forwarded");
        try {
            sourceMessage.setFlags(forwarded, true);
        } catch (MessagingException unsupportedFlag) {
            LOG.warnf(unsupportedFlag, "Unable to set $Forwarded on source %s; continuing without that marker", bridge.id());
        }
    }

    private EmailAccountConnectionTestResult testImapConnection(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);
            List<String> targetFolders = bridge.sourceFolders();
            String targetFolder = String.join(", ", targetFolders);
            int visibleMessageCount = 0;
            int unreadMessageCount = 0;
            Message[] candidateMessages = new Message[0];
            Folder candidateFolder = null;
            for (String folderName : targetFolders) {
                folder = store.getFolder(folderName);
                registerFolder(folder);
                if (!folder.exists()) {
                    throw new IllegalStateException("The mailbox path " + folderName + " does not exist on " + bridge.host() + ".");
                }
                folder.open(Folder.READ_ONLY);
                visibleMessageCount += folder.getMessageCount();
                Message[] unreadMessages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                unreadMessageCount += unreadMessages.length;
                Message[] folderCandidateMessages = bridge.unreadOnly()
                        ? trimTailMessages(unreadMessages, 1)
                        : selectTailMessages(folder, 1);
                if (folderCandidateMessages.length > 0) {
                    candidateMessages = folderCandidateMessages;
                    candidateFolder = folder;
                    folder = null;
                    break;
                }
                closeQuietly(folder);
                folder = null;
            }
            boolean sampleMessageAvailable = candidateMessages.length > 0;
            Boolean sampleMessageMaterialized = null;
            if (sampleMessageAvailable) {
                prefetchMessageMetadata(candidateFolder, candidateMessages);
                sampleMessageMaterialized = !toFetchedMessages(bridge.id(), candidateFolder, candidateMessages).isEmpty();
            }
            Boolean forwardedMarkerSupported = resolveForwardedMarkerSupport(candidateFolder);
            return buildProbeResult(
                    bridge,
                    targetFolder,
                    true,
                    Boolean.TRUE,
                    bridge.unreadOnly() ? Boolean.TRUE : null,
                    visibleMessageCount,
                    unreadMessageCount,
                    sampleMessageAvailable,
                    sampleMessageMaterialized,
                    forwardedMarkerSupported);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to connect to IMAP mail fetcher " + bridge.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private Optional<MailboxCountProbe> probeImapSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);
            folder = locateSpamOrJunkFolder(store);
            if (folder == null || !folder.exists()) {
                return Optional.empty();
            }
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            return Optional.of(new MailboxCountProbe(folder.getFullName(), folder.getMessageCount()));
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to inspect spam or junk mailbox for source " + bridge.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private List<String> listImapFolders(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);

            LinkedHashSet<String> folderNames = new LinkedHashSet<>();
            Folder inbox = store.getFolder("INBOX");
            if (inbox != null && inbox.exists()) {
                folderNames.add(inbox.getFullName());
            }

            Folder defaultFolder = store.getDefaultFolder();
            if (defaultFolder != null) {
                collectFolderNames(defaultFolder.list("*"), folderNames);
            }

            List<String> folders = new ArrayList<>(folderNames);
            folders.sort(Comparator
                    .comparing((String folderName) -> !"INBOX".equalsIgnoreCase(folderName))
                    .thenComparing(String.CASE_INSENSITIVE_ORDER));
            return folders;
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to list folders for source " + bridge.id(), e);
        } finally {
            closeQuietly(store);
        }
    }

    private List<FetchedMessage> fetchImap(InboxBridgeConfig.Source source, int fetchWindow) {
        requireSupportedAuth(source);
        Session session = mailSessionFactory().sourceImapSession(source);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(source.tls()));
            registerStore(store);
            connectStore(store, source);
            return fetchImapMessages(
                    source.id(),
                    null,
                    SourceMailboxFolders.forSource(source.protocol(), source.folder()),
                    source.unreadOnly(),
                    fetchWindow,
                    store);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch IMAP mail for source " + source.id(), e);
        } finally {
            closeQuietly(store);
        }
    }

    private List<FetchedMessage> fetchImap(RuntimeEmailAccount bridge, int fetchWindow) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);
            return fetchImapMessages(
                    bridge.id(),
                    destinationKeyFor(bridge),
                    bridge.sourceFolders(),
                    bridge.unreadOnly(),
                    fetchWindow,
                    store);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch IMAP mail for source " + bridge.id(), e);
        } finally {
            closeQuietly(store);
        }
    }

    /**
     * Fetches candidate IMAP messages independently per configured folder so each
     * folder keeps its own checkpoint continuity and visible-window selection.
     */
    private List<FetchedMessage> fetchImapMessages(
            String sourceId,
            String destinationKey,
            List<String> folderNames,
            boolean unreadOnly,
            int fetchWindow,
            Store store) throws MessagingException {
        List<FetchedMessage> fetchedMessages = new ArrayList<>();
        for (String folderName : folderNames) {
            Folder folder = null;
            try {
                folder = store.getFolder(folderName);
                registerFolder(folder);
                if (!folder.exists()) {
                    throw new IllegalStateException("The mailbox path " + folderName + " does not exist on " + store.getURLName().getHost() + ".");
                }
                folder.open(Folder.READ_ONLY);
                Message[] candidateMessages = selectImapCandidateMessages(
                        sourceId,
                        destinationKey,
                        folderName,
                        unreadOnly,
                        fetchWindow,
                        folder);
                prefetchMessageMetadata(folder, candidateMessages);
                fetchedMessages.addAll(toFetchedMessages(sourceId, folder, candidateMessages));
            } finally {
                closeQuietly(folder);
            }
        }
        fetchedMessages.sort(Comparator.comparing(FetchedMessage::messageInstant));
        return fetchedMessages;
    }

    private List<FetchedMessage> fetchPop3(InboxBridgeConfig.Source source, int fetchWindow) {
        requireSupportedAuth(source);
        Session session = mailSessionFactory().sourcePop3Session(source);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().pop3StoreProtocol(source.tls()));
            registerStore(store);
            connectStore(store, source);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = selectPop3CandidateMessages(source.id(), null, fetchWindow, folder);
            return toFetchedMessages(source, candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch POP3 mail for source " + source.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private List<FetchedMessage> fetchPop3(RuntimeEmailAccount bridge, int fetchWindow) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourcePop3Session(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().pop3StoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = selectPop3CandidateMessages(bridge.id(), destinationKeyFor(bridge), fetchWindow, folder);
            return toFetchedMessages(bridge.id(), candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch POP3 mail for source " + bridge.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private EmailAccountConnectionTestResult testPop3Connection(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourcePop3Session(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().pop3StoreProtocol(bridge.tls()));
            registerStore(store);
            connectStore(store, bridge);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            int visibleMessageCount = folder.getMessageCount();
            Message[] candidateMessages = selectTailMessages(folder, 1);
            boolean sampleMessageAvailable = candidateMessages.length > 0;
            Boolean sampleMessageMaterialized = null;
            if (sampleMessageAvailable) {
                sampleMessageMaterialized = !toFetchedMessages(bridge.id(), candidateMessages).isEmpty();
            }
            return buildProbeResult(
                    bridge,
                    "INBOX",
                    true,
                    Boolean.FALSE,
                    bridge.unreadOnly() ? Boolean.FALSE : null,
                    visibleMessageCount,
                    null,
                    sampleMessageAvailable,
                    sampleMessageMaterialized,
                    null);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to connect to POP3 mail fetcher " + bridge.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private EmailAccountConnectionTestResult buildProbeResult(
            RuntimeEmailAccount bridge,
            String targetFolder,
            boolean folderAccessible,
            Boolean unreadFilterSupported,
            Boolean unreadFilterValidated,
            Integer visibleMessageCount,
            Integer unreadMessageCount,
            Boolean sampleMessageAvailable,
            Boolean sampleMessageMaterialized,
            Boolean forwardedMarkerSupported) {
        StringBuilder message = new StringBuilder("Connection test succeeded.");
        message.append(" Mailbox path ").append(targetFolder).append(" is reachable.");
        if (Boolean.TRUE.equals(unreadFilterSupported)) {
            message.append(" Unread filter probing is supported");
            if (Boolean.TRUE.equals(unreadFilterValidated)) {
                message.append(" and validated");
            }
            message.append('.');
        } else if (bridge.unreadOnly()) {
            message.append(" Server-side unread filtering is not supported for this protocol.");
        }
        if (Boolean.TRUE.equals(sampleMessageAvailable) && Boolean.TRUE.equals(sampleMessageMaterialized)) {
            message.append(" A sample message was materialized successfully.");
        } else if (Boolean.TRUE.equals(sampleMessageAvailable)) {
            message.append(" A sample message was found but could not be materialized.");
        } else {
            message.append(" No sample message was available to materialize.");
        }
        return new EmailAccountConnectionTestResult(
                true,
                message.toString(),
                bridge.protocol().name(),
                bridge.host(),
                bridge.port(),
                bridge.tls(),
                bridge.authMethod().name(),
                bridge.oauthProvider().name(),
                true,
                targetFolder,
                folderAccessible,
                bridge.unreadOnly(),
                unreadFilterSupported,
                unreadFilterValidated,
                visibleMessageCount,
                unreadMessageCount,
                sampleMessageAvailable,
                sampleMessageMaterialized,
                forwardedMarkerSupported);
    }

    static Boolean resolveForwardedMarkerSupport(Folder folder) {
        if (folder == null) {
            return null;
        }
        Flags permanentFlags = folder.getPermanentFlags();
        if (permanentFlags == null) {
            return null;
        }
        String[] userFlags = permanentFlags.getUserFlags();
        if (userFlags != null) {
            for (String userFlag : userFlags) {
                if ("$forwarded".equalsIgnoreCase(userFlag)) {
                    return Boolean.TRUE;
                }
            }
        }
        if (permanentFlags.contains(Flags.Flag.USER)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
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

    private List<FetchedMessage> toFetchedMessages(String sourceId, Folder folder, Message[] messages) {
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
                String sha = mimeHashService.sha256Hex(raw);
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

    private List<FetchedMessage> toFetchedMessages(InboxBridgeConfig.Source source, Message[] messages) {
        return toFetchedMessages(source.id(), source.protocol() == InboxBridgeConfig.Protocol.IMAP ? inferredFolder(messages) : null, messages);
    }

    private List<FetchedMessage> toFetchedMessages(String sourceId, Message[] messages) {
        return toFetchedMessages(sourceId, inferredFolder(messages), messages);
    }

    private Folder inferredFolder(Message[] messages) {
        if (messages == null || messages.length == 0) {
            return null;
        }
        return messages[0].getFolder();
    }

    private String resolveSourceMessageKey(InboxBridgeConfig.Source source, Message message, String sha) throws MessagingException {
        return resolveSourceMessageKey(source.id(), message, sha);
    }

    private String resolveSourceMessageKey(String sourceId, Message message, String sha) throws MessagingException {
        if (message.getFolder() instanceof UIDFolder uidFolder) {
            long uid = uidFolder.getUID(message);
            if (uid > 0) {
                return imapSourceMessageKey(sourceId, safeFolderName(message.getFolder()), resolveUidValidity(message.getFolder()), uid);
            }
        }
        String messageIdHeader = firstHeader(message, "Message-ID");
        if (messageIdHeader != null && !messageIdHeader.isBlank()) {
            return sourceId + ":message-id:" + messageIdHeader;
        }
        return mimeHashService.fallbackMessageKey(sourceId, sha);
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
        return mimeHashService.fallbackMessageKey(sourceId, sha);
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

    private Message[] selectImapCandidateMessages(
            String sourceId,
            String destinationKey,
            String folderName,
            boolean unreadOnly,
            int fetchWindow,
            Folder folder) throws MessagingException {
        Optional<ImapCheckpoint> checkpoint = sourcePollingStateService == null
                ? Optional.empty()
                : sourcePollingStateService.imapCheckpoint(sourceId, destinationKey, folderName);
        Message[] checkpointMessages = selectMessagesFromCheckpoint(folder, checkpoint, unreadOnly);
        if (checkpointMessages != null) {
            return checkpointMessages;
        }
        return unreadOnly
                ? trimTailMessages(folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)), fetchWindow)
                : selectTailMessages(folder, fetchWindow);
    }

    private Message[] selectPop3CandidateMessages(String sourceId, String destinationKey, int fetchWindow, Folder folder) throws MessagingException {
        Optional<String> checkpoint = sourcePollingStateService == null
                ? Optional.empty()
                : sourcePollingStateService.popCheckpoint(sourceId, destinationKey);
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
            LOG.debugf(e, "Unable to resolve POP UIDL for message %s", safeMessageNumber(message));
            return null;
        }
    }

    private String destinationKeyFor(RuntimeEmailAccount bridge) {
        return bridge == null ? null : DestinationIdentityKeys.forTarget(bridge.destination());
    }

    private String safeFolderName(Folder folder) {
        if (folder == null) {
            return null;
        }
        return folder.getFullName();
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

    private String firstHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private Message resolveSourceMessage(Folder folder, FetchedMessage message) throws MessagingException {
        String sourceMessageKey = message.sourceMessageKey();
        Long imapUid = extractImapUidFromSourceKey(message);
        if (imapUid != null && folder instanceof UIDFolder uidFolder) {
            long uid = imapUid.longValue();
            return uidFolder.getMessageByUID(uid);
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

    private Optional<String> extractMessageIdFromSourceKey(FetchedMessage message) {
        String sourceMessageKey = message.sourceMessageKey();
        String messageIdPrefix = message.sourceAccountId() + ":message-id:";
        if (sourceMessageKey == null || !sourceMessageKey.startsWith(messageIdPrefix)) {
            return Optional.empty();
        }
        return Optional.of(sourceMessageKey.substring(messageIdPrefix.length()));
    }

    private String imapSourceMessageKey(String sourceId, Long uidValidity, long uid) {
        return imapSourceMessageKey(sourceId, null, uidValidity, uid);
    }

    private String imapSourceMessageKey(String sourceId, String folderName, Long uidValidity, long uid) {
        if (folderName != null && !folderName.isBlank() && uidValidity != null && uidValidity > 0L) {
            return sourceId + ":imap-folder-uid:" + encodeFolderName(folderName) + ":" + uidValidity + ":" + uid;
        }
        if (uidValidity != null && uidValidity > 0L) {
            return sourceId + ":imap-uid:" + uidValidity + ":" + uid;
        }
        return sourceId + ":uid:" + uid;
    }

    private Long extractImapUidFromSourceKey(FetchedMessage message) {
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

    private int safeMessageNumber(Message message) {
        try {
            return message.getMessageNumber();
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    static boolean isRetryableMicrosoftOAuthFailure(Throwable error) {
        return MailFailureClassifier.classify(error).retryableOAuthSessionFailure();
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
        closeQuietly(folder, false);
    }

    private void closeQuietly(Folder folder, boolean expunge) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(expunge);
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

    private void registerStore(Store store) {
        if (store == null || pollCancellationService == null) {
            return;
        }
        pollCancellationService.register(() -> closeQuietly(store));
    }

    private void registerFolder(Folder folder) {
        if (folder == null || pollCancellationService == null) {
            return;
        }
        pollCancellationService.register(() -> closeQuietly(folder));
    }

    private void collectFolderNames(Folder[] folders, LinkedHashSet<String> names) throws MessagingException {
        if (folders == null) {
            return;
        }
        for (Folder folder : folders) {
            if (folder == null) {
                continue;
            }
            if (folder.exists()) {
                String fullName = folder.getFullName();
                if (fullName != null && !fullName.isBlank()) {
                    names.add(fullName);
                }
            }
            if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
                collectFolderNames(folder.list("*"), names);
            }
        }
    }

    private void connectStore(Store store, InboxBridgeConfig.Source source) throws MessagingException {
        if (usesMicrosoftOAuth(source)) {
            connectStoreWithMicrosoftOAuthRetry(
                    store,
                    source.id(),
                    source.host(),
                    source.port(),
                    source.username(),
                    () -> microsoftOAuthService.getAccessToken(source));
            return;
        }
        if (usesGoogleOAuth(source)) {
            connectStoreWithGoogleOAuthRetry(
                    store,
                    source.id(),
                    source.host(),
                    source.port(),
                    source.username(),
                    () -> googleOAuthService.getAccessToken(source));
            return;
        }
        store.connect(source.host(), source.port(), source.username(), source.password());
    }

    private void connectStore(Store store, RuntimeEmailAccount bridge) throws MessagingException {
        if (usesMicrosoftOAuth(bridge)) {
            connectStoreWithMicrosoftOAuthRetry(
                    store,
                    bridge.id(),
                    bridge.host(),
                    bridge.port(),
                    bridge.username(),
                    () -> microsoftOAuthService.getAccessToken(bridge));
            return;
        }
        if (usesGoogleOAuth(bridge)) {
            connectStoreWithGoogleOAuthRetry(
                    store,
                    bridge.id(),
                    bridge.host(),
                    bridge.port(),
                    bridge.username(),
                    () -> googleOAuthService.getAccessToken(bridge));
            return;
        }
        store.connect(bridge.host(), bridge.port(), bridge.username(), bridge.password());
    }

    private void connectStoreWithMicrosoftOAuthRetry(
            Store store,
            String sourceId,
            String host,
            int port,
            String username,
            TokenSupplier tokenSupplier) throws MessagingException {
        try {
            store.connect(host, port, username, tokenSupplier.get());
        } catch (MessagingException firstFailure) {
            if (!isRetryableMicrosoftOAuthFailure(firstFailure)) {
                throw firstFailure;
            }
            LOG.warnf(firstFailure,
                    "Microsoft session for %s was rejected; invalidating the cached token and retrying once",
                    sourceId);
            microsoftOAuthService.invalidateCachedToken(sourceId);
            store.connect(host, port, username, tokenSupplier.get());
        }
    }

    private boolean usesMicrosoftOAuth(InboxBridgeConfig.Source source) {
        return source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT;
    }

    private boolean usesGoogleOAuth(InboxBridgeConfig.Source source) {
        return source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == InboxBridgeConfig.OAuthProvider.GOOGLE;
    }

    private boolean usesMicrosoftOAuth(RuntimeEmailAccount bridge) {
        return bridge.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && bridge.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT;
    }

    private boolean usesGoogleOAuth(RuntimeEmailAccount bridge) {
        return bridge.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && bridge.oauthProvider() == InboxBridgeConfig.OAuthProvider.GOOGLE;
    }

    private void requireSupportedAuth(InboxBridgeConfig.Source source) {
        if (source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2) {
            throw new IllegalStateException("OAuth2 is only implemented for configured Google or Microsoft source providers at the moment");
        }
    }

    private void requireSupportedAuth(RuntimeEmailAccount bridge) {
        if (bridge.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2) {
            throw new IllegalStateException("OAuth2 is only implemented for configured Google or Microsoft source providers at the moment");
        }
    }

    private void connectStoreWithGoogleOAuthRetry(
            Store store,
            String sourceId,
            String host,
            int port,
            String username,
            TokenSupplier tokenSupplier) throws MessagingException {
        try {
            store.connect(host, port, username, tokenSupplier.get());
        } catch (MessagingException firstFailure) {
            if (!isRetryableMicrosoftOAuthFailure(firstFailure)) {
                throw firstFailure;
            }
            LOG.warnf(firstFailure,
                    "Google session for %s was rejected; invalidating the cached token and retrying once",
                    sourceId);
            googleOAuthService.clearCachedToken("source-google:" + sourceId);
            store.connect(host, port, username, tokenSupplier.get());
        }
    }

    private Folder locateSpamOrJunkFolder(Store store) throws MessagingException {
        Folder defaultFolder = store.getDefaultFolder();
        if (defaultFolder == null) {
            return null;
        }
        List<Folder> folders = new ArrayList<>();
        collectFolders(defaultFolder, folders, new HashSet<>());
        for (Folder folder : folders) {
            if (hasSpamOrJunkSpecialUse(folder)) {
                return folder;
            }
        }
        for (Folder folder : folders) {
            if (isLikelySpamOrJunkFolder(folder)) {
                return folder;
            }
        }
        return null;
    }

    private void collectFolders(Folder folder, List<Folder> collected, Set<String> visited) throws MessagingException {
        String fullName = folder.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            if (!visited.add(fullName)) {
                return;
            }
            collected.add(folder);
        }
        for (Folder child : folder.list("*")) {
            collectFolders(child, collected, visited);
        }
    }

    private boolean isLikelySpamOrJunkFolder(Folder folder) {
        char separator;
        try {
            separator = folder.getSeparator();
        } catch (MessagingException e) {
            separator = '/';
        }
        List<String> candidates = new ArrayList<>();
        if (folder.getFullName() != null) {
            candidates.add(folder.getFullName());
            if (separator != 0) {
                candidates.addAll(Arrays.asList(folder.getFullName().split(java.util.regex.Pattern.quote(String.valueOf(separator)))));
            }
        }
        if (folder.getName() != null) {
            candidates.add(folder.getName());
        }
        return candidates.stream()
                .map(this::normalizeFolderToken)
                .anyMatch(SPAM_OR_JUNK_FOLDER_NAMES::contains);
    }

    private boolean hasSpamOrJunkSpecialUse(Folder folder) {
        try {
            java.lang.reflect.Method method = folder.getClass().getMethod("getAttributes");
            Object result = method.invoke(folder);
            if (!(result instanceof String[] attributes)) {
                return false;
            }
            for (String attribute : attributes) {
                String normalized = String.valueOf(attribute).trim().toLowerCase(Locale.ROOT);
                if (SPAM_OR_JUNK_SPECIAL_USE_ATTRIBUTES.contains(normalized)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private String normalizeFolderToken(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private MailSessionFactory mailSessionFactory() {
        if (mailSessionFactory != null) {
            return mailSessionFactory;
        }
        MailSessionFactory fallback = new MailSessionFactory();
        fallback.mailClientConfig = new dev.inboxbridge.config.MailClientConfig() {
            @Override
            public java.time.Duration connectionTimeout() {
                return java.time.Duration.ofSeconds(20);
            }

            @Override
            public java.time.Duration operationTimeout() {
                return java.time.Duration.ofSeconds(20);
            }

            @Override
            public java.time.Duration idleOperationTimeout() {
                return java.time.Duration.ZERO;
            }
        };
        mailSessionFactory = fallback;
        return mailSessionFactory;
    }

    @FunctionalInterface
    private interface TokenSupplier {
        String get();
    }

    private record MessageMetadata(Long uid, Long uidValidity, String popUidl, String messageId, Instant instant, String folderName) {
    }

    public record MailboxCountProbe(String folderName, int messageCount) {
    }

    private static final Set<String> SPAM_OR_JUNK_FOLDER_NAMES = Set.of(
            "spam",
            "junk",
            "junkemail",
            "junkeemail",
            "junkmail",
            "bulkmail",
            "correonodeseado",
            "correoindeseado",
            "indesejados");

    private static final Set<String> SPAM_OR_JUNK_SPECIAL_USE_ATTRIBUTES = Set.of(
            "\\junk",
            "\\spam");

}
