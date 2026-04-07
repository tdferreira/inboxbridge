package dev.inboxbridge.service;

import java.util.Properties;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.MailClientConfig;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Session;

/**
 * Builds protocol-specific Jakarta Mail sessions from one shared set of
 * timeout and TLS defaults so mailbox clients do not duplicate those details.
 */
@ApplicationScoped
public class MailSessionFactory {

    @Inject
    MailClientConfig mailClientConfig;

    public Session sourceImapSession(RuntimeEmailAccount account) {
        return Session.getInstance(imapProperties(account.tls(), usesOAuth(account), false));
    }

    public Session sourceImapSession(InboxBridgeConfig.Source source) {
        return Session.getInstance(imapProperties(source.tls(), usesOAuth(source), false));
    }

    public Session idleImapSession(RuntimeEmailAccount account) {
        Properties properties = imapProperties(account.tls(), usesOAuth(account), true);
        properties.put("mail.imap.closefoldersonstorefailure", "false");
        properties.put("mail.imaps.closefoldersonstorefailure", "false");
        return Session.getInstance(properties);
    }

    public Session sourcePop3Session(RuntimeEmailAccount account) {
        return Session.getInstance(pop3Properties(account.tls(), usesOAuth(account)));
    }

    public Session sourcePop3Session(InboxBridgeConfig.Source source) {
        return Session.getInstance(pop3Properties(source.tls(), usesOAuth(source)));
    }

    public Session destinationImapSession(ImapAppendDestinationTarget target) {
        return Session.getInstance(imapProperties(target.tls(), target.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2, false));
    }

    public String imapStoreProtocol(boolean tls) {
        return tls ? "imaps" : "imap";
    }

    public String pop3StoreProtocol(boolean tls) {
        return tls ? "pop3s" : "pop3";
    }

    private Properties imapProperties(boolean tls, boolean oauth, boolean idleWatch) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", imapStoreProtocol(tls));
        properties.put("mail.imap.ssl.enable", tls);
        properties.put("mail.imaps.ssl.enable", tls);
        properties.put("mail.imap.ssl.checkserveridentity", "true");
        properties.put("mail.imaps.ssl.checkserveridentity", "true");
        properties.put("mail.imap.connectiontimeout", timeoutMillis(mailClientConfig.connectionTimeout()));
        properties.put("mail.imaps.connectiontimeout", timeoutMillis(mailClientConfig.connectionTimeout()));
        properties.put("mail.imap.timeout", timeoutMillis(idleWatch
                ? mailClientConfig.idleOperationTimeout()
                : mailClientConfig.operationTimeout()));
        properties.put("mail.imaps.timeout", timeoutMillis(idleWatch
                ? mailClientConfig.idleOperationTimeout()
                : mailClientConfig.operationTimeout()));
        if (oauth) {
            configureImapOAuth(properties);
        }
        return properties;
    }

    private Properties pop3Properties(boolean tls, boolean oauth) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", pop3StoreProtocol(tls));
        properties.put("mail.pop3.ssl.enable", tls);
        properties.put("mail.pop3s.ssl.enable", tls);
        properties.put("mail.pop3.ssl.checkserveridentity", "true");
        properties.put("mail.pop3s.ssl.checkserveridentity", "true");
        properties.put("mail.pop3.connectiontimeout", timeoutMillis(mailClientConfig.connectionTimeout()));
        properties.put("mail.pop3s.connectiontimeout", timeoutMillis(mailClientConfig.connectionTimeout()));
        properties.put("mail.pop3.timeout", timeoutMillis(mailClientConfig.operationTimeout()));
        properties.put("mail.pop3s.timeout", timeoutMillis(mailClientConfig.operationTimeout()));
        if (oauth) {
            configurePop3OAuth(properties);
        }
        return properties;
    }

    private boolean usesOAuth(RuntimeEmailAccount account) {
        return account.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2;
    }

    private boolean usesOAuth(InboxBridgeConfig.Source source) {
        return source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2;
    }

    private String timeoutMillis(java.time.Duration duration) {
        return String.valueOf(duration.toMillis());
    }

    private void configureImapOAuth(Properties properties) {
        properties.put("mail.imap.auth.mechanisms", "XOAUTH2");
        properties.put("mail.imaps.auth.mechanisms", "XOAUTH2");
        properties.put("mail.imap.auth.login.disable", "true");
        properties.put("mail.imaps.auth.login.disable", "true");
        properties.put("mail.imap.auth.plain.disable", "true");
        properties.put("mail.imaps.auth.plain.disable", "true");
        properties.put("mail.imap.auth.xoauth2.disable", "false");
        properties.put("mail.imaps.auth.xoauth2.disable", "false");
    }

    private void configurePop3OAuth(Properties properties) {
        properties.put("mail.pop3.auth.mechanisms", "XOAUTH2");
        properties.put("mail.pop3s.auth.mechanisms", "XOAUTH2");
        properties.put("mail.pop3.auth.login.disable", "true");
        properties.put("mail.pop3s.auth.login.disable", "true");
        properties.put("mail.pop3.auth.plain.disable", "true");
        properties.put("mail.pop3s.auth.plain.disable", "true");
        properties.put("mail.pop3.auth.xoauth2.disable", "false");
        properties.put("mail.pop3s.auth.xoauth2.disable", "false");
        properties.put("mail.pop3.auth.xoauth2.two.line.authentication.format", "true");
        properties.put("mail.pop3s.auth.xoauth2.two.line.authentication.format", "true");
    }
}
