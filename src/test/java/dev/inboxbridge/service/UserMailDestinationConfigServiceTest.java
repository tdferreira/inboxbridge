package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.UpdateUserMailDestinationRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;

class UserMailDestinationConfigServiceTest {

    @Test
    void updateAllowsBlankUsernameForPendingMicrosoftDestinationOAuth() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();

        service.update(user, new UpdateUserMailDestinationRequest(
                UserMailDestinationConfigService.PROVIDER_OUTLOOK,
                "outlook.office365.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.OAUTH2.name(),
                InboxBridgeConfig.OAuthProvider.MICROSOFT.name(),
                "",
                "",
                "INBOX"));

        UserMailDestinationConfig stored = ((InMemoryUserMailDestinationConfigRepository) service.repository).stored.orElseThrow();
        assertEquals(UserMailDestinationConfigService.PROVIDER_OUTLOOK, stored.provider);
        assertEquals(InboxBridgeConfig.AuthMethod.OAUTH2.name(), stored.authMethod);
        assertEquals(InboxBridgeConfig.OAuthProvider.MICROSOFT.name(), stored.oauthProvider);
        assertNull(stored.username);
    }

    @Test
    void updateUnlinksExistingGoogleDestinationWhenSwitchingAwayFromGmail() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        UserMailDestinationConfig existing = new UserMailDestinationConfig();
        existing.userId = user.id;
        existing.provider = UserMailDestinationConfigService.PROVIDER_GMAIL;
        existing.updatedAt = Instant.now();
        ((InMemoryUserMailDestinationConfigRepository) service.repository).stored = Optional.of(existing);
        FakeUserGmailConfigService gmailService = (FakeUserGmailConfigService) service.userGmailConfigService;
        gmailService.destinationLinked = true;

        service.update(user, new UpdateUserMailDestinationRequest(
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD.name(),
                InboxBridgeConfig.OAuthProvider.NONE.name(),
                "owner@example.com",
                "Secret#123",
                "INBOX"));

        assertTrue(gmailService.unlinkCalled);
    }

    @Test
    void updateUnlinksExistingMicrosoftDestinationWhenSwitchingToPasswordAuth() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        UserMailDestinationConfig existing = new UserMailDestinationConfig();
        existing.userId = user.id;
        existing.provider = UserMailDestinationConfigService.PROVIDER_OUTLOOK;
        existing.authMethod = InboxBridgeConfig.AuthMethod.OAUTH2.name();
        existing.oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        existing.updatedAt = Instant.now();
        ((InMemoryUserMailDestinationConfigRepository) service.repository).stored = Optional.of(existing);
        FakeMicrosoftOAuthService microsoftService = (FakeMicrosoftOAuthService) service.microsoftOAuthService;
        microsoftService.linked = true;

        service.update(user, new UpdateUserMailDestinationRequest(
            UserMailDestinationConfigService.PROVIDER_CUSTOM,
            "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD.name(),
                InboxBridgeConfig.OAuthProvider.NONE.name(),
                "owner@example.com",
                "Secret#123",
                "INBOX"));

        assertTrue(microsoftService.unlinkCalled);
    }

    @Test
    void updateForcesOutlookDestinationToMicrosoftOAuth() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();

        service.update(user, new UpdateUserMailDestinationRequest(
                UserMailDestinationConfigService.PROVIDER_OUTLOOK,
                "outlook.office365.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD.name(),
                InboxBridgeConfig.OAuthProvider.NONE.name(),
                "owner@example.com",
                "Secret#123",
                "INBOX"));

        UserMailDestinationConfig stored = ((InMemoryUserMailDestinationConfigRepository) service.repository).stored.orElseThrow();
        assertEquals(InboxBridgeConfig.AuthMethod.OAUTH2.name(), stored.authMethod);
        assertEquals(InboxBridgeConfig.OAuthProvider.MICROSOFT.name(), stored.oauthProvider);
        assertNull(stored.passwordCiphertext);
        assertNull(stored.passwordNonce);
    }

    private static UserMailDestinationConfigService service() {
        UserMailDestinationConfigService service = new UserMailDestinationConfigService();
        service.repository = new InMemoryUserMailDestinationConfigRepository();
        service.userGmailConfigService = new FakeUserGmailConfigService();
        service.secretEncryptionService = new FakeSecretEncryptionService();
        service.systemOAuthAppSettingsService = new SystemOAuthAppSettingsService() {
            @Override
            public boolean microsoftClientConfigured() {
                return true;
            }

            @Override
            public String microsoftClientId() {
                return "client-id";
            }

            @Override
            public String microsoftClientSecret() {
                return "client-secret";
            }
        };
        service.inboxBridgeConfig = new InboxBridgeConfig() {
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
                return null;
            }

            @Override
            public Gmail gmail() {
                return null;
            }

            @Override
            public java.util.List<Source> sources() {
                return java.util.List.of();
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
                        return "client-id";
                    }

                    @Override
                    public String clientSecret() {
                        return "client-secret";
                    }

                    @Override
                    public String redirectUri() {
                        return "http://localhost:8080/api/microsoft-oauth/callback";
                    }
                };
            }
        };
        service.microsoftOAuthService = new FakeMicrosoftOAuthService();
        return service;
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";
        user.role = AppUser.Role.USER;
        return user;
    }

    private static final class InMemoryUserMailDestinationConfigRepository extends UserMailDestinationConfigRepository {
        private Optional<UserMailDestinationConfig> stored = Optional.empty();

        @Override
        public Optional<UserMailDestinationConfig> findByUserId(Long userId) {
            return stored.filter(config -> config.userId.equals(userId));
        }

        @Override
        public void persist(UserMailDestinationConfig entity) {
            stored = Optional.of(entity);
        }
    }

    private static final class FakeUserGmailConfigService extends UserGmailConfigService {
        private boolean destinationLinked;
        private boolean unlinkCalled;

        @Override
        public boolean sharedGoogleClientConfigured() {
            return true;
        }

        @Override
        public String defaultRedirectUri() {
            return "https://localhost:3000/api/google-oauth/callback";
        }

        @Override
        public boolean destinationLinked(Long userId) {
            return destinationLinked;
        }

        @Override
        public GmailUnlinkResult unlinkForUser(Long userId) {
            unlinkCalled = true;
            destinationLinked = false;
            return new GmailUnlinkResult(true, true);
        }
    }

    private static final class FakeMicrosoftOAuthService extends MicrosoftOAuthService {
        private boolean linked;
        private boolean unlinkCalled;

        @Override
        public boolean destinationLinked(Long userId) {
            return linked;
        }

        @Override
        public void unlinkDestination(Long userId) {
            unlinkCalled = true;
            linked = false;
        }
    }

    private static final class FakeSecretEncryptionService extends SecretEncryptionService {
        private FakeSecretEncryptionService() {
            tokenEncryptionKey = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
            tokenEncryptionKeyId = "v1";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String keyVersion() {
            return "v1";
        }

        @Override
        public EncryptedValue encrypt(String value, String context) {
            return new EncryptedValue("cipher:" + value, "nonce");
        }

        @Override
        public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion, String context) {
            return ciphertextBase64.replaceFirst("^cipher:", "");
        }
    }
}