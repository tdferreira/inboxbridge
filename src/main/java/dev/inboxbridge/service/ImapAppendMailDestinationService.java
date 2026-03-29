package dev.inboxbridge.service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;
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

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

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
        Properties properties = new Properties();
        properties.put("mail.store.protocol", imapTarget.tls() ? "imaps" : "imap");
        properties.put("mail.imap.ssl.enable", imapTarget.tls());
        properties.put("mail.imaps.ssl.enable", imapTarget.tls());
        properties.put("mail.imap.ssl.checkserveridentity", "true");
        properties.put("mail.imaps.ssl.checkserveridentity", "true");
        properties.put("mail.imap.timeout", "20000");
        properties.put("mail.imaps.timeout", "20000");
        properties.put("mail.imap.connectiontimeout", "20000");
        properties.put("mail.imaps.connectiontimeout", "20000");
        if (imapTarget.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2) {
            properties.put("mail.imap.auth.mechanisms", "XOAUTH2");
            properties.put("mail.imap.auth.login.disable", "true");
            properties.put("mail.imap.auth.plain.disable", "true");
            properties.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            properties.put("mail.imaps.auth.login.disable", "true");
            properties.put("mail.imaps.auth.plain.disable", "true");
        }

        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(imapTarget.tls() ? "imaps" : "imap");
            store.connect(imapTarget.host(), imapTarget.port(), imapTarget.username(), resolveSecret(imapTarget));
            folder = store.getFolder(imapTarget.folder());
            if (!folder.exists() && !folder.create(Folder.HOLDS_MESSAGES)) {
                throw new IllegalStateException("Unable to create destination mailbox folder " + imapTarget.folder());
            }
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
}