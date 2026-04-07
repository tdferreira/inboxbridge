package dev.inboxbridge.service;

import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserEmailAccount;

class RuntimeEmailAccountServiceTest {

    @Test
    void listEnabledForPollingSkipsInactiveUserOwners() {
        RuntimeEmailAccountService service = new RuntimeEmailAccountService();
        service.config = new InboxBridgeConfig() {
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
                return null;
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
                        return "client-id";
                    }

                    @Override
                    public String clientSecret() {
                        return "client-secret";
                    }

                    @Override
                    public String refreshToken() {
                        return "refresh-token";
                    }

                    @Override
                    public String redirectUri() {
                        return "https://localhost:3000/api/google-oauth/callback";
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
            public List<Source> sources() {
                return List.of();
            }
        };
        service.systemOAuthAppSettingsService = new SystemOAuthAppSettingsService() {
            @Override
            public String googleDestinationUser() {
                return "me";
            }

            @Override
            public String googleClientId() {
                return "client-id";
            }

            @Override
            public String googleClientSecret() {
                return "client-secret";
            }

            @Override
            public String googleRefreshToken() {
                return "refresh-token";
            }

            @Override
            public String googleRedirectUri() {
                return "https://localhost:3000/api/google-oauth/callback";
            }
        };
        service.envSourceService = new EnvSourceService() {
            @Override
            public List<IndexedSource> configuredSources() {
                return List.of();
            }
        };
        service.userEmailAccountService = new UserEmailAccountService() {
            @Override
            public List<UserEmailAccount> listEnabledBridges() {
                return List.of(userBridge(1L, "active-user-bridge"), userBridge(2L, "inactive-user-bridge"));
            }

            @Override
            public String decryptPassword(UserEmailAccount bridge) {
                return "secret";
            }

            @Override
            public String decryptRefreshToken(UserEmailAccount bridge) {
                return "";
            }
        };
        service.userMailDestinationConfigService = new UserMailDestinationConfigService() {
            @Override
            public Optional<MailDestinationTarget> resolveForUser(Long userId, String ownerUsername) {
                return Optional.of(new GmailApiDestinationTarget(
                        "user-gmail:" + userId,
                        userId,
                        ownerUsername,
                        UserMailDestinationConfigService.PROVIDER_GMAIL,
                        "me",
                        "client-id",
                        "client-secret",
                        "refresh-token",
                        "https://localhost:3000/api/google-oauth/callback",
                        true,
                        false,
                        false));
            }
        };
        service.appUserRepository = new AppUserRepository() {
            @Override
            public Optional<AppUser> findByIdOptional(Long id) {
                return switch (Math.toIntExact(id)) {
                    case 1 -> Optional.of(user(1L, true, true, "alice"));
                    case 2 -> Optional.of(user(2L, false, true, "bob"));
                    default -> Optional.empty();
                };
            }
        };

        assertEquals(List.of("active-user-bridge"), service.listEnabledForPolling().stream().map(bridge -> bridge.id()).toList());
    }

    private static AppUser user(Long id, boolean active, boolean approved, String username) {
        AppUser user = new AppUser();
        user.id = id;
        user.active = active;
        user.approved = approved;
        user.username = username;
        user.role = AppUser.Role.USER;
        return user;
    }

    private static UserEmailAccount userBridge(Long userId, String emailAccountId) {
        UserEmailAccount bridge = new UserEmailAccount();
        bridge.userId = userId;
        bridge.emailAccountId = emailAccountId;
        bridge.enabled = true;
        bridge.protocol = InboxBridgeConfig.Protocol.IMAP;
        bridge.host = "imap.example.com";
        bridge.port = 993;
        bridge.tls = true;
        bridge.authMethod = InboxBridgeConfig.AuthMethod.PASSWORD;
        bridge.oauthProvider = InboxBridgeConfig.OAuthProvider.NONE;
        bridge.username = emailAccountId + "@example.com";
        bridge.folderName = "INBOX";
        bridge.unreadOnly = false;
        bridge.customLabel = null;
        bridge.createdAt = Instant.now();
        bridge.updatedAt = bridge.createdAt;
        return bridge;
    }
}
