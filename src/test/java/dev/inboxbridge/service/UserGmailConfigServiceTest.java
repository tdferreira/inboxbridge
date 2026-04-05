package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.InboxBridgeConfig.Security.Auth;
import dev.inboxbridge.dto.UpdateUserGmailConfigRequest;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;

class UserGmailConfigServiceTest {

    @Test
    void updateAllowsNonSecretFieldsWithoutEncryption() {
        UserGmailConfigService service = service(false, repositoryWithNoRow(), Optional.empty());
        AppUser user = new AppUser();
        user.id = 7L;
        user.role = AppUser.Role.ADMIN;

        UserGmailConfigView view = service.update(user, new UpdateUserGmailConfigRequest(
                "me",
                "",
                "",
                "",
                "",
                true,
                false,
                false));

        assertEquals("me", view.destinationUser());
        assertEquals("https://mail.example.test/api/google-oauth/callback", view.redirectUri());
        assertFalse(view.clientIdConfigured());
        assertFalse(view.clientSecretConfigured());
        assertTrue(view.sharedClientConfigured());
    }

    @Test
    void updateRejectsNonAdminOverrides() {
        UserGmailConfigService service = service(false, repositoryWithNoRow(), Optional.empty());
        AppUser user = new AppUser();
        user.id = 8L;
        user.role = AppUser.Role.USER;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.update(user, new UpdateUserGmailConfigRequest(
                        "me",
                        "",
                        "",
                        "",
                        "",
                        true,
                        false,
                        false)));

