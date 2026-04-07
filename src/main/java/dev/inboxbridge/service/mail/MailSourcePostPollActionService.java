package dev.inboxbridge.service.mail;

import org.jboss.logging.Logger;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.service.polling.PollCancellationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

/**
 * Applies source-side post-poll actions so MailSourceClient can stay focused on
 * fetch orchestration instead of IMAP mutation details.
 */
@ApplicationScoped
public class MailSourcePostPollActionService {

    private static final Logger LOG = Logger.getLogger(MailSourcePostPollActionService.class);

    private final MailSessionFactory mailSessionFactory;
    private final MailSourceConnectionService mailSourceConnectionService;
    private final MailSourceMessageMapper mailSourceMessageMapper;
    private final PollCancellationService pollCancellationService;

    @Inject
    MailSourcePostPollActionService(
            MailSessionFactory mailSessionFactory,
            MailSourceConnectionService mailSourceConnectionService,
            MailSourceMessageMapper mailSourceMessageMapper,
            PollCancellationService pollCancellationService) {
        this.mailSessionFactory = mailSessionFactory;
        this.mailSourceConnectionService = mailSourceConnectionService;
        this.mailSourceMessageMapper = mailSourceMessageMapper;
        this.pollCancellationService = pollCancellationService;
    }

    public void apply(RuntimeEmailAccount bridge, FetchedMessage message) {
        if (!bridge.postPollSettings().hasAnyAction()) {
            return;
        }
        if (bridge.protocol() != InboxBridgeConfig.Protocol.IMAP) {
            throw new IllegalStateException("Source-side message actions are only supported for IMAP accounts");
        }

        Session session = mailSessionFactory.sourceImapSession(bridge);
        Store store = null;
        Folder sourceFolder = null;
        Folder targetFolder = null;
        boolean expunge = false;
        try {
            store = session.getStore(mailSessionFactory.imapStoreProtocol(bridge.tls()));
            registerStore(store);
            mailSourceConnectionService.connectStore(store, bridge);
            sourceFolder = store.getFolder(message.folderName().orElse(bridge.primaryFolder()));
            registerFolder(sourceFolder);
            if (!sourceFolder.exists()) {
                throw new IllegalStateException("The mailbox path " + sourceFolder.getFullName() + " does not exist on " + bridge.host() + ".");
            }
            sourceFolder.open(Folder.READ_WRITE);
            Message sourceMessage = mailSourceMessageMapper.resolveSourceMessage(sourceFolder, message);
            if (sourceMessage == null) {
                throw new IllegalStateException("Unable to find the source message to apply post-poll actions for " + bridge.id());
            }

            if (bridge.postPollSettings().markAsRead()) {
                sourceMessage.setFlag(Flags.Flag.SEEN, true);
            }
            if (bridge.postPollSettings().action() == SourcePostPollAction.MOVE) {
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
            } else if (bridge.postPollSettings().action() == SourcePostPollAction.FORWARDED) {
                applyForwardedFlag(sourceMessage, bridge);
            } else if (bridge.postPollSettings().action() == SourcePostPollAction.DELETE) {
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

}
