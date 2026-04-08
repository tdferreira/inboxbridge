package dev.inboxbridge.service.destination;

import dev.inboxbridge.service.oauth.GoogleOAuthService;
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
