package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;

class SourceTransportSecurityServiceTest {

    @Test
    void prefersImplicitTlsWhenSecurePortResponds() {
        StubSourceTransportSecurityService service = new StubSourceTransportSecurityService(true, true);

        Optional<Integer> detectedPort = service.detectRecommendedTlsPort(
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                143);

        assertEquals(Optional.of(993), detectedPort);
    }

    @Test
    void fallsBackToStartTlsOnTheCurrentPlaintextPort() {
        StubSourceTransportSecurityService service = new StubSourceTransportSecurityService(false, true);

        Optional<Integer> detectedPort = service.detectRecommendedTlsPort(
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                143);

        assertEquals(Optional.of(143), detectedPort);
    }

    @Test
    void detectRecommendedUpgradePromotesSamePortStartTlsAccounts() {
        StubSourceTransportSecurityService service = new StubSourceTransportSecurityService(false, true);
        RuntimeEmailAccount account = new RuntimeEmailAccount(
                "source-42",
                "USER_SOURCE",
                7L,
                "owner@example.com",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                143,
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "user@example.com",
                "Secret#123",
                "",
                Optional.of("INBOX"),
                false,
                null,
                Optional.of("Imported/Test"),
                null);

        SourceTransportSecurityService.TlsUpgrade upgrade = service.detectRecommendedUpgrade(account).orElseThrow();

        assertEquals(143, upgrade.securePort());
        assertEquals(true, upgrade.upgradedAccount().tls());
        assertEquals(143, upgrade.upgradedAccount().port());
    }

    private static final class StubSourceTransportSecurityService extends SourceTransportSecurityService {
        private final boolean implicitTlsSupported;
        private final boolean startTlsSupported;

        private StubSourceTransportSecurityService(boolean implicitTlsSupported, boolean startTlsSupported) {
            this.implicitTlsSupported = implicitTlsSupported;
            this.startTlsSupported = startTlsSupported;
        }

        @Override
        protected boolean supportsImplicitTls(String host, int securePort) {
            return implicitTlsSupported;
        }

        @Override
        protected boolean supportsStartTls(InboxBridgeConfig.Protocol protocol, String host, int port) {
            return startTlsSupported;
        }
    }
}
