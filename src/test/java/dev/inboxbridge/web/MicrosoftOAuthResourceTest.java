package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.InboxBridgeConfig.Security.Auth;
import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.EnvSourceService;
import dev.inboxbridge.service.MicrosoftOAuthService;
import dev.inboxbridge.service.UserEmailAccountService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

class MicrosoftOAuthResourceTest {

    @Test
    void startRedirectsBrowserToAuthorizationUrl() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();
        resource.currentUserContext = adminContext();
        resource.envSourceService = envSourceService();
        resource.userEmailAccountService = new FakeUserEmailAccountService();

        Response response = resource.start("outlook-main-imap", null);

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
        assertTrue(html.contains("abc123"));
        assertTrue(html.contains("Exchange Code In Browser"));
        assertTrue(html.contains("Cancel automatic return"));
        assertTrue(html.contains("Return To Admin UI"));
        assertTrue(html.contains("Leave this page without exchanging the code?"));
        assertTrue(html.contains("Attempting automatic exchange"));
        assertTrue(html.contains("const callbackParams = new URLSearchParams(window.location.search);"));
        assertTrue(html.contains("const oauthCode = callbackParams.get(\"code\") || serverRenderedCode;"));
        assertTrue(html.contains("const oauthState = callbackParams.get(\"state\") || serverRenderedState;"));
        assertTrue(html.contains("window.prompt(manualCopyPrompt, text);"));
        assertTrue(html.contains("Copy the authorization code manually and press Cmd+C, then Enter."));
        assertTrue(html.contains("let allowLeave = false;"));
        assertTrue(html.contains("function clearAutoReturn()"));
        assertTrue(html.contains("function cancelAutoReturn()"));
        assertTrue(html.contains("cancelReturnButton?.addEventListener(\"click\""));
        assertTrue(html.contains("Automatic return canceled. You can stay on this page and inspect the exchange details."));
        assertTrue(html.contains("if (exchanged || allowLeave) {"));
        assertTrue(html.contains("allowLeave = true;"));
        assertTrue(html.contains("window.setTimeout(() => {"));
        assertTrue(html.contains("Returning to the admin UI in"));
        assertTrue(html.contains("Secure token storage is enabled."));
        assertTrue(html.contains("id=\"copyStatus\""));
        assertFalse(html.contains("Env Refresh Token Key"));
        assertFalse(html.contains("MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN"));
        assertFalse(html.contains("PostgreSQL"));
        assertTrue(html.contains("Microsoft OAuth is still missing one or more required permissions"));
    }

    @Test
    void callbackShowsEnvRefreshTokenKeyWhenSecureStorageIsDisabled() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public boolean secureStorageConfigured() {
                return false;
            }
        };

        Response response = resource.callback("abc123", "state-1", null, null);
        String html = (String) response.getEntity();

        assertTrue(html.contains("MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN"));
        assertTrue(html.contains("Env Refresh Token Key"));
        assertTrue(html.contains("Secure token storage is not configured."));
    }

    @Test
    void callbackShowsErrorPageForInvalidState() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public BrowserCallbackValidation validateBrowserCallback(String state) {
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
    void callbackShowsConsentRetryGuidanceWhenMicrosoftConsentIsDenied() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();

        Response response = resource.callback(null, null, "access_denied", "user denied");
        String html = (String) response.getEntity();

        assertEquals(200, response.getStatus());
        assertTrue(html.contains("Microsoft OAuth Permission Required"));
        assertTrue(html.contains("required consent"));
        assertTrue(html.contains("Return To Admin UI"));
    }

    @Test
    void callbackRendersPortugueseWhenStateLanguageIsPortuguese() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public BrowserCallbackValidation validateBrowserCallback(String state) {
                return new BrowserCallbackValidation("outlook-main-imap", "MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN", "pt", "Mail account outlook-main-imap");
            }
        };

        Response response = resource.callback("abc123", "state-1", null, null);
        String html = (String) response.getEntity();

        assertTrue(html.contains("Codigo do Microsoft OAuth recebido"));
        assertTrue(html.contains("Trocar codigo no browser"));
        assertTrue(html.contains("Voltar a interface de administracao"));
    }

    @Test
    void callbackHidesEnvRefreshTokenKeyForUserManagedSources() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public BrowserCallbackValidation validateBrowserCallback(String state) {
                return new BrowserCallbackValidation("outlook-user", "", "en", "Mail account outlook-user");
            }
        };

        Response response = resource.callback("abc123", "state-1", null, null);
        String html = (String) response.getEntity();

        assertTrue(html.contains("outlook-user"));
        assertTrue(html.contains("Authorization Code"));
        assertTrue(html.contains("Exchange Endpoint"));
        assertFalse(html.contains("Config Key"));
        assertFalse(html.contains("Env Refresh Token Key"));
    }

    @Test
    void startMapsConfigurationErrorsToBadRequest() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public String buildAuthorizationUrl(String sourceId, String language) {
                throw new IllegalStateException("Microsoft OAuth client id is not configured");
            }
        };
        resource.currentUserContext = adminContext();
        resource.envSourceService = envSourceService();
        resource.userEmailAccountService = new FakeUserEmailAccountService();

        BadRequestException error = org.junit.jupiter.api.Assertions.assertThrows(
                BadRequestException.class,
                () -> resource.start("outlook-main-imap", null));

        assertEquals("Microsoft OAuth client id is not configured", error.getMessage());
    }

    @Test
    void sourcesReturnsConfiguredMicrosoftOAuthSources() {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService();
        resource.currentUserContext = adminContext();
        resource.envSourceService = envSourceService();
        resource.userEmailAccountService = new FakeUserEmailAccountService();

        List<MicrosoftOAuthSourceOption> sources = resource.sources();

        assertEquals(1, sources.size());
        assertEquals("outlook-main-imap", sources.getFirst().id());
        assertEquals("IMAP", sources.getFirst().protocol());
    }

    @Test
    void exchangeReturnsJsonErrorBodyForBrowserCallbackFailures() throws Exception {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService() {
            @Override
            public dev.inboxbridge.dto.MicrosoftTokenExchangeResponse exchangeAuthorizationCodeByState(String state, String code) {
                throw new IllegalStateException("Invalid or expired OAuth state");
            }
        };

        Response response = resource.exchange(new dev.inboxbridge.dto.MicrosoftOAuthCodeRequest(null, "code-1", "state-1"));

        assertEquals(400, response.getStatus());
        ApiError payload = (ApiError) response.getEntity();
        assertEquals("oauth_state_invalid_or_expired", payload.code());
        assertEquals("Invalid or expired OAuth state", payload.message());
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

    private InboxBridgeConfig testConfig() {
        return new InboxBridgeConfig() {
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
            public java.time.Duration sourceHostMinSpacing() {
                return java.time.Duration.ofSeconds(1);
            }

            @Override
            public int sourceHostMaxConcurrency() {
                return 2;
            }

            @Override
            public java.time.Duration destinationProviderMinSpacing() {
                return java.time.Duration.ofMillis(250);
            }

            @Override
            public int destinationProviderMaxConcurrency() {
                return 1;
            }

            @Override
            public java.time.Duration throttleLeaseTtl() {
                return java.time.Duration.ofMinutes(2);
            }

            @Override
            public int adaptiveThrottleMaxMultiplier() {
                return 6;
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
                    public Auth auth() {
                        return new Auth() {
                            @Override
                            public int loginFailureThreshold() {
                                return 5;
                            }

                            @Override
                            public java.time.Duration loginInitialBlock() {
                                return java.time.Duration.ofMinutes(5);
                            }

                            @Override
                            public java.time.Duration loginMaxBlock() {
                                return java.time.Duration.ofHours(1);
                            }

                            @Override
                            public boolean registrationChallengeEnabled() {
                                return true;
                            }

                            @Override
                            public java.time.Duration registrationChallengeTtl() {
                                return java.time.Duration.ofMinutes(10);
                            }

                            @Override
                            public String registrationChallengeProvider() {
                                return "ALTCHA";
                            }

                            @Override
                            public RegistrationCaptcha registrationCaptcha() {
                                return captchaDefaults();
                            }

                            @Override
                            public GeoIp geoIp() {
                                return new GeoIp() {
                                    @Override
                                    public boolean enabled() {
                                        return false;
                                    }

                                    @Override
                                    public String primaryProvider() {
                                        return "IPWHOIS";
                                    }

                                    @Override
                                    public String fallbackProviders() {
                                        return "IPINFO_LITE";
                                    }

                                    @Override
                                    public java.time.Duration cacheTtl() {
                                        return java.time.Duration.ofDays(30);
                                    }

                                    @Override
                                    public java.time.Duration providerCooldown() {
                                        return java.time.Duration.ofMinutes(5);
                                    }

                                    @Override
                                    public java.time.Duration requestTimeout() {
                                        return java.time.Duration.ofSeconds(3);
                                    }

                                    @Override
                                    public java.util.Optional<String> ipinfoToken() {
                                        return java.util.Optional.empty();
                                    }
                                };
                            }
                        };
                    }

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

                    @Override
                    public Remote remote() {
                        return remoteDefaults();
                    }
                };
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

            private Auth.RegistrationCaptcha captchaDefaults() {
                return new Auth.RegistrationCaptcha() {
                    @Override
                    public Auth.RegistrationCaptcha.Altcha altcha() {
                        return new Auth.RegistrationCaptcha.Altcha() {
                            @Override
                            public long maxNumber() {
                                return 100000L;
                            }

                            @Override
                            public java.util.Optional<String> hmacKey() {
                                return java.util.Optional.empty();
                            }
                        };
                    }

                    @Override
                    public Auth.RegistrationCaptcha.Turnstile turnstile() {
                        return new Auth.RegistrationCaptcha.Turnstile() {
                            @Override
                            public java.util.Optional<String> siteKey() {
                                return java.util.Optional.empty();
                            }

                            @Override
                            public java.util.Optional<String> secret() {
                                return java.util.Optional.empty();
                            }
                        };
                    }

                    @Override
                    public Auth.RegistrationCaptcha.Hcaptcha hcaptcha() {
                        return new Auth.RegistrationCaptcha.Hcaptcha() {
                            @Override
                            public java.util.Optional<String> siteKey() {
                                return java.util.Optional.empty();
                            }

                            @Override
                            public java.util.Optional<String> secret() {
                                return java.util.Optional.empty();
                            }
                        };
                    }
                };
            }

            private InboxBridgeConfig.Security.Remote remoteDefaults() {
                return new InboxBridgeConfig.Security.Remote() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }

                    @Override
                    public java.time.Duration sessionTtl() {
                        return java.time.Duration.ofHours(12);
                    }

                    @Override
                    public int pollRateLimitCount() {
                        return 60;
                    }

                    @Override
                    public java.time.Duration pollRateLimitWindow() {
                        return java.time.Duration.ofMinutes(1);
                    }

                    @Override
                    public java.util.Optional<String> serviceToken() {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public java.util.Optional<String> serviceUsername() {
                        return java.util.Optional.empty();
                    }
                };
            }
        };
    }

    private EnvSourceService envSourceService() {
        EnvSourceService service = new EnvSourceService();
        service.setConfigForTest(testConfig());
        return service;
    }

    private static class FakeMicrosoftOAuthService extends MicrosoftOAuthService {
        @Override
        public String buildAuthorizationUrl(String sourceId, String language) {
            return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=demo";
        }

        @Override
        public BrowserCallbackValidation validateBrowserCallback(String state) {
            return new BrowserCallbackValidation("outlook-main-imap", "MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN", "en", "Mail account outlook-main-imap");
        }

        @Override
        public List<MicrosoftOAuthSourceOption> listMicrosoftOAuthSources() {
            return List.of(new MicrosoftOAuthSourceOption("outlook-main-imap", "IMAP", true));
        }

        @Override
        public boolean secureStorageConfigured() {
            return true;
        }
    }

    private static class FakeUserEmailAccountService extends UserEmailAccountService {
        @Override
        public Optional<UserEmailAccount> findByEmailAccountId(String emailAccountId) {
            return Optional.empty();
        }
    }
}
