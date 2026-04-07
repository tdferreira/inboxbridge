package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.Arrays;
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
import jakarta.mail.search.HeaderTerm;

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
        mailSourcePostPollActionService().apply(bridge, message);
    }

    private EmailAccountConnectionTestResult testImapConnection(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService().connectStore(store, bridge);
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
                sampleMessageMaterialized = !mailSourceMessageMapper().toFetchedMessages(bridge.id(), candidateFolder, candidateMessages).isEmpty();
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

    private Optional<MailboxCountProbe> probeImapSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService().connectStore(store, bridge);
            Optional<MailboxCountProbe> probe = mailSourceFolderService().probeSpamOrJunkFolder(store);
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

    private List<String> listImapFolders(RuntimeEmailAccount bridge) {
        requireSupportedAuth(bridge);
        Session session = mailSessionFactory().sourceImapSession(bridge);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService().connectStore(store, bridge);
            return mailSourceFolderService().listFolders(store);
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

    private EmailAccountConnectionTestResult testPop3Connection(RuntimeEmailAccount bridge) {
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
            int visibleMessageCount = folder.getMessageCount();
            Message[] candidateMessages = selectTailMessages(folder, 1);
            boolean sampleMessageAvailable = candidateMessages.length > 0;
            Boolean sampleMessageMaterialized = null;
            if (sampleMessageAvailable) {
                sampleMessageMaterialized = !mailSourceMessageMapper().toFetchedMessages(bridge.id(), candidateMessages).isEmpty();
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

    public record MailboxCountProbe(String folderName, int messageCount) {
    }
}
