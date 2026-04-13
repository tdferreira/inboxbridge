package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.MailClientConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import jakarta.mail.Session;

class MailSessionFactoryTest {

    @Test
    void sourceImapSessionUsesStartTlsWhenTlsIsEnabledOnPlainImapPort() {
        MailSessionFactory factory = factory();

        Session session = factory.sourceImapSession(runtimeImapAccount(143, true));

        assertEquals("imap", session.getProperty("mail.store.protocol"));
        assertEquals("true", String.valueOf(session.getProperties().get("mail.imap.starttls.enable")));
        assertEquals("true", String.valueOf(session.getProperties().get("mail.imap.starttls.required")));
        assertEquals("false", String.valueOf(session.getProperties().get("mail.imap.ssl.enable")));
    }

    @Test
    void sourceImapSessionUsesImplicitTlsOnImapsPort() {
        MailSessionFactory factory = factory();

        Session session = factory.sourceImapSession(runtimeImapAccount(993, true));

        assertEquals("imaps", session.getProperty("mail.store.protocol"));
        assertEquals("true", String.valueOf(session.getProperties().get("mail.imap.ssl.enable")));
        assertEquals("false", String.valueOf(session.getProperties().get("mail.imap.starttls.enable")));
    }

    private static MailSessionFactory factory() {
        MailSessionFactory factory = new MailSessionFactory();
        factory.setMailClientConfig(new MailClientConfig() {
            @Override
            public Duration connectionTimeout() {
                return Duration.ofSeconds(5);
            }

            @Override
            public Duration operationTimeout() {
                return Duration.ofSeconds(20);
            }

            @Override
            public Duration idleOperationTimeout() {
                return Duration.ofMinutes(29);
            }
        });
        return factory;
    }

    private static RuntimeEmailAccount runtimeImapAccount(int port, boolean tls) {
        return new RuntimeEmailAccount(
                "source-1",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.test",
                port,
                tls,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test",
                "secret",
                "",
                java.util.Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                java.util.Optional.empty(),
                SourcePostPollSettings.none(),
                null);
    }
}
