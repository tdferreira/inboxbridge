package dev.inboxbridge.service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.service.mail.MailSessionFactory;
import dev.inboxbridge.service.mail.MailSourceClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

@ApplicationScoped
public class ImapAppendMailDestinationService implements MailDestinationService {

    public static final String MICROSOFT_DESTINATION_ACCESS_REVOKED_MESSAGE =
            "The linked Microsoft destination account no longer grants InboxBridge access. Reconnect it from My Destination Mailbox.";
    public static final String IMAP_DESTINATION_NOT_LINKED_MESSAGE =
            "The destination mailbox is not fully configured yet. Save My Destination Mailbox and connect its provider OAuth if required before polling this source.";
    private final ConcurrentMap<String, Object> destinationFolderLocks = new ConcurrentHashMap<>();

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    PollCancellationService pollCancellationService;

    @Inject
    MailSessionFactory mailSessionFactory;

    @Override
    public boolean supports(MailDestinationTarget target) {
        return target instanceof ImapAppendDestinationTarget;
    }

    @Override
    public boolean isLinked(MailDestinationTarget target) {
        if (!(target instanceof ImapAppendDestinationTarget imapTarget)) {
            return false;
        }
        if (imapTarget.username() == null || imapTarget.username().isBlank()) {
            return false;
        }
        if (imapTarget.authMethod() == InboxBridgeConfig.AuthMethod.PASSWORD) {
            return imapTarget.password() != null && !imapTarget.password().isBlank();
        }
        if (imapTarget.oauthProvider() != InboxBridgeConfig.OAuthProvider.MICROSOFT) {
            return false;
        }
        return microsoftOAuthService.destinationLinked(imapTarget.userId());
    }

    @Override
    public String notLinkedMessage(MailDestinationTarget target) {
        return IMAP_DESTINATION_NOT_LINKED_MESSAGE;
    }

    @Override
    public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message) {
        ImapAppendDestinationTarget imapTarget = (ImapAppendDestinationTarget) target;
        Session session = mailSessionFactory().destinationImapSession(imapTarget);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(imapTarget.tls()));
            registerStore(store);
            store.connect(imapTarget.host(), imapTarget.port(), imapTarget.username(), resolveSecret(imapTarget));
            folder = store.getFolder(imapTarget.folder());
            registerFolder(folder);
            ensureFolderExists(folder, imapTarget);
            MimeMessage mimeMessage = new MimeMessage(session, new ByteArrayInputStream(message.rawMessage()));
            folder.appendMessages(new Message[] { mimeMessage });
            return new MailImportResponse(imapTarget.providerId() + ":" + UUID.randomUUID(), Instant.now().toString());
        } catch (MessagingException e) {
            if (MailSourceClient.isRetryableMicrosoftOAuthFailure(e)) {
                microsoftOAuthService.invalidateDestinationCachedToken(imapTarget.userId());
                throw new IllegalStateException(MICROSOFT_DESTINATION_ACCESS_REVOKED_MESSAGE, e);
            }
            throw new IllegalStateException("Failed to append destination mail message", e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    public List<String> listFolders(ImapAppendDestinationTarget target) {
        Session session = mailSessionFactory().destinationImapSession(target);
        Store store = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(target.tls()));
            store.connect(target.host(), target.port(), target.username(), resolveSecret(target));

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
            if (MailSourceClient.isRetryableMicrosoftOAuthFailure(e)) {
                microsoftOAuthService.invalidateDestinationCachedToken(target.userId());
                throw new IllegalStateException(MICROSOFT_DESTINATION_ACCESS_REVOKED_MESSAGE, e);
            }
            throw new IllegalStateException("Failed to list destination mailbox folders", e);
        } finally {
            closeQuietly(store);
        }
    }

    public EmailAccountConnectionTestResult testConnection(ImapAppendDestinationTarget target) {
        Session session = mailSessionFactory().destinationImapSession(target);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(mailSessionFactory().imapStoreProtocol(target.tls()));
            store.connect(target.host(), target.port(), target.username(), resolveSecret(target));
            folder = store.getFolder(target.folder());
            if (!folder.exists()) {
                throw new IllegalStateException("The mailbox path " + target.folder() + " does not exist on " + target.host() + ".");
            }
            folder.open(Folder.READ_ONLY);
            int visibleMessageCount = folder.getMessageCount();
            return new EmailAccountConnectionTestResult(
                    true,
                    "Connection test succeeded.",
                    "IMAP",
                    target.host(),
                    target.port(),
                    target.tls(),
                    target.authMethod().name(),
                    target.oauthProvider().name(),
                    true,
                    target.folder(),
                    true,
                    false,
                    null,
                    null,
                    visibleMessageCount,
                    null,
                    visibleMessageCount > 0,
                    null,
                    null);
        } catch (MessagingException e) {
            if (MailSourceClient.isRetryableMicrosoftOAuthFailure(e)) {
                microsoftOAuthService.invalidateDestinationCachedToken(target.userId());
                throw new IllegalStateException(MICROSOFT_DESTINATION_ACCESS_REVOKED_MESSAGE, e);
            }
            throw new IllegalStateException("Failed to connect to the destination mailbox", e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
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

    private void ensureFolderExists(Folder folder, ImapAppendDestinationTarget target) throws MessagingException {
        String folderName = target.folder();
        if (folder.exists()) {
            return;
        }
        Object folderLock = destinationFolderLocks.computeIfAbsent(destinationFolderKey(target), ignored -> new Object());
        synchronized (folderLock) {
            if (folder.exists()) {
                return;
            }
            try {
                if (folder.create(Folder.HOLDS_MESSAGES) || folder.exists()) {
                    return;
                }
            } catch (MessagingException createError) {
                if (folder.exists()) {
                    return;
                }
                throw createError;
            }
        }
        throw new IllegalStateException("Unable to create destination mailbox folder " + folderName);
    }

    private String destinationFolderKey(ImapAppendDestinationTarget target) {
        return String.join("|",
                String.valueOf(target.tls()),
                String.valueOf(target.host()),
                String.valueOf(target.port()),
                String.valueOf(target.username()),
                String.valueOf(target.folder()));
    }

    private String resolveSecret(ImapAppendDestinationTarget target) {
        if (target.authMethod() == InboxBridgeConfig.AuthMethod.PASSWORD) {
            return target.password();
        }
        if (target.oauthProvider() != InboxBridgeConfig.OAuthProvider.MICROSOFT) {
            throw new IllegalStateException("Only Microsoft OAuth2 is currently supported for IMAP destination mailboxes.");
        }
        return microsoftOAuthService.getDestinationAccessToken(target.userId());
    }

    private void closeQuietly(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        } catch (MessagingException ignored) {
        }
    }

    private void closeQuietly(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
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

    private MailSessionFactory mailSessionFactory() {
        if (mailSessionFactory != null) {
            return mailSessionFactory;
        }
        MailSessionFactory fallback = new MailSessionFactory();
        fallback.setMailClientConfig(new dev.inboxbridge.config.MailClientConfig() {
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
        });
        mailSessionFactory = fallback;
        return mailSessionFactory;
    }
}
