package dev.inboxbridge.service.remote;

import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.PollingSettingsService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.SourcePollEventService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.SourcePollingStateService;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import dev.inboxbridge.service.user.UserUiPreferenceService;
import jakarta.enterprise.inject.Vetoed;

class RemoteControlServiceTest {

    @Test
    void listSourcesHidesDisabledUserAccountsFromRemoteSurface() {
        RemoteControlService service = new RemoteControlService();
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of(
                userAccount("enabled-source", true),
                userAccount("disabled-source", false)));
        service.appUserService = new FakeAppUserService(owner(7L, "alice"));
        service.runtimeEmailAccountService = new FakeRuntimeEmailAccountService(List.of(runtimeAccount("enabled-source"), runtimeAccount("disabled-source")));
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new SourcePollingStateService() {
            @Override
            public Optional<dev.inboxbridge.dto.SourcePollingStateView> viewForSource(String sourceId) {
                return Optional.empty();
            }
        };
        service.sourcePollEventService = new SourcePollEventService() {
            @Override
            public Optional<dev.inboxbridge.dto.AdminPollEventSummary> latestForSource(String sourceId) {
                return Optional.empty();
            }
        };
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public List<Object[]> summarizeBySource() {
                return List.of();
            }
        };

        List<dev.inboxbridge.dto.RemoteSourceView> sources = service.listSources(owner(7L, "alice"));

        assertEquals(List.of("enabled-source"), sources.stream().map(dev.inboxbridge.dto.RemoteSourceView::sourceId).toList());
        assertTrue(sources.stream().noneMatch((source) -> !source.enabled()));
    }

    @Test
    void viewForUsesEffectiveRuntimeMultiUserMode() {
        RemoteControlService service = new RemoteControlService();
        service.inboxBridgeConfig = new InboxBridgeConfig() {
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
                return 0.2;
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
                        return null;
                    }

                    @Override
                    public Passkeys passkeys() {
                        return null;
                    }

                    @Override
                    public Remote remote() {
                        return new Remote() {
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
                            public Optional<String> serviceToken() {
                                return Optional.empty();
                            }

                            @Override
                            public Optional<String> serviceUsername() {
                                return Optional.empty();
                            }
                        };
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
                return List.of();
            }
        };
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of());
        service.userMailDestinationConfigService = new UserMailDestinationConfigService() {
            @Override
            public Optional<dev.inboxbridge.domain.MailDestinationTarget> resolveForUser(Long userId, String username) {
                return Optional.of(new ImapAppendDestinationTarget(
                        "imap:user-" + userId,
                        userId,
                        username,
                        "imap",
                        "imap.example.com",
                        993,
                        true,
                        InboxBridgeConfig.AuthMethod.PASSWORD,
                        InboxBridgeConfig.OAuthProvider.NONE,
                        username + "@example.com",
                        "secret",
                        "INBOX"));
            }
        };
        service.userUiPreferenceService = new UserUiPreferenceService() {
            @Override
            public Optional<dev.inboxbridge.dto.UserUiPreferenceView> viewForUser(Long userId) {
                return Optional.empty();
            }
        };
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public List<Object[]> summarizeBySource() {
                return List.of();
            }
        };
        service.systemOAuthAppSettingsService = new SystemOAuthAppSettingsService() {
            @Override
            public boolean effectiveMultiUserEnabled() {
                return false;
            }
        };

        var view = service.viewFor(owner(7L, "alice"));

        assertEquals(false, view.session().multiUserEnabled());
    }

    private static AppUser owner(Long id, String username) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = username;
        user.role = AppUser.Role.USER;
        user.active = true;
        user.approved = true;
        return user;
    }

    private static UserEmailAccount userAccount(String sourceId, boolean enabled) {
        UserEmailAccount account = new UserEmailAccount();
        account.userId = 7L;
        account.emailAccountId = sourceId;
        account.enabled = enabled;
        account.protocol = InboxBridgeConfig.Protocol.IMAP;
        account.host = "imap.example.com";
        account.port = 993;
        account.username = "alice@example.com";
        account.folderName = "INBOX";
        account.authMethod = InboxBridgeConfig.AuthMethod.PASSWORD;
        account.oauthProvider = InboxBridgeConfig.OAuthProvider.NONE;
        return account;
    }

    private static RuntimeEmailAccount runtimeAccount(String sourceId) {
        return new RuntimeEmailAccount(
                sourceId,
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Inbox"),
                null);
    }

    private static final class FakeUserEmailAccountRepository extends UserEmailAccountRepository {
        private final List<UserEmailAccount> accounts;

        private FakeUserEmailAccountRepository(List<UserEmailAccount> accounts) {
            this.accounts = accounts;
        }

        @Override
        public List<UserEmailAccount> list(String query, Object... params) {
            if ("userId".equals(query) && params.length == 1) {
                Long userId = (Long) params[0];
                return accounts.stream().filter((account) -> userId.equals(account.userId)).toList();
            }
            return accounts;
        }

        @Override
        public long count(String query, Object... params) {
            if (params.length == 1 && params[0] instanceof Long userId) {
                return accounts.stream().filter((account) -> userId.equals(account.userId) && account.enabled).count();
            }
            return accounts.size();
        }
    }

    @Vetoed
    private static final class FakeAppUserService extends AppUserService {
        private final AppUser owner;

        private FakeAppUserService(AppUser owner) {
            this.owner = owner;
        }

        @Override
        public Optional<AppUser> findById(Long id) {
            return owner.id.equals(id) ? Optional.of(owner) : Optional.empty();
        }
    }

    private static final class FakeRuntimeEmailAccountService extends RuntimeEmailAccountService {
        private final java.util.Map<String, RuntimeEmailAccount> accountsById;

        private FakeRuntimeEmailAccountService(List<RuntimeEmailAccount> accounts) {
            this.accountsById = accounts.stream().collect(java.util.stream.Collectors.toMap(RuntimeEmailAccount::id, account -> account));
        }

        @Override
        public Optional<RuntimeEmailAccount> findAccessibleForUser(AppUser actor, String sourceId) {
            return Optional.ofNullable(accountsById.get(sourceId));
        }
    }

    private static final class FakeSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount emailAccount) {
            return new PollingSettingsService.EffectivePollingSettings(true, "5m", java.time.Duration.ofMinutes(5), 50);
        }
    }
}
