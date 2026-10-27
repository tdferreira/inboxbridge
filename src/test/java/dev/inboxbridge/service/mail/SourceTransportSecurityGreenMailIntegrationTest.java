package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.MailClientConfig;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import jakarta.mail.MessagingException;

class SourceTransportSecurityGreenMailIntegrationTest {
    @RegisterExtension
    final GreenMailExtension mail = new GreenMailExtension(new ServerSetup(0, null, ServerSetup.PROTOCOL_IMAPS));

    @Test
    void unauthenticatedProbeCannotRelaxCredentialBearingTlsConnections() throws Exception {
        MailClientConfig config = mock(MailClientConfig.class);
        when(config.connectionTimeout()).thenReturn(Duration.ofSeconds(2));
        when(config.operationTimeout()).thenReturn(Duration.ofSeconds(2));
        MailSessionFactory factory = new MailSessionFactory();
        factory.setMailClientConfig(config);
        var target = new ImapAppendDestinationTarget("tls-regression", 7L, "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM, "localhost", 993, true,
                InboxBridgeConfig.AuthMethod.PASSWORD, InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.test", "synthetic-password", "INBOX");
        var session = factory.destinationImapSession(target);
        mail.setUser(target.username(), target.password());

        // GreenMail's self-signed certificate is deliberately absent from the default trust store.
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var store = session.getStore()) {
                MessagingException failure = assertThrows(MessagingException.class,
                        () -> store.connect("localhost", mail.getImaps().getPort(), target.username(), target.password()));
                Throwable cause = failure;
                while (cause != null && !(cause instanceof SSLHandshakeException)) cause = cause.getCause();
                assertTrue(cause instanceof SSLHandshakeException, "Certificate rejection must precede authentication");
            }
            if (attempt == 0) {
                SourceTransportSecurityService probe = new SourceTransportSecurityService();
                probe.mailClientConfig = config;
                assertTrue(probe.supportsImplicitTls("localhost", mail.getImaps().getPort()));
            }
        }
    }
}
