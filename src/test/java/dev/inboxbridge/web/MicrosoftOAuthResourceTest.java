package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.MicrosoftOAuthService;
import dev.inboxbridge.service.UserBridgeService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

class MicrosoftOAuthResourceTest {

    @Test
    void startRedirectsBrowserToAuthorizationUrl() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();
        resource.currentUserContext = adminContext();
        resource.bridgeConfig = testConfig();
        resource.userBridgeService = new FakeUserBridgeService();

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
        assertTrue(html.contains("Return To Admin UI"));
        assertTrue(html.contains("Leave this page without exchanging the code?"));
        assertTrue(html.contains("Returning to the admin UI in"));
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
        resource.currentUserContext = adminContext();
        resource.bridgeConfig = testConfig();
        resource.userBridgeService = new FakeUserBridgeService();

        BadRequestException error = org.junit.jupiter.api.Assertions.assertThrows(
                BadRequestException.class,
                () -> resource.start("outlook-main-imap"));

        assertEquals("Microsoft OAuth client id is not configured", error.getMessage());
    }

    @Test
    void sourcesReturnsConfiguredMicrosoftOAuthSources() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();
        resource.currentUserContext = adminContext();
        resource.bridgeConfig = testConfig();
        resource.userBridgeService = new FakeUserBridgeService();

        List<MicrosoftOAuthSourceOption> sources = resource.sources();

        assertEquals(1, sources.size());
        assertEquals("outlook-main-imap", sources.getFirst().id());
        assertEquals("IMAP", sources.getFirst().protocol());
    }

    private CurrentUserContext adminContext() {
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "admin";
        user.role = AppUser.Role.ADMIN;
        context.setUser(user);
        return context;
    }

    private BridgeConfig testConfig() {
        return new BridgeConfig() {
            @Override
            public boolean pollEnabled() {
                return true;
            }

            @Override
            public String pollInterval() {
                return "5m";
            }

            @Override
            public int fetchWindow() {
                return 50;
            }

            @Override
            public Gmail gmail() {
                return null;
            }

            @Override
            public Microsoft microsoft() {
                return null;
            }

            @Override
            public List<Source> sources() {
                return List.of(new Source() {
                    @Override
                    public String id() {
                        return "outlook-main-imap";
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }

                    @Override
                    public Protocol protocol() {
                        return Protocol.IMAP;
                    }

                    @Override
                    public String host() {
                        return "outlook.office365.com";
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
                    public AuthMethod authMethod() {
                        return AuthMethod.OAUTH2;
                    }

                    @Override
                    public OAuthProvider oauthProvider() {
                        return OAuthProvider.MICROSOFT;
                    }

                    @Override
                    public String username() {
                        return "user@example.com";
                    }

                    @Override
                    public String password() {
                        return "password";
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
                    public Optional<String> customLabel() {
                        return Optional.empty();
                    }
                });
            }
        };
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

    private static class FakeUserBridgeService extends UserBridgeService {
        @Override
        public Optional<UserBridge> findByBridgeId(String bridgeId) {
            return Optional.empty();
        }
    }
}
