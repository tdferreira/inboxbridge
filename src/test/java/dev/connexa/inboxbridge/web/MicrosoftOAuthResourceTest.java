package dev.connexa.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.connexa.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.connexa.inboxbridge.service.MicrosoftOAuthService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

class MicrosoftOAuthResourceTest {

    @Test
    void startRedirectsBrowserToAuthorizationUrl() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();

        Response response = resource.start("outlook-main-imap");

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=demo"),
                response.getLocation());
    }

    @Test
    void callbackRendersHelpfulHtmlForSuccessfulBrowserFlow() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();

        Response response = resource.callback("abc123", "state-1", null, null);
        String html = (String) response.getEntity();

        assertEquals(200, response.getStatus());
        assertTrue(html.contains("Microsoft OAuth Code Received"));
        assertTrue(html.contains("outlook-main-imap"));
        assertTrue(html.contains("BRIDGE_SOURCES_0__OAUTH_REFRESH_TOKEN"));
        assertTrue(html.contains("abc123"));
        assertTrue(html.contains("Exchange Code In Browser"));
        assertTrue(html.contains("encrypted in PostgreSQL"));
    }

    @Test
    void callbackShowsErrorPageForInvalidState() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public CallbackValidation validateCallback(String state) {
                throw new IllegalArgumentException("Invalid or expired OAuth state");
            }
        };

        Response response = resource.callback("abc123", "bad-state", null, null);
        String html = (String) response.getEntity();

        assertEquals(200, response.getStatus());
        assertTrue(html.contains("Invalid OAuth State"));
        assertTrue(html.contains("Invalid or expired OAuth state"));
    }

    @Test
    void startMapsConfigurationErrorsToBadRequest() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public String buildAuthorizationUrl(String sourceId) {
                throw new IllegalStateException("Microsoft OAuth client id is not configured");
            }
        };

        BadRequestException error = org.junit.jupiter.api.Assertions.assertThrows(
                BadRequestException.class,
                () -> resource.start("outlook-main-imap"));

        assertEquals("Microsoft OAuth client id is not configured", error.getMessage());
    }

    @Test
    void sourcesReturnsConfiguredMicrosoftOAuthSources() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();

        List<MicrosoftOAuthSourceOption> sources = resource.sources();

        assertEquals(1, sources.size());
        assertEquals("outlook-main-imap", sources.getFirst().id());
        assertEquals("IMAP", sources.getFirst().protocol());
    }

    private static class FakeMicrosoftOAuthService extends MicrosoftOAuthService {
        @Override
        public String buildAuthorizationUrl(String sourceId) {
            return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=demo";
        }

        @Override
        public CallbackValidation validateCallback(String state) {
            return new CallbackValidation("outlook-main-imap", "BRIDGE_SOURCES_0__OAUTH_REFRESH_TOKEN");
        }

        @Override
        public List<MicrosoftOAuthSourceOption> listMicrosoftOAuthSources() {
            return List.of(new MicrosoftOAuthSourceOption("outlook-main-imap", "IMAP", true));
        }
    }
}
