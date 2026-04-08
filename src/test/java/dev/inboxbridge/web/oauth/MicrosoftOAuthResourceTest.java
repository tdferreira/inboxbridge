package dev.inboxbridge.web.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.InboxBridgeConfig.Security.Auth;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.mail.EnvSourceService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.user.UserEmailAccountService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

class MicrosoftOAuthResourceTest {

    @Test
    void startRedirectsBrowserToAuthorizationUrl() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService());

        Response response = resource.start("outlook-main-imap", null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=demo"),
                response.getLocation());
    }

    @Test
    void callbackRedirectsSuccessfulBrowserFlowToFrontendRoute() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService());

        Response response = resource.callback("abc123", "state-1", null, null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/microsoft/callback?lang=en&code=abc123&state=state-1"),
                response.getLocation());
    }

    @Test
    void callbackPreservesRedirectEvenWhenSecureStorageIsDisabled() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService() {
            @Override
            public boolean secureStorageConfigured() {
                return false;
            }
        });

        Response response = resource.callback("abc123", "state-1", null, null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/microsoft/callback?lang=en&code=abc123&state=state-1"),
                response.getLocation());
    }

    @Test
    void callbackRedirectsInvalidStateToFrontendErrorRoute() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService() {
            @Override
            public BrowserCallbackValidation validateBrowserCallback(String state) {
                throw new IllegalArgumentException("Invalid or expired OAuth state");
            }
        });

        Response response = resource.callback("abc123", "bad-state", null, null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/microsoft/callback?lang=en&error=invalid_state&error_description=The+callback+state+was+missing+or+expired.+Start+the+Microsoft+OAuth+flow+again+from+InboxBridge."),
                response.getLocation());
    }

    @Test
    void callbackRedirectsConsentErrorsToFrontendRoute() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService());

        Response response = resource.callback(null, null, "access_denied", "user denied");

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/microsoft/callback?lang=en&error=access_denied&error_description=user+denied"),
                response.getLocation());
    }

    @Test
    void callbackRedirectsPortugueseWhenStateLanguageIsPortuguese() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService() {
            @Override
            public BrowserCallbackValidation validateBrowserCallback(String state) {
                return new BrowserCallbackValidation("outlook-main-imap", "MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN", "pt", "Mail account outlook-main-imap");
            }
        });

        Response response = resource.callback("abc123", "state-1", null, null);

        assertEquals(
                URI.create("/oauth/microsoft/callback?lang=pt-PT&code=abc123&state=state-1"),
                response.getLocation());
    }

    @Test
    void callbackRedirectsUserManagedSourcesToFrontendRoute() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService() {
            @Override
            public BrowserCallbackValidation validateBrowserCallback(String state) {
                return new BrowserCallbackValidation("outlook-user", "", "en", "Mail account outlook-user");
            }
        });

        Response response = resource.callback("abc123", "state-1", null, null);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/oauth/microsoft/callback?lang=en&code=abc123&state=state-1"),
                response.getLocation());
    }

    @Test
    void startMapsConfigurationErrorsToBadRequest() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService() {
            @Override
            public String buildAuthorizationUrl(String sourceId, String language) {
                throw new IllegalStateException("Microsoft OAuth client id is not configured");
            }
        });

        BadRequestException error = org.junit.jupiter.api.Assertions.assertThrows(
                BadRequestException.class,
                () -> resource.start("outlook-main-imap", null));

        assertEquals("Microsoft OAuth client id is not configured", error.getMessage());
    }

    @Test
    void sourcesReturnsConfiguredMicrosoftOAuthSources() {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService());

        List<MicrosoftOAuthSourceOption> sources = resource.sources();

        assertEquals(1, sources.size());
        assertEquals("outlook-main-imap", sources.getFirst().id());
        assertEquals("IMAP", sources.getFirst().protocol());
    }

    @Test
    void exchangeReturnsJsonErrorBodyForBrowserCallbackFailures() throws Exception {
        MicrosoftOAuthResource resource = newResource(new FakeMicrosoftOAuthService() {
            @Override
            public dev.inboxbridge.dto.MicrosoftTokenExchangeResponse exchangeAuthorizationCodeByState(String state, String code) {
                throw new IllegalStateException("Invalid or expired OAuth state");
            }
        });

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

    private MicrosoftOAuthResource newResource(MicrosoftOAuthService oauthService) {
        MicrosoftOAuthResource resource = new MicrosoftOAuthResource();
        resource.microsoftOAuthService = oauthService;
        resource.currentUserContext = adminContext();
        resource.envSourceService = envSourceService();
        resource.userEmailAccountService = new FakeUserEmailAccountService();
        return resource;
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
                    public SourceFetchMode fetchMode() {
                        return SourceFetchMode.POLLING;
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
