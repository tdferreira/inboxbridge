package dev.inboxbridge.service.mail;

import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.testsupport.ScopedLogCapture;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;

class MailSourceConnectionServiceTest {

    @Test
    void runtimeMicrosoftOAuthRetriesOnceAndInvalidatesCachedToken() throws Exception {
        FakeMicrosoftOAuthService microsoft = new FakeMicrosoftOAuthService("stale-token", "fresh-token");
        MailSourceConnectionService service = new MailSourceConnectionService(microsoft, new FakeGoogleOAuthService());
        RecordingStore store = new RecordingStore();
        store.failFirst("* BYE Session invalidated - Invalid");

        java.util.List<ScopedLogCapture.CapturedRecord> records;
        try (ScopedLogCapture capture = ScopedLogCapture.captureWarnings(MailSourceConnectionService.class)) {
            service.connectStore(store, runtimeAccount(InboxBridgeConfig.OAuthProvider.MICROSOFT));
            records = capture.records();
        }

        assertEquals(2, store.connectAttempts);
        assertEquals("source-1", microsoft.invalidatedSourceId);
        assertEquals("fresh-token", store.lastPassword);
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.getFirst().level());
        assertEquals("Microsoft session for source-1 was rejected; invalidating the cached token and retrying once",
                records.getFirst().message());
        assertTrue(records.getFirst().thrown() instanceof MessagingException);
    }

    @Test
    void runtimeGoogleOAuthRetriesOnceAndClearsCachedToken() throws Exception {
        FakeGoogleOAuthService google = new FakeGoogleOAuthService("stale-token", "fresh-token");
        MailSourceConnectionService service = new MailSourceConnectionService(new FakeMicrosoftOAuthService(), google);
        RecordingStore store = new RecordingStore();
        store.failFirst("* BYE Session invalidated - Invalid");

        java.util.List<ScopedLogCapture.CapturedRecord> records;
        try (ScopedLogCapture capture = ScopedLogCapture.captureWarnings(MailSourceConnectionService.class)) {
            service.connectStore(store, runtimeAccount(InboxBridgeConfig.OAuthProvider.GOOGLE));
            records = capture.records();
        }

        assertEquals(2, store.connectAttempts);
        assertEquals("source-google:source-1", google.clearedSubjectKey);
        assertEquals("fresh-token", store.lastPassword);
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.getFirst().level());
        assertEquals("Google session for source-1 was rejected; invalidating the cached token and retrying once",
                records.getFirst().message());
        assertTrue(records.getFirst().thrown() instanceof MessagingException);
    }

    @Test
    void sourcePasswordConnectionDoesNotRetryOrTouchOauthServices() throws Exception {
        FakeMicrosoftOAuthService microsoft = new FakeMicrosoftOAuthService();
        FakeGoogleOAuthService google = new FakeGoogleOAuthService();
        MailSourceConnectionService service = new MailSourceConnectionService(microsoft, google);
        RecordingStore store = new RecordingStore();

        service.connectStore(store, configSource());

        assertEquals(1, store.connectAttempts);
        assertEquals("secret", store.lastPassword);
        assertEquals(null, microsoft.invalidatedSourceId);
        assertEquals(null, google.clearedSubjectKey);
    }

    @Test
    void nonRetryableOauthFailureIsPropagatedWithoutInvalidation() {
        FakeMicrosoftOAuthService microsoft = new FakeMicrosoftOAuthService("stale-token");
        MailSourceConnectionService service = new MailSourceConnectionService(microsoft, new FakeGoogleOAuthService());
        RecordingStore store = new RecordingStore();
        store.failFirst(new MessagingException("unexpected protocol failure"));

        assertThrows(MessagingException.class, () -> service.connectStore(store, runtimeAccount(InboxBridgeConfig.OAuthProvider.MICROSOFT)));
        assertEquals("stale-token", store.lastPassword);
        assertEquals(null, microsoft.invalidatedSourceId);
    }

    private static RuntimeEmailAccount runtimeAccount(InboxBridgeConfig.OAuthProvider provider) {
        return new RuntimeEmailAccount(
                "source-1",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.test",
                993,
                true,
                provider == InboxBridgeConfig.OAuthProvider.NONE ? InboxBridgeConfig.AuthMethod.PASSWORD : InboxBridgeConfig.AuthMethod.OAUTH2,
                provider,
                "alice@example.test",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                SourcePostPollSettings.none(),
                null);
    }

    private static InboxBridgeConfig.Source configSource() {
        return new InboxBridgeConfig.Source() {
            @Override
            public String id() {
                return "source-1";
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public InboxBridgeConfig.Protocol protocol() {
                return InboxBridgeConfig.Protocol.IMAP;
            }

            @Override
            public String host() {
                return "imap.example.test";
            }

            @Override
            public int port() {
                return 993;
            }

            @Override
            public boolean tls() {
                return true;
            }

            @Override
            public InboxBridgeConfig.AuthMethod authMethod() {
                return InboxBridgeConfig.AuthMethod.PASSWORD;
            }

            @Override
            public InboxBridgeConfig.OAuthProvider oauthProvider() {
                return InboxBridgeConfig.OAuthProvider.NONE;
            }

            @Override
            public String username() {
                return "alice@example.test";
            }

            @Override
            public String password() {
                return "secret";
            }

            @Override
            public Optional<String> oauthRefreshToken() {
                return Optional.empty();
            }

            @Override
            public Optional<String> folder() {
                return Optional.of("INBOX");
            }

            @Override
            public boolean unreadOnly() {
                return false;
            }

            @Override
            public SourceFetchMode fetchMode() {
                return SourceFetchMode.POLLING;
            }

            @Override
            public Optional<String> customLabel() {
                return Optional.empty();
            }
        };
    }

    private static final class RecordingStore extends Store {
        private final ArrayDeque<MessagingException> queuedFailures = new ArrayDeque<>();
        private int connectAttempts;
        private String lastPassword;

        private RecordingStore() {
            super(Session.getInstance(new Properties()), (URLName) null);
        }

        private void failFirst(String message) {
            queuedFailures.add(new MessagingException(message, new MessagingException("AUTHENTICATE failed")));
        }

        private void failFirst(MessagingException exception) {
            queuedFailures.add(exception);
        }

        @Override
        public void connect(String host, int port, String user, String password) throws MessagingException {
            connectAttempts++;
            lastPassword = password;
            if (!queuedFailures.isEmpty()) {
                throw queuedFailures.removeFirst();
            }
        }

        @Override
        public Folder getDefaultFolder() {
            return null;
        }

        @Override
        public Folder getFolder(String name) {
            return null;
        }

        @Override
        public Folder getFolder(URLName url) {
            return null;
        }

        @Override
        protected boolean protocolConnect(String host, int port, String user, String password) {
            return true;
        }
    }

    private static final class FakeMicrosoftOAuthService extends MicrosoftOAuthService {
        private final ArrayDeque<String> tokens = new ArrayDeque<>();
        private String invalidatedSourceId;

        private FakeMicrosoftOAuthService(String... tokens) {
            for (String token : tokens) {
                this.tokens.add(token);
            }
        }

        @Override
        public String getAccessToken(RuntimeEmailAccount bridge) {
            return tokens.isEmpty() ? "missing-token" : tokens.removeFirst();
        }

        @Override
        public void invalidateCachedToken(String sourceId) {
            invalidatedSourceId = sourceId;
        }
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        private final ArrayDeque<String> tokens = new ArrayDeque<>();
        private String clearedSubjectKey;

        private FakeGoogleOAuthService(String... tokens) {
            for (String token : tokens) {
                this.tokens.add(token);
            }
        }

        @Override
        public String getAccessToken(RuntimeEmailAccount bridge) {
            return tokens.isEmpty() ? "missing-token" : tokens.removeFirst();
        }

        @Override
        public void clearCachedToken(String subjectKey) {
            clearedSubjectKey = subjectKey;
        }
    }
}
