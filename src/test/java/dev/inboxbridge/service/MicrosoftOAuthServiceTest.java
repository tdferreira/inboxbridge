package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.persistence.UserBridgeRepository;

class MicrosoftOAuthServiceTest {

    @Test
    void buildAuthorizationUrlIncludesImapScopeAndTracksState() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.config = new TestConfig(
                new TestMicrosoft("consumers", "client-id", "client-secret", "http://localhost:8080/api/microsoft-oauth/callback"),
                List.of(new TestSource(
                        "outlook-main-imap",
                        BridgeConfig.Protocol.IMAP,
                        BridgeConfig.AuthMethod.OAUTH2,
                        BridgeConfig.OAuthProvider.MICROSOFT)));
        service.objectMapper = new ObjectMapper();
        service.userBridgeRepository = new EmptyUserBridgeRepository();
        service.envSourceService = envSourceService(service.config);

        String authorizationUrl = service.buildAuthorizationUrl("outlook-main-imap");
        Map<String, String> params = queryParams(authorizationUrl);

        assertTrue(authorizationUrl.startsWith("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"));
        assertEquals("client-id", params.get("client_id"));
        assertEquals("http://localhost:8080/api/microsoft-oauth/callback", params.get("redirect_uri"));
        assertEquals("offline_access https://outlook.office.com/IMAP.AccessAsUser.All", params.get("scope"));

        MicrosoftOAuthService.CallbackValidation callbackValidation = service.validateCallback(params.get("state"));
        assertEquals("outlook-main-imap", callbackValidation.sourceId());
        assertEquals("BRIDGE_SOURCES_0__OAUTH_REFRESH_TOKEN", callbackValidation.configKey());
    }

    @Test
    void buildAuthorizationUrlUsesPopScopeForPopSources() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.config = new TestConfig(
                new TestMicrosoft("consumers", "client-id", "client-secret", "http://localhost:8080/api/microsoft-oauth/callback"),
                List.of(new TestSource(
                        "outlook-main-pop",
                        BridgeConfig.Protocol.POP3,
                        BridgeConfig.AuthMethod.OAUTH2,
                        BridgeConfig.OAuthProvider.MICROSOFT)));
        service.objectMapper = new ObjectMapper();
        service.userBridgeRepository = new EmptyUserBridgeRepository();
        service.envSourceService = envSourceService(service.config);

        String authorizationUrl = service.buildAuthorizationUrl("outlook-main-pop");

        assertEquals(
                "offline_access https://outlook.office.com/POP.AccessAsUser.All",
                queryParams(authorizationUrl).get("scope"));
    }

    @Test
    void buildAuthorizationUrlRejectsUnconfiguredClient() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.config = new TestConfig(
                new TestMicrosoft("consumers", "replace-me", "replace-me", "http://localhost:8080/api/microsoft-oauth/callback"),
                List.of(new TestSource(
                        "outlook-main-imap",
                        BridgeConfig.Protocol.IMAP,
                        BridgeConfig.AuthMethod.OAUTH2,
                        BridgeConfig.OAuthProvider.MICROSOFT)));
        service.objectMapper = new ObjectMapper();
        service.userBridgeRepository = new EmptyUserBridgeRepository();
        service.envSourceService = envSourceService(service.config);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.buildAuthorizationUrl("outlook-main-imap"));

        assertEquals("Microsoft OAuth client id is not configured", error.getMessage());
    }

    @Test
    void validateGrantedScopesRejectsMissingMailboxPermission() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.validateGrantedScopes("offline_access", BridgeConfig.Protocol.IMAP));

        assertTrue(error.getMessage().contains("did not grant all required permissions"));
        assertTrue(error.getMessage().contains("IMAP.AccessAsUser.All"));
    }

    private static Map<String, String> queryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private EnvSourceService envSourceService(BridgeConfig config) {
        EnvSourceService service = new EnvSourceService();
        service.setConfigForTest(config);
        return service;
    }

    private record TestConfig(TestMicrosoft microsoft, List<BridgeConfig.Source> sources) implements BridgeConfig {
        @Override
        public boolean pollEnabled() {
            return false;
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
        public boolean multiUserEnabled() {
            return true;
        }

        @Override
        public Security security() {
            return new Security() {
                @Override
                public Passkeys passkeys() {
                    return new Passkeys() {
                        @Override
                        public boolean enabled() {
                            return true;
                        }

                        @Override
                        public String rpId() {
                            return "localhost";
                        }

                        @Override
                        public String rpName() {
                            return "InboxBridge";
                        }

                        @Override
                        public String origins() {
                            return "https://localhost:3000";
                        }

                        @Override
                        public String challengeTtl() {
                            return "PT5M";
                        }
                    };
                }
            };
        }

        @Override
        public Gmail gmail() {
            return new Gmail() {
                @Override
                public String destinationUser() {
                    return "me";
                }

                @Override
                public String clientId() {
                    return "google-client-id";
                }

                @Override
                public String clientSecret() {
                    return "google-client-secret";
                }

                @Override
                public String refreshToken() {
                    return "google-refresh-token";
                }

                @Override
                public String redirectUri() {
                    return "http://localhost:8080/api/google-oauth/callback";
                }

                @Override
                public boolean createMissingLabels() {
                    return true;
                }

                @Override
                public boolean neverMarkSpam() {
                    return false;
                }

                @Override
                public boolean processForCalendar() {
                    return false;
                }
            };
        }
    }

    private record TestMicrosoft(String tenant, String clientId, String clientSecret, String redirectUri) implements BridgeConfig.Microsoft {
    }

    private record TestSource(
            String id,
            BridgeConfig.Protocol protocol,
            BridgeConfig.AuthMethod authMethod,
            BridgeConfig.OAuthProvider oauthProvider) implements BridgeConfig.Source {

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String host() {
            return "outlook.office365.com";
        }

        @Override
        public int port() {
            return protocol == BridgeConfig.Protocol.IMAP ? 993 : 995;
        }

        @Override
        public boolean tls() {
            return true;
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
    }

    private static class EmptyUserBridgeRepository extends UserBridgeRepository {
        @Override
        public List<UserBridge> listAll() {
            return List.of();
        }

        @Override
        public Optional<UserBridge> findByBridgeId(String bridgeId) {
            return Optional.empty();
        }
    }
}
