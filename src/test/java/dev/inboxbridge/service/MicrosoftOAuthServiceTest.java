package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;

class MicrosoftOAuthServiceTest {

    @Test
    void buildAuthorizationUrlIncludesImapScopeAndTracksState() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.config = new TestConfig(
                new TestMicrosoft("consumers", "client-id", "client-secret", "http://localhost:8080/api/microsoft-oauth/callback"),
                List.of(new TestSource(
                        "outlook-main-imap",
                        InboxBridgeConfig.Protocol.IMAP,
                        InboxBridgeConfig.AuthMethod.OAUTH2,
                        InboxBridgeConfig.OAuthProvider.MICROSOFT)));
        service.objectMapper = new ObjectMapper();
        service.userEmailAccountRepository = new EmptyUserEmailAccountRepository();
        service.envSourceService = envSourceService(service.config);
                service.systemOAuthAppSettingsService = systemOAuthAppSettingsService(service.config);
                service.oAuthCredentialService = oauthCredentialService();

        String authorizationUrl = service.buildAuthorizationUrl("outlook-main-imap");
        Map<String, String> params = queryParams(authorizationUrl);

        assertTrue(authorizationUrl.startsWith("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"));
        assertEquals("client-id", params.get("client_id"));
        assertEquals("http://localhost:8080/api/microsoft-oauth/callback", params.get("redirect_uri"));
        assertEquals("offline_access https://outlook.office.com/IMAP.AccessAsUser.All", params.get("scope"));

