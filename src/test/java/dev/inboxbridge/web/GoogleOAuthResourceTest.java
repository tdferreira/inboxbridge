package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        Response response = resource.startSystem(null);

        assertEquals(303, response.getStatus());
        assertEquals(URI.create("https://accounts.google.com/o/oauth2/v2/auth?client_id=demo"), response.getLocation());
    }

    @Test
    void callbackRendersBrowserExchangeHtml() {
        GoogleOAuthResource resource = new GoogleOAuthResource();
        resource.googleOAuthService = new FakeGoogleOAuthService();

        String html = resource.callback("code-123", "state-1", null, null);

        assertTrue(html.contains("Google OAuth Code Received"));
        assertTrue(html.contains("Copy Code"));
        assertTrue(html.contains("Exchange Code In Browser"));
        assertTrue(html.contains("Return To Admin UI"));
        assertTrue(html.contains("Leave this page without exchanging the code?"));
        assertTrue(html.contains("Attempting automatic exchange"));
        assertTrue(html.contains("Cancel automatic return"));
        assertTrue(html.contains("window.setTimeout(() => {"));
        assertTrue(html.contains("Returning to the admin UI in"));
        assertTrue(html.contains("new URLSearchParams(window.location.search)"));
        assertTrue(html.contains("callbackParams.get('code')"));
        assertTrue(html.contains("Google OAuth is still missing one or more required permissions"));
        assertTrue(html.contains("Secure token storage is enabled"));
        assertTrue(html.contains("id=\"copyStatus\""));
        assertFalse(html.contains("PostgreSQL"));
        assertTrue(html.contains("code-123"));
    }

    @Test
    void callbackShowsConsentRetryGuidanceWhenGoogleConsentIsDenied() {
        GoogleOAuthResource resource = new GoogleOAuthResource();
        resource.googleOAuthService = new FakeGoogleOAuthService();

        String html = resource.callback(null, null, "access_denied", "user denied");

        assertTrue(html.contains("Google OAuth Permission Required"));
        assertTrue(html.contains("did not receive the required consent"));
        assertTrue(html.contains("Return To Admin UI"));
    }

    @Test
    void callbackRendersPortugueseWhenStateLanguageIsPortuguese() {
        GoogleOAuthResource resource = new GoogleOAuthResource();
        resource.googleOAuthService = new FakeGoogleOAuthService() {
            @Override
            public CallbackValidation validateCallback(String state) {
                return new CallbackValidation(
                        "gmail-destination",
                        "Conta Gmail",
                        "https://localhost:3000/api/google-oauth/callback",
                        "pt");
            }
        };

        String html = resource.callback("code-123", "state-1", null, null);

        assertTrue(html.contains("Codigo do Google OAuth recebido"));
        assertTrue(html.contains("Copiar codigo"));
        assertTrue(html.contains("Trocar codigo no browser"));
        assertTrue(html.contains("Voltar a interface de administracao"));
    }

    private static class FakeGoogleOAuthService extends GoogleOAuthService {
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
                    "https://localhost:3000/api/google-oauth/callback",
                    GoogleOAuthService.GMAIL_TARGET_SCOPE);
        }

        @Override
        public String buildAuthorizationUrlWithState(GoogleOAuthProfile profile, String targetLabel, String language) {
            return "https://accounts.google.com/o/oauth2/v2/auth?client_id=demo";
        }

        @Override
        public CallbackValidation validateCallback(String state) {
            return new CallbackValidation(
                    "gmail-destination",
                    "Shared Gmail account",
                    "https://localhost:3000/api/google-oauth/callback",
                    "en");
        }

        @Override
        public GoogleTokenExchangeResponse exchangeAuthorizationCode(String code) {
            return new GoogleTokenExchangeResponse(
                    true,
                    false,
                    false,
                    true,
                    true,
                    null,
                    "db:GOOGLE:gmail-destination",
                    "gmail.insert gmail.labels",
                    "Bearer",
                    Instant.parse("2026-03-26T11:00:00Z"),
                    "Stored securely in the database.");
        }

        @Override
        public boolean secureStorageConfigured() {
            return true;
        }
    }
}
