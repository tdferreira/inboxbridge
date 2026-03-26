package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.service.GoogleOAuthService;
import jakarta.ws.rs.core.Response;

class GoogleOAuthResourceTest {

    @Test
    void startRedirectsBrowserToGoogleAuthorizationUrl() {
        GoogleOAuthResource resource = new GoogleOAuthResource();
        resource.googleOAuthService = new FakeGoogleOAuthService();

        Response response = resource.startSystem();

        assertEquals(303, response.getStatus());
        assertEquals(URI.create("https://accounts.google.com/o/oauth2/v2/auth?client_id=demo"), response.getLocation());
    }

    @Test
    void callbackRendersBrowserExchangeHtml() {
        GoogleOAuthResource resource = new GoogleOAuthResource();
        resource.googleOAuthService = new FakeGoogleOAuthService();

        String html = resource.callback("code-123", "state-1");

        assertTrue(html.contains("Google OAuth Code Received"));
        assertTrue(html.contains("Copy Code"));
        assertTrue(html.contains("Exchange Code In Browser"));
        assertTrue(html.contains("Return To Admin UI"));
        assertTrue(html.contains("Leave this page without exchanging the code?"));
        assertTrue(html.contains("Returning to the admin UI in"));
        assertTrue(html.contains("code-123"));
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        @Override
        public String buildAuthorizationUrl() {
            return "https://accounts.google.com/o/oauth2/v2/auth?client_id=demo";
        }

        @Override
        public GoogleOAuthProfile systemProfileForCallbacks() {
            return new GoogleOAuthProfile(
                    "gmail-destination",
                    "client-id",
                    "client-secret",
                    "refresh-token",
                    "https://localhost:3000/api/google-oauth/callback");
        }

        @Override
        public String buildAuthorizationUrlWithState(GoogleOAuthProfile profile, String targetLabel) {
            return "https://accounts.google.com/o/oauth2/v2/auth?client_id=demo";
        }

        @Override
        public GoogleTokenExchangeResponse exchangeAuthorizationCode(String code) {
            return new GoogleTokenExchangeResponse(
                    true,
                    false,
                    null,
                    "db:GOOGLE:gmail-destination",
                    "gmail.insert gmail.labels",
                    "Bearer",
                    Instant.parse("2026-03-26T11:00:00Z"),
                    "Stored securely in the database.");
        }
    }
}
