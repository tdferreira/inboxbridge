package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceMailboxFolders;
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

@ApplicationScoped
public class MailSourceClient {

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

    @Inject
    MailSourceFolderService mailSourceFolderService;

    @Inject
    MailSourceMessageMapper mailSourceMessageMapper;

    @Inject
    MailSourceCheckpointSelector mailSourceCheckpointSelector;

    @Inject
    MailSourceConnectionService mailSourceConnectionService;

    @Inject
    MailSourcePostPollActionService mailSourcePostPollActionService;

    @Inject
    MailSourceConnectionProbeService mailSourceConnectionProbeService;

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
        return mailSourceConnectionProbeService().testConnection(bridge);
    }

    public List<String> listFolders(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService().listFolders(bridge);
    }

    public Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService().probeSpamOrJunkFolder(bridge);
    }

    public void applyPostPollSettings(RuntimeEmailAccount bridge, FetchedMessage message) {
        mailSourcePostPollActionService().apply(bridge, message);
    }

    private List<FetchedMessage> fetchImap(InboxBridgeConfig.Source source, int fetchWindow) {
        requireSupportedAuth(source);
        Session session = mailSessionFactory().sourceImapSession(source);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(source.tls()));
            registerStore(store);
            mailSourceConnectionService().connectStore(store, source);
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
            mailSourceConnectionService().connectStore(store, bridge);
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
                Message[] candidateMessages = mailSourceCheckpointSelector().selectImapCandidateMessages(
                        sourcePollingStateService == null
                                ? Optional.empty()
                                : sourcePollingStateService.imapCheckpoint(sourceId, destinationKey, folderName),
                        unreadOnly,
                        fetchWindow,
                        folder);
                prefetchMessageMetadata(folder, candidateMessages);
                fetchedMessages.addAll(mailSourceMessageMapper().toFetchedMessages(sourceId, folder, candidateMessages));
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
            mailSourceConnectionService().connectStore(store, source);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = mailSourceCheckpointSelector().selectPop3CandidateMessages(
                    sourcePollingStateService == null ? Optional.empty() : sourcePollingStateService.popCheckpoint(source.id(), null),
                    fetchWindow,
                    folder);
            return mailSourceMessageMapper().toFetchedMessages(source.id(), candidateMessages);
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
            mailSourceConnectionService().connectStore(store, bridge);
            folder = store.getFolder("INBOX");
            registerFolder(folder);
            folder.open(Folder.READ_ONLY);
            Message[] candidateMessages = mailSourceCheckpointSelector().selectPop3CandidateMessages(
                    sourcePollingStateService == null
                            ? Optional.empty()
                            : sourcePollingStateService.popCheckpoint(bridge.id(), destinationKeyFor(bridge)),
                    fetchWindow,
                    folder);
            return mailSourceMessageMapper().toFetchedMessages(bridge.id(), candidateMessages);
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

    static boolean isRetryableMicrosoftOAuthFailure(Throwable error) {
        return MailFailureClassifier.classify(error).retryableOAuthSessionFailure();
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

    private MailSourceFolderService mailSourceFolderService() {
        if (mailSourceFolderService == null) {
            mailSourceFolderService = new MailSourceFolderService();
        }
        return mailSourceFolderService;
    }

    private MailSourceMessageMapper mailSourceMessageMapper() {
        if (mailSourceMessageMapper != null) {
            return mailSourceMessageMapper;
        }
        MailSourceMessageMapper fallback = new MailSourceMessageMapper();
        fallback.mimeHashService = mimeHashService == null ? new MimeHashService() : mimeHashService;
        mailSourceMessageMapper = fallback;
        return mailSourceMessageMapper;
    }

    private MailSourceCheckpointSelector mailSourceCheckpointSelector() {
        if (mailSourceCheckpointSelector == null) {
            mailSourceCheckpointSelector = new MailSourceCheckpointSelector();
        }
        return mailSourceCheckpointSelector;
    }

    private MailSourceConnectionService mailSourceConnectionService() {
        if (mailSourceConnectionService != null) {
            return mailSourceConnectionService;
        }
        MailSourceConnectionService fallback = new MailSourceConnectionService();
        fallback.microsoftOAuthService = microsoftOAuthService;
        fallback.googleOAuthService = googleOAuthService;
        mailSourceConnectionService = fallback;
        return mailSourceConnectionService;
    }

    private MailSourcePostPollActionService mailSourcePostPollActionService() {
        if (mailSourcePostPollActionService != null) {
            return mailSourcePostPollActionService;
        }
        MailSourcePostPollActionService fallback = new MailSourcePostPollActionService();
        fallback.mailSessionFactory = mailSessionFactory();
        fallback.mailSourceConnectionService = mailSourceConnectionService();
        fallback.mailSourceMessageMapper = mailSourceMessageMapper();
        fallback.pollCancellationService = pollCancellationService;
        mailSourcePostPollActionService = fallback;
        return mailSourcePostPollActionService;
    }

    private MailSourceConnectionProbeService mailSourceConnectionProbeService() {
        if (mailSourceConnectionProbeService != null) {
            return mailSourceConnectionProbeService;
        }
        MailSourceConnectionProbeService fallback = new MailSourceConnectionProbeService();
        fallback.mailSessionFactory = mailSessionFactory();
        fallback.mailSourceConnectionService = mailSourceConnectionService();
        fallback.mailSourceFolderService = mailSourceFolderService();
        fallback.mailSourceMessageMapper = mailSourceMessageMapper();
        fallback.pollCancellationService = pollCancellationService;
        mailSourceConnectionProbeService = fallback;
        return mailSourceConnectionProbeService;
    }

    public record MailboxCountProbe(String folderName, int messageCount) {
    }
}
