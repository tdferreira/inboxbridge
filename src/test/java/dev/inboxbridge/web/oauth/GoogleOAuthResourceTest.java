package dev.inboxbridge.web.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.service.oauth.GoogleOAuthService;
import jakarta.ws.rs.core.Response;

class GoogleOAuthResourceTest {

    @Test
    void startRedirectsBrowserToGoogleAuthorizationUrl() {
        GoogleOAuthResource resource = newResource(new FakeGoogleOAuthService());

        Response response = resource.startSystem(null);

        assertEquals(303, response.getStatus());
        assertEquals(URI.create("https://accounts.google.com/o/oauth2/v2/auth?client_id=demo"), response.getLocation());
    }

    @Test
    void callbackRedirectsBrowserToFrontendCallbackRoute() {
        GoogleOAuthResource resource = newResource(new FakeGoogleOAuthService());

        Response response = resource.callback("code-123", "state-1", null, null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/google/callback?lang=en&code=code-123&state=state-1"),
                response.getLocation());
    }

    @Test
    void callbackRedirectsProviderErrorsToFrontendRoute() {
        GoogleOAuthResource resource = newResource(new FakeGoogleOAuthService());

        Response response = resource.callback(null, null, "access_denied", "user denied");

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/google/callback?lang=en&error=access_denied&error_description=user+denied"),
                response.getLocation());
    }

    @Test
    void callbackRedirectsPortugueseStateToFrontendRoute() {
        GoogleOAuthResource resource = newResource(new FakeGoogleOAuthService() {
            @Override
            public CallbackValidation validateCallback(String state) {
                return new CallbackValidation(
                        "gmail-destination",
                        "Conta Gmail",
                        "https://localhost:3000/api/google-oauth/callback",
                        "pt");
            }
        });

        Response response = resource.callback("code-123", "state-1", null, null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/google/callback?lang=pt-PT&code=code-123&state=state-1"),
                response.getLocation());
    }

    private GoogleOAuthResource newResource(GoogleOAuthService oauthService) {
        GoogleOAuthResource resource = new GoogleOAuthResource();
        resource.googleOAuthService = oauthService;
        return resource;
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
                    "Stored securely in encrypted storage.");
        }

        @Override
        public boolean secureStorageConfigured() {
            return true;
        }
    }
}
