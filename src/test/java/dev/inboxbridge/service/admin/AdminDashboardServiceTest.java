package dev.inboxbridge.service.admin;

import dev.inboxbridge.service.polling.PollingStatsService;
import dev.inboxbridge.service.polling.PollingTimelineService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.SourceDiagnosticsView;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.SourcePollingStateView;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.SourcePollEvent;
import dev.inboxbridge.service.EnvSourceService;
import dev.inboxbridge.service.SecretEncryptionService;
import dev.inboxbridge.service.SourceDiagnosticsService;
import dev.inboxbridge.service.oauth.OAuthCredentialService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.polling.PollingSettingsService;
import dev.inboxbridge.service.polling.SourcePollEventService;
import dev.inboxbridge.service.polling.SourcePollingSettingsService;
import dev.inboxbridge.service.polling.SourcePollingStateService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;

class AdminDashboardServiceTest {

    @Test
    void dashboardIncludesEnvDefinedBridgesAndRecentEvents() {
        AdminDashboardService service = new AdminDashboardService();
        service.config = new TestConfig();
        service.importedMessageRepository = new FakeImportedMessageRepository();
        service.oAuthCredentialService = new FakeOAuthCredentialService();
        service.sourcePollEventService = new FakeSourcePollEventService();
        service.pollingSettingsService = new FakePollingSettingsService();
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.envSourceService = envSourceService(service.config);
        service.sourcePollingStateService = new FakeSourcePollingStateService();
        service.runtimeEmailAccountService = runtimeEmailAccountService(service.config);
        service.sourceDiagnosticsService = new FakeSourceDiagnosticsService();
        service.pollingStatsService = pollingStatsService(service.importedMessageRepository, service.sourcePollEventService, service.envSourceService);
        service.systemOAuthAppSettingsService = systemOAuthAppSettingsService(service.config);

        AdminDashboardResponse response = service.dashboard();

        assertEquals(2, response.overall().configuredSources());
        assertEquals(1, response.overall().enabledSources());
        assertEquals(4L, response.overall().totalImportedMessages());
        assertEquals(1, response.overall().sourcesWithErrors());
        assertEquals("3m", response.overall().pollInterval());
        assertEquals(25, response.overall().fetchWindow());
        assertFalse(response.polling().defaultPollEnabled());
        assertEquals("3m", response.polling().effectivePollInterval());
        assertEquals(2, response.stats().configuredMailFetchers());
        assertEquals(1, response.stats().enabledMailFetchers());
        assertEquals(4L, response.stats().totalImportedMessages());
        assertEquals(1, response.stats().health().failingMailFetchers());
        assertEquals(1L, response.stats().scheduledRuns());

        assertEquals("DATABASE", response.destination().tokenStorageMode());
        assertEquals(2, response.emailAccounts().size());
        assertEquals("outlook-main-imap", response.emailAccounts().getFirst().id());
        assertEquals(4L, response.emailAccounts().getFirst().totalImportedMessages());
        assertEquals("DATABASE", response.emailAccounts().getFirst().tokenStorageMode());
        assertNotNull(response.emailAccounts().getFirst().lastEvent());
        assertEquals("ERROR", response.emailAccounts().getFirst().lastEvent().status());
        assertEquals("3m", response.emailAccounts().getFirst().effectivePollInterval());
        assertEquals(25, response.emailAccounts().getFirst().effectiveFetchWindow());
        assertNotNull(response.emailAccounts().getFirst().pollingState());
        assertNotNull(response.emailAccounts().getFirst().diagnostics());

        assertEquals(1, response.recentEvents().size());
        assertEquals("outlook-main-imap", response.recentEvents().getFirst().sourceId());
    }

