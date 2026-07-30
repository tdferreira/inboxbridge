package dev.inboxbridge.service.destination;

import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.dto.GmailImportResponse;

class GmailImportServiceTest {

    @Test
    void importMessageRetriesOnceAfterUnauthorizedResponse() {
        GmailImportService service = new GmailImportService();
        service.googleOAuthService = new FakeGoogleOAuthService("expired-token", "fresh-token");
        service.objectMapper = new ObjectMapper();
        service.userGmailConfigService = new FakeUserGmailConfigService();
        service.httpClient = new FakeHttpClient(
                new FakeHttpResponse(401, "{\"error\":\"expired\"}"),
                new FakeHttpResponse(200, "{\"id\":\"gmail-message-1\",\"threadId\":\"thread-1\"}"));

        GmailImportResponse response = service.importMessage(
                new GmailTarget(
                        "user-gmail:8",
                        8L,
                        "john-doe",
                        "me",
                        "client",
                        "secret",
                        "",
                        "https://localhost:3000/api/google-oauth/callback",
                        true,
                        false,
                        false),
                "hello".getBytes(),
                List.of("INBOX"));

        assertEquals("gmail-message-1", response.id());
        assertEquals("user-gmail:8", ((FakeGoogleOAuthService) service.googleOAuthService).clearedSubjectKey);
    }

    @Test
    void importMessageMarksUserGmailLinkRevokedAfterRepeatedUnauthorizedResponses() {
        GmailImportService service = new GmailImportService();
        service.googleOAuthService = new FakeGoogleOAuthService("expired-token", "fresh-token");
        service.objectMapper = new ObjectMapper();
        FakeUserGmailConfigService userGmailConfigService = new FakeUserGmailConfigService();
        service.userGmailConfigService = userGmailConfigService;
        service.httpClient = new FakeHttpClient(
                new FakeHttpResponse(401, "{\"error\":\"expired\"}"),
                new FakeHttpResponse(401, "{\"error\":\"revoked\"}"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.importMessage(
                new GmailTarget(
                        "user-gmail:10",
                        10L,
                        "john-doe",
                        "me",
                        "client",
                        "secret",
                        "",
                        "https://localhost:3000/api/google-oauth/callback",
                        true,
                        false,
                        false),
                "hello".getBytes(),
                List.of("INBOX")));

        assertEquals("The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.", error.getMessage());
        assertEquals("user-gmail:10", userGmailConfigService.lastRevokedSubjectKey);
    }

    @Test
    void importMessageClassifiesGmailInvalidAttachmentResponseAsPermanentMessageRejection() {
        GmailImportService service = new GmailImportService();
        service.googleOAuthService = new FakeGoogleOAuthService("access-token");
        service.objectMapper = new ObjectMapper();
        service.userGmailConfigService = new FakeUserGmailConfigService();
        service.httpClient = new FakeHttpClient(new FakeHttpResponse(400, """
                {
                  "error": {
                    "code": 400,
                    "message": "Invalid attachment. Please check https://support.google.com/mail/answer/6590.",
                    "errors": [
                      {
                        "message": "Invalid attachment. Please check https://support.google.com/mail/answer/6590.",
                        "domain": "global",
                        "reason": "invalidArgument"
                      }
                    ],
                    "status": "INVALID_ARGUMENT"
                  }
                }
                """));

        GmailInvalidAttachmentException error = assertThrows(
                GmailInvalidAttachmentException.class,
                () -> service.importMessage(userTarget(), "bad attachment".getBytes(), List.of("INBOX")));

        assertEquals(400, error.statusCode());
    }

    @Test
    void importMessageKeepsOtherBadRequestsFatal() {
        GmailImportService service = new GmailImportService();
        service.googleOAuthService = new FakeGoogleOAuthService("access-token");
        service.objectMapper = new ObjectMapper();
        service.userGmailConfigService = new FakeUserGmailConfigService();
        service.httpClient = new FakeHttpClient(new FakeHttpResponse(400, """
                {
                  "error": {
                    "code": 400,
                    "message": "Invalid label",
                    "errors": [
                      {
                        "message": "Invalid label",
                        "domain": "global",
                        "reason": "invalidArgument"
                      }
                    ],
                    "status": "INVALID_ARGUMENT"
                  }
                }
                """));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.importMessage(userTarget(), "valid message".getBytes(), List.of("missing-label")));

        assertEquals(IllegalStateException.class, error.getClass());
    }

