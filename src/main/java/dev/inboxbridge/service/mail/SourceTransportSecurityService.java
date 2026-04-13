package dev.inboxbridge.service.mail;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Locale;
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

    public Optional<Integer> detectRecommendedTlsPort(
            InboxBridgeConfig.Protocol protocol,
            String host,
            Integer currentPort) {
        String normalizedHost = host == null ? "" : host.trim();
        if (normalizedHost.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> implicitTlsPort = detectImplicitTlsPort(protocol, normalizedHost);
        if (implicitTlsPort.isPresent()) {
            return implicitTlsPort;
        }
        int startTlsPort = currentPort == null || currentPort <= 0
                ? plainPort(protocol)
                : currentPort;
        return supportsStartTls(protocol, normalizedHost, startTlsPort)
                ? Optional.of(startTlsPort)
                : Optional.empty();
    }

    public Optional<TlsUpgrade> detectRecommendedUpgrade(RuntimeEmailAccount account) {
        if (account == null || account.tls()) {
            return Optional.empty();
        }
        return detectRecommendedTlsPort(account.protocol(), account.host(), account.port())
                .map(securePort -> new TlsUpgrade(
                        securePort,
                        upgradedAccount(account, securePort)));
    }

    public String insecureConnectionMessage(InboxBridgeConfig.Protocol protocol, int securePort) {
        return "This source mail server supports TLS on port " + securePort
                + " for " + protocol.name() + ". Enable TLS instead of saving an unsafe plain-text connection.";
    }

    public String insecureDestinationConnectionMessage(int securePort) {
        return "This destination mail server supports TLS on port " + securePort
                + " for IMAP. Enable TLS instead of saving an unsafe plain-text connection.";
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

    protected int plainPort(InboxBridgeConfig.Protocol protocol) {
        return protocol == InboxBridgeConfig.Protocol.IMAP ? 143 : 110;
    }

    protected boolean supportsStartTls(InboxBridgeConfig.Protocol protocol, String host, int port) {
        if (port <= 0 || port == securePort(protocol)) {
            return false;
        }
        return switch (protocol) {
            case IMAP -> supportsImapStartTls(host, port);
            case POP3 -> supportsPop3StartTls(host, port);
        };
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

    private boolean supportsImapStartTls(String host, int port) {
        try (Socket socket = openPlainProbeSocket(host, port)) {
            ProbeReader reader = new ProbeReader(socket);
            reader.readLine();
            reader.writeLine("a001 CAPABILITY");
            return reader.readUntilTaggedResponse("a001")
                    .toUpperCase(Locale.ROOT)
                    .contains("STARTTLS");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean supportsPop3StartTls(String host, int port) {
        try (Socket socket = openPlainProbeSocket(host, port)) {
            ProbeReader reader = new ProbeReader(socket);
            reader.readLine();
            reader.writeLine("CAPA");
            return reader.readMultilineResponse()
                    .toUpperCase(Locale.ROOT)
                    .contains("STLS");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Socket openPlainProbeSocket(String host, int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), probeTimeoutMillis());
        socket.setSoTimeout(probeTimeoutMillis());
        return socket;
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

    private static final class ProbeReader {
        private final java.io.BufferedReader reader;
        private final java.io.BufferedWriter writer;

        private ProbeReader(Socket socket) throws Exception {
            this.reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets.US_ASCII));
            this.writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets.US_ASCII));
        }

        private String readLine() throws Exception {
            String line = reader.readLine();
            return line == null ? "" : line;
        }

        private void writeLine(String line) throws Exception {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        private String readUntilTaggedResponse(String tag) throws Exception {
            StringBuilder response = new StringBuilder();
            for (int index = 0; index < 32; index += 1) {
                String line = readLine();
                if (line.isBlank()) {
                    continue;
                }
                response.append(line).append('\n');
                if (line.regionMatches(true, 0, tag, 0, tag.length())) {
                    break;
                }
            }
            return response.toString();
        }

        private String readMultilineResponse() throws Exception {
            StringBuilder response = new StringBuilder();
            for (int index = 0; index < 64; index += 1) {
                String line = readLine();
                if (line.isEmpty()) {
                    continue;
                }
                response.append(line).append('\n');
                if (".".equals(line)) {
                    break;
                }
            }
            return response.toString();
        }
    }

    public record TlsUpgrade(int securePort, RuntimeEmailAccount upgradedAccount) {
    }
}