    @Test
    void dashboardHidesStaleNoRefreshTokenErrorWhenNewerMicrosoftCredentialExists() {
        AdminDashboardService service = new AdminDashboardService();
        service.config = new TestConfig();
        service.importedMessageRepository = new FakeImportedMessageRepository();
        service.oAuthCredentialService = new OAuthCredentialService() {
            @Override
            public boolean secureStorageConfigured() {
                return true;
            }

            @Override
            public Optional<StoredOAuthCredential> findGoogleCredential() {
                return Optional.of(new StoredOAuthCredential(
                        GOOGLE_PROVIDER,
                        "gmail-destination",
                        "refresh",
                        "access",
                        Instant.parse("2026-03-26T11:00:00Z"),
                        "gmail.insert",
                        "Bearer",
                        Instant.parse("2026-03-26T10:00:00Z")));
            }

            @Override
            public Optional<StoredOAuthCredential> findMicrosoftCredential(String sourceId) {
                if ("outlook-main-imap".equals(sourceId)) {
                    return Optional.of(new StoredOAuthCredential(
                            MICROSOFT_PROVIDER,
                            sourceId,
                            "refresh",
                            "access",
                            Instant.parse("2026-03-27T11:00:00Z"),
                            "imap",
                            "Bearer",
                            Instant.parse("2026-03-27T10:00:00Z")));
                }
                return Optional.empty();
            }
        };
        service.sourcePollEventService = new SourcePollEventService() {
            @Override
            public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
                if (!"outlook-main-imap".equals(sourceId)) {
                    return Optional.empty();
                }
                return Optional.of(new AdminPollEventSummary(
                        sourceId,
                        "scheduler",
                        "ERROR",
                        Instant.parse("2026-03-26T10:02:00Z"),
                        Instant.parse("2026-03-26T10:02:05Z"),
                        0,
                        0,
                        0L,
                        0,
                        0,
                        null,
                        null,
                        "Source outlook-main-imap failed: Source outlook-main-imap is configured for OAuth2 but has no refresh token",
                        null, null, null, null, null, null, null, null, null));
            }

            @Override
            public List<AdminPollEventSummary> recentEvents(int limit) {
                return List.of();
            }

            @Override
            public List<SourcePollEvent> listSince(Instant since) {
                return List.of();
            }
        };
        service.pollingSettingsService = new FakePollingSettingsService();
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.envSourceService = envSourceService(service.config);
        service.sourcePollingStateService = new FakeSourcePollingStateService();
        service.runtimeEmailAccountService = runtimeEmailAccountService(service.config);
        service.sourceDiagnosticsService = new FakeSourceDiagnosticsService();
        service.pollingStatsService = pollingStatsService(service.importedMessageRepository, service.sourcePollEventService, service.envSourceService);
        service.systemOAuthAppSettingsService = systemOAuthAppSettingsService(service.config);

        AdminDashboardResponse response = service.dashboard();