    @Test
    void importMessageForSystemTargetFailsClearlyWhenEnvRefreshTokenIsBlockedByPolicy() {
        GmailImportService service = new GmailImportService();
        service.googleOAuthService = new FakeGoogleOAuthService("expired-token");
        service.objectMapper = new ObjectMapper();
        service.userGmailConfigService = new FakeUserGmailConfigService();
        service.config = new TestConfig();
        service.systemOAuthAppSettingsService = new SystemOAuthAppSettingsService() {
            @Override
            public String googleDestinationUser() {
                return "me";
            }

            @Override
            public String googleClientId() {
                return "client";
            }

            @Override
            public String googleClientSecret() {
                return "secret";
            }

            @Override
            public String googleRefreshToken() {
                return "";
            }

            @Override
            public String googleRedirectUri() {
                return "https://localhost:3000/api/google-oauth/callback";
            }

            @Override
            public boolean envManagedGoogleRefreshTokenBlockedByPolicy() {
                return true;
            }
        };

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.importMessage("hello".getBytes(), List.of("INBOX")));

        assertEquals(
                "InboxBridge is configured to block env-managed mailbox secrets. Save the Gmail destination refresh token in the admin UI instead of using GMAIL_REFRESH_TOKEN.",
                error.getMessage());
    }

    private static final class TestConfig implements dev.inboxbridge.config.InboxBridgeConfig {
        @Override
        public boolean pollEnabled() { return true; }
        @Override
        public String pollInterval() { return "5m"; }
        @Override
        public int fetchWindow() { return 50; }
        @Override
        public Duration sourceHostMinSpacing() { return Duration.ofSeconds(1); }
        @Override
        public int sourceHostMaxConcurrency() { return 2; }
        @Override
        public Duration destinationProviderMinSpacing() { return Duration.ofMillis(250); }
        @Override
        public int destinationProviderMaxConcurrency() { return 1; }
        @Override
        public Duration throttleLeaseTtl() { return Duration.ofMinutes(2); }
        @Override
        public int adaptiveThrottleMaxMultiplier() { return 6; }
        @Override
        public double successJitterRatio() { return 0.2d; }
        @Override
        public Duration maxSuccessJitter() { return Duration.ofSeconds(30); }
        @Override
        public boolean multiUserEnabled() { return true; }
        @Override
        public dev.inboxbridge.config.InboxBridgeConfig.Security security() { return null; }
        @Override
        public Gmail gmail() {
            return new Gmail() {
                @Override
                public String destinationUser() { return "me"; }
                @Override
                public String clientId() { return "client"; }
                @Override
                public String clientSecret() { return "secret"; }
                @Override
                public String refreshToken() { return ""; }
                @Override
                public String redirectUri() { return "https://localhost:3000/api/google-oauth/callback"; }
                @Override
                public boolean createMissingLabels() { return true; }
                @Override
                public boolean neverMarkSpam() { return false; }
                @Override
                public boolean processForCalendar() { return false; }
            };
        }
        @Override
        public Microsoft microsoft() { return null; }
        @Override
        public List<Source> sources() { return List.of(); }
    }

    private static GmailTarget userTarget() {
        return new GmailTarget(
                "user-gmail:8",
                8L,
                "john-doe",
                "me",
                "client",
                "secret",
                "",
                "https://localhost:3000/api/google-oauth/callback",
                true,
                false,
                false);
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        private final Queue<String> accessTokens = new ConcurrentLinkedQueue<>();
        private String clearedSubjectKey;

        private FakeGoogleOAuthService(String... accessTokens) {
            this.accessTokens.addAll(List.of(accessTokens));
        }

        @Override
        public String getAccessToken(GoogleOAuthProfile profile) {
            String token = accessTokens.poll();
            return token == null ? "fallback-token" : token;
        }

        @Override
        public void clearCachedToken(String subjectKey) {
            this.clearedSubjectKey = subjectKey;
        }
    }

    private static final class FakeUserGmailConfigService extends UserGmailConfigService {
        private String lastRevokedSubjectKey;

        @Override
        public boolean markGoogleAccessRevoked(GmailApiDestinationTarget target) {
            this.lastRevokedSubjectKey = target.subjectKey();
            return true;
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final Queue<HttpResponse<String>> responses = new ConcurrentLinkedQueue<>();

        private FakeHttpClient(HttpResponse<String>... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responses.poll();
            if (response == null) {
                throw new IOException("No fake response configured for " + request.uri());
            }
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeHttpResponse(int statusCode, String body) implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com")).build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://gmail.googleapis.com");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
