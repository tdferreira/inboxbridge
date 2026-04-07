package dev.inboxbridge.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FetchProfile;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;

/**
 * Owns mailbox probe flows such as connection testing, folder listing, and
 * spam-folder inspection so MailSourceClient can stay focused on fetch
 * orchestration.
 */
@ApplicationScoped
public class MailSourceConnectionProbeService {

    private final MailSessionFactory mailSessionFactory;
    private final MailSourceConnectionService mailSourceConnectionService;
    private final MailSourceFolderService mailSourceFolderService;
    private final MailSourceMessageMapper mailSourceMessageMapper;
    private final PollCancellationService pollCancellationService;

    @Inject
    MailSourceConnectionProbeService(
            MailSessionFactory mailSessionFactory,
            MailSourceConnectionService mailSourceConnectionService,
            MailSourceFolderService mailSourceFolderService,
            MailSourceMessageMapper mailSourceMessageMapper,
            PollCancellationService pollCancellationService) {
        this.mailSessionFactory = mailSessionFactory;
        this.mailSourceConnectionService = mailSourceConnectionService;
        this.mailSourceFolderService = mailSourceFolderService;
        this.mailSourceMessageMapper = mailSourceMessageMapper;
        this.pollCancellationService = pollCancellationService;
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

    public Optional<MailSourceClient.MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        return switch (bridge.protocol()) {
            case IMAP -> probeImapSpamOrJunkFolder(bridge);
            case POP3 -> Optional.empty();
        };
    }

    private EmailAccountConnectionTestResult testImapConnection(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory.sourceImapSession(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory.imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, bridge);
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
                sampleMessageMaterialized = !mailSourceMessageMapper.toFetchedMessages(bridge.id(), candidateFolder, candidateMessages).isEmpty();
            }
            Boolean forwardedMarkerSupported = MailSourceFolderService.resolveForwardedMarkerSupport(candidateFolder);
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

    private EmailAccountConnectionTestResult testPop3Connection(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory.sourcePop3Session(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory.pop3StoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, bridge);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            int visibleMessageCount = folder.getMessageCount();
            Message[] candidateMessages = selectTailMessages(folder, 1);
            boolean sampleMessageAvailable = candidateMessages.length > 0;
            Boolean sampleMessageMaterialized = null;
            if (sampleMessageAvailable) {
                sampleMessageMaterialized = !mailSourceMessageMapper.toFetchedMessages(bridge.id(), candidateMessages).isEmpty();
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

    private List<String> listImapFolders(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory.sourceImapSession(bridge);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory.imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, bridge);
            return mailSourceFolderService.listFolders(store);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to list folders for source " + bridge.id(), e);
        } finally {
            closeQuietly(store);
        }
    }

    private Optional<MailSourceClient.MailboxCountProbe> probeImapSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory.sourceImapSession(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory.imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, bridge);
            Optional<MailSourceClient.MailboxCountProbe> probe = mailSourceFolderService.probeSpamOrJunkFolder(store);
            if (probe.isEmpty()) {
                return Optional.empty();
            }
            folder = store.getFolder(probe.get().folderName());
            registerFolder(folder);
            return probe;
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to inspect spam or junk mailbox for source " + bridge.id(), e);
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

    private void requireSupportedAuth(RuntimeEmailAccount bridge) {
        if (bridge.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2) {
            throw new IllegalStateException("OAuth2 is only implemented for configured Google or Microsoft source providers at the moment");
        }
    }

}
