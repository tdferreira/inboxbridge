package dev.inboxbridge.service.user;

import dev.inboxbridge.service.security.SecretEncryptionService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;
import dev.inboxbridge.service.destination.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.DestinationMailboxFolderOptionsView;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.dto.UpdateUserMailDestinationRequest;
import dev.inboxbridge.dto.UserMailDestinationView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;

class UserMailDestinationConfigServiceTest {

    @Test
    void updateRequiresUsernameForMicrosoftDestinationOAuth() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(user, new UpdateUserMailDestinationRequest(
                        UserMailDestinationConfigService.PROVIDER_OUTLOOK,
                        "outlook.office365.com",
                        993,
                        true,
                        InboxBridgeConfig.AuthMethod.OAUTH2.name(),
                        InboxBridgeConfig.OAuthProvider.MICROSOFT.name(),
                        "",
                        "",
                        "INBOX")));

        assertEquals("Destination username is required", error.getMessage());
    }

    @Test
    void viewForUserDoesNotReportGmailAsConnectedWhenOnlySharedClientExists() {
        UserMailDestinationConfigService service = service();

        UserMailDestinationView view = service.viewForUser(7L);

        assertEquals(UserMailDestinationConfigService.PROVIDER_GMAIL, view.provider());
        org.junit.jupiter.api.Assertions.assertFalse(view.configured());
        org.junit.jupiter.api.Assertions.assertFalse(view.linked());
        org.junit.jupiter.api.Assertions.assertFalse(view.oauthConnected());
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

    @Test
    void updateRejectsNonTlsDestinationConnections() {
        UserMailDestinationConfigService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(user(), new UpdateUserMailDestinationRequest(
                        UserMailDestinationConfigService.PROVIDER_CUSTOM,
                        "imap.example.com",
                        143,
                        false,
                        InboxBridgeConfig.AuthMethod.PASSWORD.name(),
                        InboxBridgeConfig.OAuthProvider.NONE.name(),
                        "owner@example.com",
                        "Secret#123",
                        "INBOX")));

        assertEquals("InboxBridge requires TLS for every destination mailbox connection.", error.getMessage());
    }

    @Test
    void listFoldersForUserDelegatesToLinkedImapDestination() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        UserMailDestinationConfig existing = new UserMailDestinationConfig();
        existing.userId = user.id;
        existing.provider = UserMailDestinationConfigService.PROVIDER_OUTLOOK;
        existing.host = "outlook.office365.com";
        existing.port = 993;
        existing.tls = true;
        existing.authMethod = InboxBridgeConfig.AuthMethod.OAUTH2.name();
        existing.oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        existing.username = "owner@example.com";
        existing.folderName = "Archive";
        existing.updatedAt = Instant.now();
        ((InMemoryUserMailDestinationConfigRepository) service.repository).stored = Optional.of(existing);
        FakeMicrosoftOAuthService microsoftService = (FakeMicrosoftOAuthService) service.microsoftOAuthService;
        microsoftService.linked = true;
        FakeImapAppendMailDestinationService imapService = (FakeImapAppendMailDestinationService) service.imapAppendMailDestinationService;
        imapService.linked = true;
        imapService.folders = List.of("INBOX", "Archive", "Sent Items");

        DestinationMailboxFolderOptionsView view = service.listFoldersForUser(user.id, user.username);

        assertEquals(List.of("INBOX", "Archive", "Sent Items"), view.folders());
        assertEquals("owner@example.com", imapService.lastTarget.username());
    }

    @Test
    void listFoldersForUserAllowsLinkedMicrosoftOauthWithoutSecretStorage() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        UserMailDestinationConfig existing = new UserMailDestinationConfig();
        existing.userId = user.id;
        existing.provider = UserMailDestinationConfigService.PROVIDER_OUTLOOK;
        existing.host = "outlook.office365.com";
        existing.port = 993;
        existing.tls = true;
        existing.authMethod = InboxBridgeConfig.AuthMethod.OAUTH2.name();
        existing.oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        existing.username = "owner@example.com";
        existing.folderName = "INBOX";
        existing.updatedAt = Instant.now();
        ((InMemoryUserMailDestinationConfigRepository) service.repository).stored = Optional.of(existing);
        FakeMicrosoftOAuthService microsoftService = (FakeMicrosoftOAuthService) service.microsoftOAuthService;
        microsoftService.linked = true;
        FakeSecretEncryptionService secretService = (FakeSecretEncryptionService) service.secretEncryptionService;
        secretService.configured = false;
        FakeImapAppendMailDestinationService imapService = (FakeImapAppendMailDestinationService) service.imapAppendMailDestinationService;
        imapService.linked = true;
        imapService.folders = List.of("INBOX", "Archive");

        DestinationMailboxFolderOptionsView view = service.listFoldersForUser(user.id, user.username);

        assertEquals(List.of("INBOX", "Archive"), view.folders());
        assertEquals(InboxBridgeConfig.AuthMethod.OAUTH2, imapService.lastTarget.authMethod());
        assertNull(imapService.lastTarget.password());
    }

    @Test
    void listFoldersForUserRejectsUnlinkedDestination() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        UserMailDestinationConfig existing = new UserMailDestinationConfig();
        existing.userId = user.id;
        existing.provider = UserMailDestinationConfigService.PROVIDER_CUSTOM;
        existing.host = "imap.example.com";
        existing.port = 993;
        existing.tls = true;
        existing.authMethod = InboxBridgeConfig.AuthMethod.PASSWORD.name();
        existing.oauthProvider = InboxBridgeConfig.OAuthProvider.NONE.name();
        existing.username = "owner@example.com";
        existing.passwordCiphertext = "cipher:Secret#123";
        existing.passwordNonce = "nonce";
        existing.keyVersion = "v1";
        existing.updatedAt = Instant.now();
        ((InMemoryUserMailDestinationConfigRepository) service.repository).stored = Optional.of(existing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.listFoldersForUser(user.id, user.username));

        assertEquals("Save and connect the destination mailbox before loading folders.", error.getMessage());
    }

    @Test
    void testConnectionForUserDelegatesToImapPreviewTarget() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        FakeMicrosoftOAuthService microsoftService = (FakeMicrosoftOAuthService) service.microsoftOAuthService;
        microsoftService.linked = true;
        FakeImapAppendMailDestinationService imapService = (FakeImapAppendMailDestinationService) service.imapAppendMailDestinationService;
        imapService.linked = true;
        imapService.testResult = new EmailAccountConnectionTestResult(true, "Connection test succeeded.", "IMAP", "outlook.office365.com", 993, true, "OAUTH2", "MICROSOFT", true, "INBOX", true, false, null, null, 0, null, false, null, null);

        EmailAccountConnectionTestResult result = service.testConnectionForUser(user, new UpdateUserMailDestinationRequest(
                UserMailDestinationConfigService.PROVIDER_OUTLOOK,
                "outlook.office365.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.OAUTH2.name(),
                InboxBridgeConfig.OAuthProvider.MICROSOFT.name(),
                "owner@example.com",
                "",
                "INBOX"));

        assertEquals("Connection test succeeded.", result.message());
        assertEquals("owner@example.com", imapService.lastTarget.username());
        assertEquals(InboxBridgeConfig.AuthMethod.OAUTH2, imapService.lastTarget.authMethod());
    }

    @Test
    void testConnectionForUserRejectsUnlinkedMicrosoftOauthDestination() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.testConnectionForUser(user, new UpdateUserMailDestinationRequest(
                        UserMailDestinationConfigService.PROVIDER_OUTLOOK,
                        "outlook.office365.com",
                        993,
                        true,
                        InboxBridgeConfig.AuthMethod.OAUTH2.name(),
                        InboxBridgeConfig.OAuthProvider.MICROSOFT.name(),
                        "owner@example.com",
                        "",
                        "INBOX")));

        assertEquals("Save and connect the destination mailbox before testing it.", error.getMessage());
    }

    @Test
    void updateDisablesSourcesThatNowMatchTheDestination() {
        UserMailDestinationConfigService service = service();
        AppUser user = user();
        TrackingMailboxConflictService mailboxConflictService = new TrackingMailboxConflictService();
        service.mailboxConflictService = mailboxConflictService;

        service.update(user, new UpdateUserMailDestinationRequest(
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD.name(),
                InboxBridgeConfig.OAuthProvider.NONE.name(),
                "owner@example.com",
                "Secret#123",
                "Archive"));

        assertEquals(List.of(user.id), mailboxConflictService.disabledForUsers);
    }

    private static UserMailDestinationConfigService service() {
        UserMailDestinationConfigService service = new UserMailDestinationConfigService();
        service.repository = new InMemoryUserMailDestinationConfigRepository();
        service.mailboxConflictService = new TrackingMailboxConflictService();
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
        service.imapAppendMailDestinationService = new FakeImapAppendMailDestinationService();
        return service;
    }

    private static final class TrackingMailboxConflictService extends MailboxConflictService {
        private final java.util.List<Long> disabledForUsers = new java.util.ArrayList<>();

        @Override
        public java.util.List<String> disableSourcesMatchingCurrentDestination(Long userId) {
            disabledForUsers.add(userId);
            return java.util.List.of();
        }
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
        public java.util.Optional<dev.inboxbridge.dto.UserGmailConfigView> viewForUser(Long userId) {
            return java.util.Optional.empty();
        }

        @Override
        public dev.inboxbridge.dto.UserGmailConfigView defaultView(Long userId) {
            return new dev.inboxbridge.dto.UserGmailConfigView(
                    "me",
                    false,
                    false,
                    false,
                    defaultRedirectUri(),
                    defaultRedirectUri(),
                    true,
                    true,
                    false,
                    false);
        }

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
        private boolean configured = true;

        private FakeSecretEncryptionService() {
            setTokenEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
            setTokenEncryptionKeyId("v1");
        }

        @Override
        public boolean isConfigured() {
            return configured;
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

    private static final class FakeImapAppendMailDestinationService extends ImapAppendMailDestinationService {
        private List<String> folders = List.of();
        private boolean linked;
        private dev.inboxbridge.domain.ImapAppendDestinationTarget lastTarget;
        private EmailAccountConnectionTestResult testResult = new EmailAccountConnectionTestResult(true, "Connection test succeeded.", "IMAP", "imap.example.com", 993, true, "PASSWORD", "NONE", true, "INBOX", true, false, null, null, 0, null, false, null, null);

        @Override
        public boolean isLinked(dev.inboxbridge.domain.MailDestinationTarget target) {
            return linked && target instanceof dev.inboxbridge.domain.ImapAppendDestinationTarget;
        }

        @Override
        public List<String> listFolders(dev.inboxbridge.domain.ImapAppendDestinationTarget target) {
            lastTarget = target;
            return folders;
        }

        @Override
        public EmailAccountConnectionTestResult testConnection(dev.inboxbridge.domain.ImapAppendDestinationTarget target) {
            lastTarget = target;
            return testResult;
        }
    }
}
