package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.persistence.ImportedMessageRepository;

class AdminDashboardServiceTest {

    @Test
    void dashboardIncludesEnvDefinedBridgesAndRecentEvents() {
        AdminDashboardService service = new AdminDashboardService();
        service.config = new TestConfig();
        service.importedMessageRepository = new FakeImportedMessageRepository();
        service.oAuthCredentialService = new FakeOAuthCredentialService();
        service.sourcePollEventService = new FakeSourcePollEventService();
        service.pollingSettingsService = new FakePollingSettingsService();

        AdminDashboardResponse response = service.dashboard();

        assertEquals(2, response.overall().configuredSources());
        assertEquals(1, response.overall().enabledSources());
        assertEquals(4L, response.overall().totalImportedMessages());
        assertEquals(1, response.overall().sourcesWithErrors());
        assertEquals("3m", response.overall().pollInterval());
        assertEquals(25, response.overall().fetchWindow());
        assertFalse(response.polling().defaultPollEnabled());
        assertEquals("3m", response.polling().effectivePollInterval());

        assertEquals("DATABASE", response.destination().tokenStorageMode());
        assertEquals(2, response.bridges().size());
        assertEquals("outlook-main-imap", response.bridges().getFirst().id());
        assertEquals(4L, response.bridges().getFirst().totalImportedMessages());
        assertEquals("DATABASE", response.bridges().getFirst().tokenStorageMode());
        assertNotNull(response.bridges().getFirst().lastEvent());
        assertEquals("ERROR", response.bridges().getFirst().lastEvent().status());

        assertEquals(1, response.recentEvents().size());
        assertEquals("outlook-main-imap", response.recentEvents().getFirst().sourceId());
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
                    25);
        }
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
                0,
                "Source outlook-main-imap failed: AUTHENTICATE failed");

        @Override
        public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
            return "outlook-main-imap".equals(sourceId) ? Optional.of(EVENT) : Optional.empty();
        }

        @Override
        public List<AdminPollEventSummary> recentEvents(int limit) {
            return List.of(EVENT);
        }
    }

    private static final class TestConfig implements BridgeConfig {
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
    }

    private record TestSource(
            String id,
            boolean enabled,
            BridgeConfig.Protocol protocol,
            BridgeConfig.AuthMethod authMethod,
            BridgeConfig.OAuthProvider oauthProvider) implements BridgeConfig.Source {

        @Override
        public String host() {
            return protocol == BridgeConfig.Protocol.IMAP ? "outlook.office365.com" : "pop.example.com";
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
            return authMethod == BridgeConfig.AuthMethod.PASSWORD ? "password" : "replace-me";
        }

        @Override
        public Optional<String> oauthRefreshToken() {
            return authMethod == BridgeConfig.AuthMethod.OAUTH2 ? Optional.of("refresh") : Optional.empty();
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