        MicrosoftOAuthService.CallbackValidation callbackValidation = service.validateCallback(params.get("state"));
        assertEquals("outlook-main-imap", callbackValidation.sourceId());
        assertEquals("MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN", callbackValidation.configKey());
    }

    @Test
    void buildAuthorizationUrlUsesPopScopeForPopSources() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.config = new TestConfig(
                new TestMicrosoft("consumers", "client-id", "client-secret", "http://localhost:8080/api/microsoft-oauth/callback"),
                List.of(new TestSource(
                        "outlook-main-pop",
                        InboxBridgeConfig.Protocol.POP3,
                        InboxBridgeConfig.AuthMethod.OAUTH2,
                        InboxBridgeConfig.OAuthProvider.MICROSOFT)));
        service.objectMapper = new ObjectMapper();
        service.userEmailAccountRepository = new EmptyUserEmailAccountRepository();
        service.envSourceService = envSourceService(service.config);
                service.systemOAuthAppSettingsService = systemOAuthAppSettingsService(service.config);
                service.oAuthCredentialService = oauthCredentialService();

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
                        InboxBridgeConfig.Protocol.IMAP,
                        InboxBridgeConfig.AuthMethod.OAUTH2,
                        InboxBridgeConfig.OAuthProvider.MICROSOFT)));
        service.objectMapper = new ObjectMapper();
        service.userEmailAccountRepository = new EmptyUserEmailAccountRepository();
        service.envSourceService = envSourceService(service.config);
                service.systemOAuthAppSettingsService = systemOAuthAppSettingsService(service.config);
                service.oAuthCredentialService = oauthCredentialService();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.buildAuthorizationUrl("outlook-main-imap"));

        assertEquals("Microsoft OAuth client id is not configured", error.getMessage());
    }

    @Test
    void validateGrantedScopesRejectsMissingMailboxPermission() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.validateGrantedScopes("offline_access", InboxBridgeConfig.Protocol.IMAP));

        assertTrue(error.getMessage().contains("did not grant all required permissions"));
        assertTrue(error.getMessage().contains("IMAP.AccessAsUser.All"));
    }

    @Test
    void validateGrantedScopesAllowsMicrosoftResponsesThatOmitOfflineAccessFromScopeEcho() {
        MicrosoftOAuthService service = new MicrosoftOAuthService();

        service.validateGrantedScopes("https://outlook.office.com/IMAP.AccessAsUser.All", InboxBridgeConfig.Protocol.IMAP);
    }

    @Test
    void invalidateCachedTokenRemovesExistingCacheEntry() throws Exception {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.config = new TestConfig(
                new TestMicrosoft("consumers", "client-id", "client-secret", "http://localhost:8080/api/microsoft-oauth/callback"),
                List.of(new TestSource(
                        "outlook-main-imap",
                        InboxBridgeConfig.Protocol.IMAP,
                        InboxBridgeConfig.AuthMethod.OAUTH2,
                        InboxBridgeConfig.OAuthProvider.MICROSOFT)));
        service.oAuthCredentialService = oauthCredentialService();

        java.lang.reflect.Field cacheField = MicrosoftOAuthService.class.getDeclaredField("cachedTokens");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, Object> cache =
                (java.util.concurrent.ConcurrentMap<String, Object>) cacheField.get(service);
        java.lang.reflect.Constructor<?> constructor = Class
                .forName("dev.inboxbridge.service.MicrosoftOAuthService$CachedToken")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        cache.put("outlook-main-imap", constructor.newInstance("token", java.time.Instant.now().plusSeconds(300)));

        service.invalidateCachedToken("outlook-main-imap");

        assertTrue(cache.isEmpty());
    }

    @Test
    void preferredMailboxUsernameReadsPreferredUsernameClaimFromAccessToken() throws Exception {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.objectMapper = new ObjectMapper();
        String accessToken = jwt(Map.of("preferred_username", "owner@example.com"));

        java.lang.reflect.Method method = MicrosoftOAuthService.class.getDeclaredMethod("preferredMailboxUsername", String.class);
        method.setAccessible(true);

        assertEquals("owner@example.com", method.invoke(service, accessToken));
    }

    @Test
    void preferredMailboxUsernameReturnsNullForOpaqueTokens() throws Exception {
        MicrosoftOAuthService service = new MicrosoftOAuthService();
        service.objectMapper = new ObjectMapper();

        java.lang.reflect.Method method = MicrosoftOAuthService.class.getDeclaredMethod("preferredMailboxUsername", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(service, "opaque-token"));
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

    private static String jwt(Map<String, String> claims) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = new ObjectMapper().writeValueAsString(claims);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return header + "." + encodedPayload + ".signature";
    }

    private EnvSourceService envSourceService(InboxBridgeConfig config) {
        EnvSourceService service = new EnvSourceService();
        service.setConfigForTest(config);
        return service;
    }

    private SystemOAuthAppSettingsService systemOAuthAppSettingsService(InboxBridgeConfig config) {
        InboxBridgeConfig bridgeConfig = config;
        return new SystemOAuthAppSettingsService() {
            @Override
            public String microsoftClientId() {
                return bridgeConfig.microsoft().clientId();
            }

            @Override
            public String microsoftClientSecret() {
                return bridgeConfig.microsoft().clientSecret();
            }

            @Override
            public boolean microsoftClientConfigured() {
                return !"replace-me".equals(bridgeConfig.microsoft().clientId())
                        && !"replace-me".equals(bridgeConfig.microsoft().clientSecret());
            }
        };
    }

    private OAuthCredentialService oauthCredentialService() {
        return new OAuthCredentialService() {
            @Override
            public boolean secureStorageConfigured() {
                return false;
            }
        };
    }

    private record TestConfig(TestMicrosoft microsoft, List<InboxBridgeConfig.Source> sources) implements InboxBridgeConfig {
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
        public java.time.Duration sourceHostMinSpacing() {
            return java.time.Duration.ofSeconds(1);
        }

        @Override
        public java.time.Duration destinationProviderMinSpacing() {
            return java.time.Duration.ofMillis(250);
        }

        @Override
        public double successJitterRatio() {
            return 0.2d;
        }

        @Override
        public java.time.Duration maxSuccessJitter() {
            return java.time.Duration.ofSeconds(30);
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

    private record TestMicrosoft(String tenant, String clientId, String clientSecret, String redirectUri) implements InboxBridgeConfig.Microsoft {
    }

    private record TestSource(
            String id,
            InboxBridgeConfig.Protocol protocol,
            InboxBridgeConfig.AuthMethod authMethod,
            InboxBridgeConfig.OAuthProvider oauthProvider) implements InboxBridgeConfig.Source {

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
            return protocol == InboxBridgeConfig.Protocol.IMAP ? 993 : 995;
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

    private static class EmptyUserEmailAccountRepository extends UserEmailAccountRepository {
        @Override
        public List<UserEmailAccount> listAll() {
            return List.of();
        }

        @Override
        public Optional<UserEmailAccount> findByEmailAccountId(String emailAccountId) {
            return Optional.empty();
        }
    }
}
