package dev.inboxbridge.service.mail;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import jakarta.mail.Session;

@QuarkusComponentTest
@TestConfigProperty(key = "inboxbridge.mail.connection-timeout", value = "PT7S")
@TestConfigProperty(key = "inboxbridge.mail.operation-timeout", value = "PT13S")
@TestConfigProperty(key = "inboxbridge.mail.idle-operation-timeout", value = "PT0S")
class MailSessionFactoryQuarkusTest {

    @Inject
    MailSessionFactory mailSessionFactory;

    @Test
    void buildsSharedImapAndPop3TimeoutPropertiesFromConfig() {
        RuntimeEmailAccount imapSource = new RuntimeEmailAccount(
                "source-1",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.test",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);
        RuntimeEmailAccount popSource = new RuntimeEmailAccount(
                "source-2",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.POP3,
                "pop.example.test",
                995,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "",
                Optional.empty(),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);

        Session imapSession = mailSessionFactory.sourceImapSession(imapSource);
        Session popSession = mailSessionFactory.sourcePop3Session(popSource);
        Session idleSession = mailSessionFactory.idleImapSession(imapSource);

        assertEquals("7000", imapSession.getProperties().getProperty("mail.imap.connectiontimeout"));
        assertEquals("13000", imapSession.getProperties().getProperty("mail.imap.timeout"));
        assertEquals("7000", popSession.getProperties().getProperty("mail.pop3.connectiontimeout"));
        assertEquals("13000", popSession.getProperties().getProperty("mail.pop3.timeout"));
        assertEquals("0", idleSession.getProperties().getProperty("mail.imap.timeout"));
    }

    @Test
    void allowsPlainSourceSessionsButRejectsPlainDestinationSessions() {
        RuntimeEmailAccount plainImapSource = new RuntimeEmailAccount(
                "source-1",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.test",
                143,
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);
        RuntimeEmailAccount plainPopSource = new RuntimeEmailAccount(
                "source-2",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.POP3,
                "pop.example.test",
                110,
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "",
                Optional.empty(),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);
        ImapAppendDestinationTarget plainDestination = new ImapAppendDestinationTarget(
                "destination-1",
                7L,
                "alice",
                "CUSTOM_IMAP",
                "imap.example.test",
                143,
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "INBOX");

        assertEquals("imap", mailSessionFactory.sourceImapSession(plainImapSource).getProperties().getProperty("mail.store.protocol"));
        assertEquals("pop3", mailSessionFactory.sourcePop3Session(plainPopSource).getProperties().getProperty("mail.store.protocol"));

        IllegalArgumentException destinationError = assertThrows(
                IllegalArgumentException.class,
                () -> mailSessionFactory.destinationImapSession(plainDestination));

        assertEquals("InboxBridge requires TLS for every mailbox connection.", destinationError.getMessage());
    }
}