        assertEquals("DATABASE", response.emailAccounts().getFirst().tokenStorageMode());
        assertNull(response.emailAccounts().getFirst().lastEvent());
        assertEquals(0, response.overall().sourcesWithErrors());
    }

    private static final class FakeSourcePollingStateService extends SourcePollingStateService {
        @Override
        public java.util.Map<String, SourcePollingStateView> viewBySourceIds(java.util.List<String> sourceIds) {
            return java.util.Map.of(
                    "outlook-main-imap",
                    new SourcePollingStateView(
                            Instant.parse("2026-03-26T10:10:00Z"),
                            Instant.parse("2026-03-26T10:10:00Z"),
                            1,
                            "Source outlook-main-imap failed: AUTHENTICATE failed",
                            Instant.parse("2026-03-26T10:02:05Z"),
                            null));
        }
    }

    private static final class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public EffectivePollingSettings effectiveSettings() {
            return new EffectivePollingSettings(true, "3m", java.time.Duration.ofMinutes(3), 25);
        }

        @Override
        public dev.inboxbridge.dto.AdminPollingSettingsView view() {
            return new dev.inboxbridge.dto.AdminPollingSettingsView(
                    false,
                    Boolean.TRUE,
                    true,
                    "5m",
                    "3m",
                    "3m",
                    50,
                    Integer.valueOf(25),
                    25,
                    5,
                    null,
                    5,
                    60,
                    null,
                    60,
                    "PT1S",
                    null,
                    "PT1S",
                    2,
                    null,
                    2,
                    "PT0.25S",
                    null,
                    "PT0.25S",
                    1,
                    null,
                    1,
                    "PT2M",
                    null,
                    "PT2M",
                    6,
                    null,
                    6,
                    0.2d,
                    null,
                    0.2d,
                    "PT30S",
                    null,
                    "PT30S");
        }
    }

    private static final class FakeSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(dev.inboxbridge.domain.RuntimeEmailAccount bridge) {
            return new PollingSettingsService.EffectivePollingSettings(true, "3m", java.time.Duration.ofMinutes(3), 25);
        }
    }

    private EnvSourceService envSourceService(InboxBridgeConfig config) {
        EnvSourceService service = new EnvSourceService();
        service.setConfigForTest(config);
        return service;
    }

    private RuntimeEmailAccountService runtimeEmailAccountService(InboxBridgeConfig config) {
        RuntimeEmailAccountService service = new RuntimeEmailAccountService() {
            @Override
            public Optional<RuntimeEmailAccount> findSystemBridge(String sourceId) {
                return Optional.of(new RuntimeEmailAccount(
                        sourceId,
                        "SYSTEM",
                        null,
                        "system",
                        true,
                        InboxBridgeConfig.Protocol.IMAP,
                        "outlook.office365.com",
                        993,
                        true,
                        InboxBridgeConfig.AuthMethod.OAUTH2,
                        InboxBridgeConfig.OAuthProvider.MICROSOFT,
                        "user@example.com",
                        "",
                        "refresh",
                        Optional.of("INBOX"),
                        false,
                        SourceFetchMode.POLLING,
                        Optional.of("Imported/Test"),
                        dev.inboxbridge.domain.SourcePostPollSettings.none(),
                        new GmailApiDestinationTarget(
                                "gmail-destination",
                                null,
                                "system",
                                "GMAIL",
                                "destination@example.com",
                                "client",
                                "secret",
                                "refresh",
                                "https://localhost",
                                true,
                                true,
                                true)));
            }
        };
        return service;
    }

    private PollingStatsService pollingStatsService(
            ImportedMessageRepository importedMessageRepository,
            SourcePollEventService sourcePollEventService,
            EnvSourceService envSourceService) {
        return new PollingStatsService(
                importedMessageRepository,
                new dev.inboxbridge.persistence.UserEmailAccountRepository() {
                    @Override
                    public long count() {
                        return 0L;
                    }

                    @Override
                    public long count(String query, Object... params) {
                        return 0L;
                    }

                    @Override
                    public List<dev.inboxbridge.persistence.UserEmailAccount> listAll() {
                        return List.of();
                    }
                },
                new dev.inboxbridge.persistence.UserMailDestinationConfigRepository(),
                sourcePollEventService,
                envSourceService,
                new FakeSourcePollingStateService(),
                new PollingTimelineService());
    }

    private SystemOAuthAppSettingsService systemOAuthAppSettingsService(InboxBridgeConfig config) {
        SecretEncryptionService secretEncryptionService = new SecretEncryptionService();
        secretEncryptionService.setTokenEncryptionKey("replace-me");
        secretEncryptionService.setTokenEncryptionKeyId("v1");

        SystemOAuthAppSettingsService service = new SystemOAuthAppSettingsService();
        service.setConfig(config);
        service.setSecretEncryptionService(secretEncryptionService);
        service.setRepository(new dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository() {
            @Override
            public Optional<dev.inboxbridge.persistence.SystemOAuthAppSettings> findSingleton() {
                return Optional.empty();
            }

            @Override
            public void persist(dev.inboxbridge.persistence.SystemOAuthAppSettings entity) {
            }
        });
        return service;
    }

    private static final class FakeImportedMessageRepository extends ImportedMessageRepository {
        @Override
        public List<Object[]> summarizeBySource() {
            return Collections.singletonList(new Object[] { "outlook-main-imap", Long.valueOf(4), Instant.parse("2026-03-26T10:00:00Z") });
        }

        @Override
        public long count() {
            return 4L;
        }

        @Override
        public List<Object[]> summarizeByImportedDay() {
            return java.util.Collections.singletonList(
                    new Object[] { java.sql.Timestamp.from(Instant.parse("2026-03-26T00:00:00Z")), Long.valueOf(4) });
        }

        @Override
        public List<Instant> listImportedAtSince(Instant since) {
            return List.of(
                    Instant.parse("2026-03-26T10:00:00Z"),
                    Instant.parse("2026-03-26T11:00:00Z"),
                    Instant.parse("2026-03-26T12:00:00Z"),
                    Instant.parse("2026-03-26T13:00:00Z"));
        }
    }

    private static final class FakeOAuthCredentialService extends OAuthCredentialService {
        @Override
        public boolean secureStorageConfigured() {
            return true;
        }

        @Override
        public Optional<StoredOAuthCredential> findGoogleCredential() {
            return Optional.of(new StoredOAuthCredential(
                    GOOGLE_PROVIDER,
                    "gmail-destination",
                    "refresh",
                    "access",
                    Instant.parse("2026-03-26T11:00:00Z"),
                    "gmail.insert",
                    "Bearer",
                    Instant.parse("2026-03-26T10:00:00Z")));
        }

        @Override
        public Optional<StoredOAuthCredential> findMicrosoftCredential(String sourceId) {
            if ("outlook-main-imap".equals(sourceId)) {
                return Optional.of(new StoredOAuthCredential(
                        MICROSOFT_PROVIDER,
                        sourceId,
                        "refresh",
                        "access",
                        Instant.parse("2026-03-26T11:00:00Z"),
                        "imap",
                        "Bearer",
                        Instant.parse("2026-03-26T10:00:00Z")));
            }
            return Optional.empty();
        }
    }

    private static final class FakeSourcePollEventService extends SourcePollEventService {
        private static final AdminPollEventSummary EVENT = new AdminPollEventSummary(
                "outlook-main-imap",
                "scheduler",
                "ERROR",
                Instant.parse("2026-03-26T10:02:00Z"),
                Instant.parse("2026-03-26T10:02:05Z"),
                0,
                0,
                0L,
                0,
                0,
                null,
                null,
                "Source outlook-main-imap failed: AUTHENTICATE failed",
                null, null, null, null, null, null, null, null, null);

        @Override
        public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
            return "outlook-main-imap".equals(sourceId) ? Optional.of(EVENT) : Optional.empty();
        }

        @Override
        public List<AdminPollEventSummary> recentEvents(int limit) {
            return List.of(EVENT);
        }

        @Override
        public List<SourcePollEvent> listSince(Instant since) {
            SourcePollEvent event = new SourcePollEvent();
            event.sourceId = EVENT.sourceId();
            event.triggerName = EVENT.trigger();
            event.status = EVENT.status();
            event.startedAt = EVENT.startedAt();
            event.finishedAt = EVENT.finishedAt();
            event.fetchedCount = EVENT.fetched();
            event.importedCount = EVENT.imported();
            event.duplicateCount = EVENT.duplicates();
            event.errorMessage = EVENT.error();
            return List.of(event);
        }
    }

    private static final class FakeSourceDiagnosticsService extends SourceDiagnosticsService {
        @Override
        public java.util.Map<String, SourceDiagnosticsView> viewByRuntimeAccounts(List<dev.inboxbridge.domain.RuntimeEmailAccount> accounts) {
            if (accounts == null || accounts.isEmpty()) {
                return java.util.Map.of();
            }
            return accounts.stream().collect(java.util.stream.Collectors.toMap(
                    dev.inboxbridge.domain.RuntimeEmailAccount::id,
                    account -> new SourceDiagnosticsView(
                            "gmail-api:demo",
                            null,
                            List.of(),
                            null,
                            null,
                            false,
                            false,
                            List.of())));
        }
    }

    private static final class TestConfig implements InboxBridgeConfig {
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
                    return "google-client";
                }

                @Override
                public String clientSecret() {
                    return "google-secret";
                }

                @Override
                public String refreshToken() {
                    return "replace-me";
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

        @Override
        public Microsoft microsoft() {
            return new Microsoft() {
                @Override
                public String tenant() {
                    return "consumers";
                }

                @Override
                public String clientId() {
                    return "ms-client";
                }

                @Override
                public String clientSecret() {
                    return "ms-secret";
                }

                @Override
                public String redirectUri() {
                    return "http://localhost:8080/api/microsoft-oauth/callback";
                }
            };
        }

        @Override
        public List<Source> sources() {
            return List.of(
                    new TestSource("outlook-main-imap", true, Protocol.IMAP, AuthMethod.OAUTH2, OAuthProvider.MICROSOFT),
                    new TestSource("legacy-pop", false, Protocol.POP3, AuthMethod.PASSWORD, OAuthProvider.NONE));
        }

        private InboxBridgeConfig.Security.Auth.RegistrationCaptcha captchaDefaults() {
            return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha() {
                @Override
                public InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Altcha altcha() {
                    return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Altcha() {
                        @Override
                        public long maxNumber() {
                            return 100000L;
                        }

                        @Override
                        public Optional<String> hmacKey() {
                            return Optional.empty();
                        }
                    };
                }

                @Override
                public InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Turnstile turnstile() {
                    return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Turnstile() {
                        @Override
                        public Optional<String> siteKey() {
                            return Optional.empty();
                        }

                        @Override
                        public Optional<String> secret() {
                            return Optional.empty();
                        }
                    };
                }

                @Override
                public InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Hcaptcha hcaptcha() {
                    return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Hcaptcha() {
                        @Override
                        public Optional<String> siteKey() {
                            return Optional.empty();
                        }

                        @Override
                        public Optional<String> secret() {
                            return Optional.empty();
                        }
                    };
                }
            };
        }

        private Security.Remote remoteDefaults() {
            return new Security.Remote() {
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
                public Optional<String> serviceToken() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> serviceUsername() {
                    return Optional.empty();
                }
            };
        }
    }

    private record TestSource(
            String id,
            boolean enabled,
            InboxBridgeConfig.Protocol protocol,
            InboxBridgeConfig.AuthMethod authMethod,
            InboxBridgeConfig.OAuthProvider oauthProvider) implements InboxBridgeConfig.Source {

        @Override
        public SourceFetchMode fetchMode() {
            return SourceFetchMode.POLLING;
        }

        @Override
        public String host() {
            return protocol == InboxBridgeConfig.Protocol.IMAP ? "outlook.office365.com" : "pop.example.com";
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
            return authMethod == InboxBridgeConfig.AuthMethod.PASSWORD ? "password" : "replace-me";
        }

        @Override
        public Optional<String> oauthRefreshToken() {
            return authMethod == InboxBridgeConfig.AuthMethod.OAUTH2 ? Optional.of("refresh") : Optional.empty();
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
            return Optional.of("Imported/Test");
        }
    }
}