        assertEquals("Only admins can override advanced Gmail account settings from the admin UI.", error.getMessage());
    }

    @Test
    void googleProfileFallsBackToSharedClientWhenUserSpecificSecretsMissing() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 9L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://custom.example.test/api/google-oauth/callback";
        repository.persist(stored);

        UserGmailConfigService service = service(false, repository, Optional.empty());

        GoogleOAuthService.GoogleOAuthProfile profile = service.googleProfileForUser(9L).orElseThrow();

        assertEquals("user-gmail:9", profile.subjectKey());
        assertEquals("shared-client-id", profile.clientId());
        assertEquals("shared-client-secret", profile.clientSecret());
        assertEquals("", profile.refreshToken());
        assertEquals("https://custom.example.test/api/google-oauth/callback", profile.redirectUri());
        assertFalse(service.destinationLinked(9L));
    }

    @Test
    void defaultViewIncludesSharedClientAndDefaultRedirectUri() {
        UserGmailConfigService service = service(false, repositoryWithNoRow(), Optional.empty());

        UserGmailConfigView view = service.defaultView(7L);

        assertTrue(view.sharedClientConfigured());
        assertFalse(view.clientIdConfigured());
        assertFalse(view.clientSecretConfigured());
        assertEquals("https://mail.example.test/api/google-oauth/callback", view.defaultRedirectUri());
        assertEquals("https://mail.example.test/api/google-oauth/callback", view.redirectUri());
    }

    @Test
    void defaultRedirectUriFallsBackToDerivedPublicHostnameAndPort() {
        UserGmailConfigService service = service(
                false,
                repositoryWithNoRow(),
                Optional.empty(),
                configWithBlankGmailRedirect(),
                Optional.empty(),
                "bridge.example.test",
                "9443");

        assertEquals("https://bridge.example.test:9443/api/google-oauth/callback", service.defaultRedirectUri());
        assertEquals("https://bridge.example.test:9443/api/google-oauth/callback", service.defaultView(7L).defaultRedirectUri());
    }

    @Test
    void defaultRedirectUriPrefersExplicitPublicBaseUrlOverride() {
        UserGmailConfigService service = service(
                false,
                repositoryWithNoRow(),
                Optional.empty(),
                configWithBlankGmailRedirect(),
                Optional.of("https://public.example.test:7443"),
                "bridge.internal.test",
                "9443");

        assertEquals("https://public.example.test:7443/api/google-oauth/callback", service.defaultRedirectUri());
    }

    @Test
    void viewReflectsStoredGoogleRefreshTokenFromOAuthCredentialTable() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 11L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://mail.example.test/api/google-oauth/callback";
        repository.persist(stored);

        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:11",
                "refresh-token-123",
                "access-token-123",
                java.time.Instant.parse("2026-03-26T12:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-26T12:00:00Z"));

        UserGmailConfigService service = service(false, repository, Optional.of(credential));

        UserGmailConfigView view = service.viewForUser(11L).orElseThrow();

        assertTrue(view.refreshTokenConfigured());
        assertFalse(view.clientIdConfigured());
        assertFalse(view.clientSecretConfigured());
        assertTrue(view.sharedClientConfigured());
    }

    @Test
    void resolveForUserFallsBackToSharedClientAndStoredOAuthCredentialWithoutConfigRow() {
        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:12",
                "refresh-token-xyz",
                "access-token-xyz",
                java.time.Instant.parse("2026-03-27T09:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-27T09:00:00Z"));

        UserGmailConfigService service = service(true, repositoryWithNoRow(), Optional.of(credential));

        UserGmailConfigService.ResolvedUserGmailConfig resolved = service.resolveForUser(12L).orElseThrow();

        assertEquals("me", resolved.destinationUser());
        assertEquals("shared-client-id", resolved.clientId());
        assertEquals("shared-client-secret", resolved.clientSecret());
        assertEquals("refresh-token-xyz", resolved.refreshToken());
        assertEquals("https://mail.example.test/api/google-oauth/callback", resolved.redirectUri());
        assertTrue(resolved.createMissingLabels());
        assertFalse(resolved.neverMarkSpam());
        assertFalse(resolved.processForCalendar());
    }

    @Test
    void unlinkForUserClearsStoredRefreshTokensAndRevokesProviderAccess() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 15L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://mail.example.test/api/google-oauth/callback";
        stored.updatedAt = java.time.Instant.parse("2026-03-27T10:00:00Z");
        repository.persist(stored);

        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:15",
                "refresh-token-xyz",
                "access-token-xyz",
                java.time.Instant.parse("2026-03-27T09:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-27T09:00:00Z"));

        FakeOAuthCredentialService credentialService = new FakeOAuthCredentialService(Optional.of(credential));
        FakeGoogleOAuthService googleOAuthService = new FakeGoogleOAuthService(true);
        UserGmailConfigService service = service(true, repository, Optional.of(credential));
        service.oAuthCredentialService = credentialService;
        service.googleOAuthService = googleOAuthService;

        UserGmailConfigService.GmailUnlinkResult result = service.unlinkForUser(15L);

        assertTrue(result.providerRevocationAttempted());
        assertTrue(result.providerRevoked());
        assertEquals("refresh-token-xyz", googleOAuthService.revokedToken);
        assertEquals("user-gmail:15", googleOAuthService.clearedSubjectKey);
        assertEquals("user-gmail:15", credentialService.deletedSubjectKey);
    }

    @Test
    void markGoogleAccessRevokedClearsStoredRefreshTokensWithoutProviderRevocation() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 21L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://mail.example.test/api/google-oauth/callback";
        stored.refreshTokenCiphertext = "stored-cipher";
        stored.refreshTokenNonce = "stored-nonce";
        repository.persist(stored);

        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:21",
                "refresh-token-xyz",
                "access-token-xyz",
                java.time.Instant.parse("2026-03-27T09:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-27T09:00:00Z"));

        FakeOAuthCredentialService credentialService = new FakeOAuthCredentialService(Optional.of(credential));
        FakeGoogleOAuthService googleOAuthService = new FakeGoogleOAuthService(true);
        UserGmailConfigService service = service(true, repository, Optional.of(credential));
        service.oAuthCredentialService = credentialService;
        service.googleOAuthService = googleOAuthService;

        boolean changed = service.markGoogleAccessRevoked(new dev.inboxbridge.domain.GmailTarget(
                "user-gmail:21",
                21L,
                "john-doe",
                "me",
                "client",
                "secret",
                "",
                "https://mail.example.test/api/google-oauth/callback",
                true,
                false,
                false));

        assertTrue(changed);
        assertEquals("user-gmail:21", credentialService.deletedSubjectKey);
        assertEquals("user-gmail:21", googleOAuthService.clearedSubjectKey);
        assertFalse(service.viewForUser(21L).orElseThrow().refreshTokenConfigured());
    }

    private UserGmailConfigService service(
            boolean encryptionConfigured,
            InMemoryUserGmailConfigRepository repository,
            Optional<OAuthCredentialService.StoredOAuthCredential> googleCredential) {
        return service(
                encryptionConfigured,
                repository,
                googleCredential,
                new TestConfig(),
                Optional.empty(),
                "localhost",
                "3000");
    }

    private UserGmailConfigService service(
            boolean encryptionConfigured,
            InMemoryUserGmailConfigRepository repository,
            Optional<OAuthCredentialService.StoredOAuthCredential> googleCredential,
            InboxBridgeConfig config,
            Optional<String> publicBaseUrl,
            String publicHostname,
            String publicPort) {
        UserGmailConfigService service = new UserGmailConfigService();
        service.repository = repository;
        service.secretEncryptionService = encryptionConfigured ? configuredSecrets() : unconfiguredSecrets();
        service.inboxBridgeConfig = config;
        service.publicBaseUrl = publicBaseUrl;
        service.publicHostname = publicHostname;
        service.publicPort = publicPort;
        service.oAuthCredentialService = new FakeOAuthCredentialService(googleCredential);
        service.systemOAuthAppSettingsService = systemOAuthAppSettingsService(service.inboxBridgeConfig, service.secretEncryptionService);
        return service;
    }

    private InboxBridgeConfig configWithBlankGmailRedirect() {
        return new TestConfig() {
            @Override
            public Gmail gmail() {
                InboxBridgeConfig.Gmail defaults = super.gmail();
                return new InboxBridgeConfig.Gmail() {
                    @Override
                    public String destinationUser() {
                        return defaults.destinationUser();
                    }

                    @Override
                    public String clientId() {
                        return defaults.clientId();
                    }

                    @Override
                    public String clientSecret() {
                        return defaults.clientSecret();
                    }

                    @Override
                    public String refreshToken() {
                        return defaults.refreshToken();
                    }

                    @Override
                    public String redirectUri() {
                        return "";
                    }

                    @Override
                    public boolean createMissingLabels() {
                        return defaults.createMissingLabels();
                    }

                    @Override
                    public boolean neverMarkSpam() {
                        return defaults.neverMarkSpam();
                    }

                    @Override
                    public boolean processForCalendar() {
                        return defaults.processForCalendar();
                    }
                };
            }
        };
    }

    private SystemOAuthAppSettingsService systemOAuthAppSettingsService(InboxBridgeConfig config, SecretEncryptionService secretEncryptionService) {
        SystemOAuthAppSettingsService service = new SystemOAuthAppSettingsService();
        service.config = config;
        service.secretEncryptionService = secretEncryptionService;
        service.repository = new dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository() {
            @Override
            public Optional<dev.inboxbridge.persistence.SystemOAuthAppSettings> findSingleton() {
                return Optional.empty();
            }

            @Override
            public void persist(dev.inboxbridge.persistence.SystemOAuthAppSettings entity) {
            }
        };
        return service;
    }

    private SecretEncryptionService unconfiguredSecrets() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = "replace-me";
        service.tokenEncryptionKeyId = "v1";
        return service;
    }

    private SecretEncryptionService configuredSecrets() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = java.util.Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        service.tokenEncryptionKeyId = "v1";
        return service;
    }

    private InMemoryUserGmailConfigRepository repositoryWithNoRow() {
        return new InMemoryUserGmailConfigRepository();
    }

    private static final class InMemoryUserGmailConfigRepository extends UserGmailConfigRepository {
        private UserGmailConfig row;

        @Override
        public Optional<UserGmailConfig> findByUserId(Long userId) {
            return row != null && row.userId.equals(userId) ? Optional.of(row) : Optional.empty();
        }

        @Override
        public void persist(UserGmailConfig entity) {
            this.row = entity;
        }
    }

    private static class TestConfig implements InboxBridgeConfig {
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
        public Duration sourceHostMinSpacing() {
            return Duration.ofSeconds(1);
        }

        @Override
        public int sourceHostMaxConcurrency() {
            return 2;
        }

        @Override
        public Duration destinationProviderMinSpacing() {
            return Duration.ofMillis(250);
        }

        @Override
        public int destinationProviderMaxConcurrency() {
            return 1;
        }

        @Override
        public Duration throttleLeaseTtl() {
            return Duration.ofMinutes(2);
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
        public Duration maxSuccessJitter() {
            return Duration.ofSeconds(30);
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
                        public Duration loginInitialBlock() {
                            return Duration.ofMinutes(5);
                        }

                        @Override
                        public Duration loginMaxBlock() {
                            return Duration.ofHours(1);
                        }

                        @Override
                        public boolean registrationChallengeEnabled() {
                            return true;
                        }

                        @Override
                        public Duration registrationChallengeTtl() {
                            return Duration.ofMinutes(10);
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
                                public Duration cacheTtl() {
                                    return Duration.ofDays(30);
                                }

                                @Override
                                public Duration providerCooldown() {
                                    return Duration.ofMinutes(5);
                                }

                                @Override
                                public Duration requestTimeout() {
                                    return Duration.ofSeconds(3);
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
            return new Gmail() {
                @Override
                public String destinationUser() {
                    return "me";
                }

                @Override
                public String clientId() {
                    return "shared-client-id";
                }

                @Override
                public String clientSecret() {
                    return "shared-client-secret";
                }

                @Override
                public String refreshToken() {
                    return "";
                }

                @Override
                public String redirectUri() {
                    return "https://mail.example.test/api/google-oauth/callback";
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

        @Override
        public Microsoft microsoft() {
            return null;
        }

        @Override
        public java.util.List<Source> sources() {
            return java.util.List.of();
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
                public Duration sessionTtl() {
                    return Duration.ofHours(12);
                }

                @Override
                public int pollRateLimitCount() {
                    return 60;
                }

                @Override
                public Duration pollRateLimitWindow() {
                    return Duration.ofMinutes(1);
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
    }

    private static final class FakeOAuthCredentialService extends OAuthCredentialService {
        private Optional<StoredOAuthCredential> googleCredential;
        private String deletedSubjectKey;

        private FakeOAuthCredentialService(Optional<StoredOAuthCredential> googleCredential) {
            this.googleCredential = googleCredential;
        }

        @Override
        public Optional<StoredOAuthCredential> findGoogleCredential(String subjectKey) {
            return googleCredential.filter(credential -> credential.subjectKey().equals(subjectKey));
        }

        @Override
        public boolean deleteGoogleCredential(String subjectKey) {
            this.deletedSubjectKey = subjectKey;
            boolean deleted = googleCredential.isPresent();
            googleCredential = Optional.empty();
            return deleted;
        }
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        private final boolean revokeResult;
        private String revokedToken;
        private String clearedSubjectKey;

        private FakeGoogleOAuthService(boolean revokeResult) {
            this.revokeResult = revokeResult;
        }

        @Override
        public boolean revokeToken(String token) {
            this.revokedToken = token;
            return revokeResult;
        }

        @Override
        public void clearCachedToken(String subjectKey) {
            this.clearedSubjectKey = subjectKey;
        }
    }
}
