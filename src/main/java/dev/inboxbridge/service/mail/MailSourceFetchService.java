package dev.inboxbridge.service.mail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceMailboxFolders;
import dev.inboxbridge.service.PollCancellationService;
import dev.inboxbridge.service.SourcePollingStateService;
import dev.inboxbridge.service.destination.DestinationIdentityKeys;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;

/**
 * Owns source mailbox fetch execution so MailSourceClient can stay focused on
 * high-level coordination while protocol fetch details evolve independently.
 */
@ApplicationScoped
public class MailSourceFetchService {

    private final MailSessionFactory mailSessionFactory;
    private final MailSourceConnectionService mailSourceConnectionService;
    private final MailSourceCheckpointSelector mailSourceCheckpointSelector;
    private final MailSourceMessageMapper mailSourceMessageMapper;
    private final SourcePollingStateService sourcePollingStateService;
    private final PollCancellationService pollCancellationService;

    @Inject
    MailSourceFetchService(
            MailSessionFactory mailSessionFactory,
            MailSourceConnectionService mailSourceConnectionService,
            MailSourceCheckpointSelector mailSourceCheckpointSelector,
            MailSourceMessageMapper mailSourceMessageMapper,
            SourcePollingStateService sourcePollingStateService,
            PollCancellationService pollCancellationService) {
        this.mailSessionFactory = mailSessionFactory;
        this.mailSourceConnectionService = mailSourceConnectionService;
        this.mailSourceCheckpointSelector = mailSourceCheckpointSelector;
        this.mailSourceMessageMapper = mailSourceMessageMapper;
        this.sourcePollingStateService = sourcePollingStateService;
        this.pollCancellationService = pollCancellationService;
    }

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source, int fetchWindow) {
        return switch (source.protocol()) {
            case IMAP -> fetchImap(source, fetchWindow);
            case POP3 -> fetchPop3(source, fetchWindow);
        };
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
        return switch (bridge.protocol()) {
            case IMAP -> fetchImap(bridge, fetchWindow);
            case POP3 -> fetchPop3(bridge, fetchWindow);
        };
    }

    private List<FetchedMessage> fetchImap(InboxBridgeConfig.Source source, int fetchWindow) {
        requireSupportedAuth(source);
        Session session = mailSessionFactory.sourceImapSession(source);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory.imapStoreProtocol(source.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, source);
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
        Session session = mailSessionFactory.sourceImapSession(bridge);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory.imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, bridge);
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
                Message[] candidateMessages = mailSourceCheckpointSelector.selectImapCandidateMessages(
                        sourcePollingStateService == null
                                ? Optional.empty()
                                : sourcePollingStateService.imapCheckpoint(sourceId, destinationKey, folderName),
                        unreadOnly,
                        fetchWindow,
                        folder);
                prefetchMessageMetadata(folder, candidateMessages);
                fetchedMessages.addAll(mailSourceMessageMapper.toFetchedMessages(sourceId, folder, candidateMessages));
            } finally {
                closeQuietly(folder);
            }
        }
        fetchedMessages.sort(Comparator.comparing(FetchedMessage::messageInstant));
        return fetchedMessages;
    }

    private List<FetchedMessage> fetchPop3(InboxBridgeConfig.Source source, int fetchWindow) {
        requireSupportedAuth(source);
        Session session = mailSessionFactory.sourcePop3Session(source);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory.pop3StoreProtocol(source.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, source);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = mailSourceCheckpointSelector.selectPop3CandidateMessages(
                    sourcePollingStateService == null ? Optional.empty() : sourcePollingStateService.popCheckpoint(source.id(), null),
                    fetchWindow,
                    folder);
            return mailSourceMessageMapper.toFetchedMessages(source.id(), candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch POP3 mail for source " + source.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private List<FetchedMessage> fetchPop3(RuntimeEmailAccount bridge, int fetchWindow) {
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
            Message[] candidateMessages = mailSourceCheckpointSelector.selectPop3CandidateMessages(
                    sourcePollingStateService == null
                            ? Optional.empty()
                            : sourcePollingStateService.popCheckpoint(bridge.id(), destinationKeyFor(bridge)),
                    fetchWindow,
                    folder);
            return mailSourceMessageMapper.toFetchedMessages(bridge.id(), candidateMessages);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to fetch POP3 mail for source " + bridge.id(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private String destinationKeyFor(RuntimeEmailAccount bridge) {
        return bridge == null ? null : DestinationIdentityKeys.forTarget(bridge.destination());
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

}
