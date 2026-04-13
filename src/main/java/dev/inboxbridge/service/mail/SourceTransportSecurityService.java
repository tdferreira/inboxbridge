package dev.inboxbridge.service.mail;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.MailClientConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Detects whether a source mailbox host exposes an implicit TLS endpoint on the
 * standard secure IMAP or POP3 port. The probe intentionally trusts any
 * certificate because it is only used to decide whether InboxBridge should
 * insist on a secure transport, not to carry mailbox credentials.
 */
@ApplicationScoped
public class SourceTransportSecurityService {

    private static final TrustManager[] TRUST_ALL_MANAGERS = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    private static final SSLSocketFactory PROBE_SOCKET_FACTORY = buildProbeSocketFactory();

    @Inject
    MailClientConfig mailClientConfig;

    public Optional<Integer> detectImplicitTlsPort(InboxBridgeConfig.Protocol protocol, String host) {
        String normalizedHost = host == null ? "" : host.trim();
        if (normalizedHost.isEmpty()) {
            return Optional.empty();
        }
        int securePort = securePort(protocol);
        return supportsImplicitTls(normalizedHost, securePort) ? Optional.of(securePort) : Optional.empty();
    }

    public Optional<TlsUpgrade> detectRecommendedUpgrade(RuntimeEmailAccount account) {
        if (account == null || account.tls()) {
            return Optional.empty();
        }
        return detectImplicitTlsPort(account.protocol(), account.host())
                .map(securePort -> new TlsUpgrade(
                        securePort,
                        upgradedAccount(account, securePort)));
    }

    public String insecureConnectionMessage(InboxBridgeConfig.Protocol protocol, int securePort) {
        return "This source mail server supports TLS on port " + securePort
                + " for " + protocol.name() + ". Enable TLS instead of saving an unsafe plain-text connection.";
    }

    protected boolean supportsImplicitTls(String host, int securePort) {
        int timeoutMillis = probeTimeoutMillis();
        try (Socket rawSocket = new Socket()) {
            rawSocket.connect(new InetSocketAddress(host, securePort), timeoutMillis);
            try (SSLSocket socket = (SSLSocket) PROBE_SOCKET_FACTORY.createSocket(
                    rawSocket,
                    host,
                    securePort,
                    true)) {
                socket.setSoTimeout(timeoutMillis);
                socket.startHandshake();
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    protected int securePort(InboxBridgeConfig.Protocol protocol) {
        return protocol == InboxBridgeConfig.Protocol.IMAP ? 993 : 995;
    }

    private RuntimeEmailAccount upgradedAccount(RuntimeEmailAccount account, int securePort) {
        return new RuntimeEmailAccount(
                account.id(),
                account.ownerKind(),
                account.ownerUserId(),
                account.ownerUsername(),
                account.enabled(),
                account.protocol(),
                account.host(),
                securePort,
                true,
                account.authMethod(),
                account.oauthProvider(),
                account.username(),
                account.password(),
                account.oauthRefreshToken(),
                account.folder(),
                account.unreadOnly(),
                account.fetchMode(),
                account.customLabel(),
                account.postPollSettings(),
                account.destination());
    }

    private int probeTimeoutMillis() {
        Duration configured = mailClientConfig == null ? Duration.ofSeconds(5) : mailClientConfig.connectionTimeout();
        long millis = configured == null ? 5000L : configured.toMillis();
        long bounded = Math.max(1000L, Math.min(millis, 5000L));
        return (int) bounded;
    }

    private static SSLSocketFactory buildProbeSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, TRUST_ALL_MANAGERS, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize the TLS probe socket factory.", e);
        }
    }

    public record TlsUpgrade(int securePort, RuntimeEmailAccount upgradedAccount) {
    }
}
