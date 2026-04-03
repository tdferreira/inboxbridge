package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.dto.UpdateUserEmailAccountRequest;
import dev.inboxbridge.dto.UserEmailAccountView;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;

class UserEmailAccountServiceTest {

    @Test
    void createRejectsDuplicateMailFetcherId() {
        UserEmailAccountService service = service();
        AppUser firstUser = user(1L);
        service.upsert(firstUser, request(null, "shared-id"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(user(2L), request(null, "shared-id")));

        assertEquals("Mail fetcher ID already exists", error.getMessage());
    }

    @Test
    void renameRejectsDuplicateMailFetcherId() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, request(null, "fetcher-a"));
        service.upsert(owner, request(null, "fetcher-b"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(owner, request("fetcher-a", "fetcher-b")));

        assertEquals("Mail fetcher ID already exists", error.getMessage());
    }

    @Test
    void listUsesStoredMicrosoftCredentialWhenBridgeRowHasNoRefreshToken() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, oauthRequest("outlook-main"));
        service.oAuthCredentialService = new FakeOAuthCredentialService(
                new OAuthCredentialService.StoredOAuthCredential(
                        OAuthCredentialService.MICROSOFT_PROVIDER,
                        "outlook-main",
                        "refresh-token",
                        "access-token",
                        Instant.parse("2026-03-27T09:28:30Z"),
                        "scope",
                        "Bearer",
                        Instant.parse("2026-03-27T09:28:30Z")));

        UserEmailAccount emailAccount = service.repository.findByEmailAccountId("outlook-main").orElseThrow();
        emailAccount.oauthRefreshTokenCiphertext = null;
        emailAccount.oauthRefreshTokenNonce = null;

        UserEmailAccountView view = service.listForUser(owner.id).getFirst();

        assertTrue(view.oauthRefreshTokenConfigured());
        assertEquals("DATABASE", view.tokenStorageMode());
        assertEquals("refresh-token", service.decryptRefreshToken(emailAccount));
    }

    @Test
    void listHidesStaleMissingRefreshTokenErrorWhenNewerCredentialExists() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, oauthRequest("outlook-main"));
        service.oAuthCredentialService = new FakeOAuthCredentialService(
                new OAuthCredentialService.StoredOAuthCredential(
                        OAuthCredentialService.MICROSOFT_PROVIDER,
                        "outlook-main",
                        "refresh-token",
                        "access-token",
                        Instant.parse("2026-03-27T09:28:30Z"),
                        "scope",
                        "Bearer",
                        Instant.parse("2026-03-27T09:28:30Z")));
        service.sourcePollEventService = new StaticSourcePollEventService(
                new AdminPollEventSummary(
                        "outlook-main",
                        "scheduler",
                        "ERROR",
                        Instant.parse("2026-03-26T17:45:00Z"),
                        Instant.parse("2026-03-26T17:45:07Z"),
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        "Source outlook-main failed: Source outlook-main is configured for OAuth2 but has no refresh token"));

        UserEmailAccountView view = service.listForUser(owner.id).getFirst();

        assertEquals(null, view.lastEvent());
    }

    @Test
    void listReplacesStaleGmail401ErrorAfterLinkedAccountWasCleared() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, request(null, "sprc-john"));
        service.userGmailConfigRepository = new FakeUserGmailConfigRepository(
                gmailConfig(owner.id, Instant.parse("2026-03-28T08:00:00Z"), false));
        service.sourcePollEventService = new StaticSourcePollEventService(
                new AdminPollEventSummary(
                        "sprc-john",
                        "scheduler",
                        "ERROR",
                        Instant.parse("2026-03-28T07:00:00Z"),
                        Instant.parse("2026-03-28T07:00:05Z"),
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        "Source sprc-john failed: Failed to list Gmail labels: 401 - {\"error\":{\"message\":\"Invalid authentication credentials\"}}"));
        service.sourcePollingStateService = new StaticSourcePollingStateService(
                new dev.inboxbridge.dto.SourcePollingStateView(
                        Instant.parse("2026-03-28T08:30:00Z"),
                        Instant.parse("2026-03-28T08:30:00Z"),
                        1,
                        "Source sprc-john failed: Failed to list Gmail labels: 401 - {\"error\":{\"message\":\"Invalid authentication credentials\"}}",
                        Instant.parse("2026-03-28T07:00:05Z"),
                        null));

        UserEmailAccountView view = service.listForUser(owner.id).getFirst();

        assertNotNull(view.lastEvent());
        assertEquals(
                "Source sprc-john failed: The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.",
                view.lastEvent().error());
        assertNotNull(view.pollingState());
        assertEquals(
                "Source sprc-john failed: The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.",
                view.pollingState().lastFailureReason());
    }

    @Test
    void listExposesSpamJunkMessageCountInLastEvent() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, request(null, "fetcher-a"));
        service.sourcePollEventService = new StaticSourcePollEventService(
                new AdminPollEventSummary(
                        "fetcher-a",
                        "manual",
                        "SUCCESS",
                        Instant.parse("2026-03-28T10:00:00Z"),
                        Instant.parse("2026-03-28T10:00:05Z"),
                        12,
                        3,
                        9,
                        6,
                        "admin",
                        "ADMINISTRATION",
                        null));

        UserEmailAccountView view = service.listForUser(owner.id).getFirst();

        assertNotNull(view.lastEvent());
        assertEquals(6, view.lastEvent().spamJunkMessageCount());
    }

    @Test
    void testConnectionUsesStoredPasswordWhenEditingWithoutReenteringIt() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, request(null, "fetcher-a"));

        EmailAccountConnectionTestResult result = service.testConnection(owner, request("fetcher-a", "fetcher-a"));

        assertTrue(result.success());
        assertEquals("IMAP", result.protocol());
        assertTrue(result.authenticated());
        assertTrue(result.folderAccessible());
        assertEquals(Boolean.FALSE, result.sampleMessageAvailable());
        assertEquals("Secret#123", ((FakeMailSourceClient) service.mailSourceClient).lastBridge.password());
    }

    @Test
    void listFoldersUsesStoredPasswordWhenEditingWithoutReenteringIt() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, request(null, "fetcher-a"));

        var response = service.listFolders(owner, request("fetcher-a", "fetcher-a"));

        assertEquals(List.of("INBOX", "Archive"), response.folders());
        assertEquals("Secret#123", ((FakeMailSourceClient) service.mailSourceClient).lastBridge.password());
    }

    @Test
    void upsertForcesOutlookHostsToMicrosoftOAuth() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);

        service.upsert(owner, new UpdateUserEmailAccountRequest(
                null,
                "outlook-main",
                true,
                "IMAP",
                "outlook.office365.com",
                993,
                true,
                "PASSWORD",
                "NONE",
                "user@example.com",
                "Secret#123",
                "refresh-token",
                "INBOX",
                false,
                "Imported/Test"));

        UserEmailAccount stored = service.repository.findByEmailAccountId("outlook-main").orElseThrow();
        assertEquals(InboxBridgeConfig.AuthMethod.OAUTH2, stored.authMethod);
        assertEquals(InboxBridgeConfig.OAuthProvider.MICROSOFT, stored.oauthProvider);
    }

    @Test
    void previewRejectsOutlookPasswordFlowWithoutOAuthToken() {
        UserEmailAccountService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(user(1L), new UpdateUserEmailAccountRequest(
                        null,
                        "outlook-main",
                        true,
                        "IMAP",
                        "outlook.office365.com",
                        993,
                        true,
                        "PASSWORD",
                        "NONE",
                        "user@example.com",
                        "Secret#123",
                        "",
                        "INBOX",
                        false,
                        "Imported/Test")));

        assertEquals("OAuth refresh token is required or connect provider OAuth first", error.getMessage());
    }

    @Test
    void upsertRejectsSourceMailboxThatMatchesCurrentDestination() {
        UserEmailAccountService service = service();
        service.mailboxConflictService = new MailboxConflictService() {
            @Override
            public boolean conflictsWithCurrentDestination(Long userId, RuntimeEmailAccount source) {
                return true;
            }
        };

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(user(1L), request(null, "fetcher-a")));

        assertEquals(MailboxConflictService.SOURCE_DESTINATION_CONFLICT_MESSAGE, error.getMessage());
    }

    @Test
    void upsertPersistsConfiguredPostPollActions() {
        UserEmailAccountService service = service();
        AppUser owner = user(1L);

        UserEmailAccountView view = service.upsert(owner, new UpdateUserEmailAccountRequest(
                null,
                "fetcher-a",
                true,
                "IMAP",
                "imap.example.com",
                993,
                true,
                "PASSWORD",
                "NONE",
                "user@example.com",
                "Secret#123",
                "",
                "INBOX",
                false,
                "Imported/Test",
                true,
                "MOVE",
                "Archive"));

        UserEmailAccount stored = service.repository.findByEmailAccountId("fetcher-a").orElseThrow();
        assertTrue(stored.markReadAfterPoll);
        assertEquals(dev.inboxbridge.domain.SourcePostPollAction.MOVE, stored.postPollAction);
        assertEquals("Archive", stored.postPollTargetFolder);
        assertTrue(view.markReadAfterPoll());
        assertEquals("MOVE", view.postPollAction());
        assertEquals("Archive", view.postPollTargetFolder());
    }

    @Test
    void previewRejectsPostPollActionsForPop3Accounts() {
        UserEmailAccountService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(user(1L), new UpdateUserEmailAccountRequest(
                        null,
                        "fetcher-a",
                        true,
                        "POP3",
                        "pop.example.com",
                        995,
                        true,
                        "PASSWORD",
                        "NONE",
                        "user@example.com",
                        "Secret#123",
                        "",
                        "INBOX",
                        false,
                        "Imported/Test",
                        true,
                        "DELETE",
                        "")));

        assertEquals("Source-side message actions are only supported for IMAP accounts", error.getMessage());
    }

    private UserEmailAccountService service() {
        UserEmailAccountService service = new UserEmailAccountService();
        service.repository = new InMemoryUserEmailAccountRepository();
        service.userGmailConfigRepository = new FakeUserGmailConfigRepository(null);
        service.secretEncryptionService = new FakeSecretEncryptionService();
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.importedMessageRepository = new EmptyImportedMessageRepository();
        service.userPollingSettingsService = new FakeUserPollingSettingsService();
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new NoopSourcePollingStateService();
        service.oAuthCredentialService = new FakeOAuthCredentialService(null);
        service.envSourceService = new FakeEnvSourceService();
        service.mailSourceClient = new FakeMailSourceClient();
        service.mailboxConflictService = new MailboxConflictService() {
            @Override
            public boolean conflictsWithCurrentDestination(Long userId, RuntimeEmailAccount source) {
                return false;
            }
        };
        return service;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = "user-" + id;
        return user;
    }

    private UpdateUserEmailAccountRequest request(String originalEmailAccountId, String emailAccountId) {
        return new UpdateUserEmailAccountRequest(
                originalEmailAccountId,
                emailAccountId,
                true,
                "IMAP",
                "imap.example.com",
                993,
                true,
                "PASSWORD",
                "NONE",
                "user@example.com",
                "Secret#123",
                "",
                "INBOX",
                false,
                "Imported/Test");
    }

    private UpdateUserEmailAccountRequest oauthRequest(String emailAccountId) {
        return new UpdateUserEmailAccountRequest(
                null,
                emailAccountId,
                true,
                "IMAP",
                "outlook.office365.com",
                993,
                true,
                "OAUTH2",
                "MICROSOFT",
                "user@example.com",
                "",
                "refresh-token",
                "INBOX",
                false,
                "Imported/Test");
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

    private static final class InMemoryUserEmailAccountRepository extends UserEmailAccountRepository {
        private final List<UserEmailAccount> emailAccounts = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<UserEmailAccount> findByEmailAccountId(String emailAccountId) {
            return emailAccounts.stream().filter(emailAccount -> emailAccount.emailAccountId.equals(emailAccountId)).findFirst();
        }

        @Override
        public List<UserEmailAccount> listByUserId(Long userId) {
            return emailAccounts.stream()
                    .filter(emailAccount -> emailAccount.userId.equals(userId))
                    .sorted((left, right) -> left.emailAccountId.compareTo(right.emailAccountId))
                    .toList();
        }

        @Override
        public void persist(UserEmailAccount emailAccount) {
            if (emailAccount.id == null) {
                emailAccount.id = sequence++;
                if (emailAccount.createdAt == null) {
                    emailAccount.createdAt = Instant.now();
                }
                emailAccounts.add(emailAccount);
            }
            if (emailAccount.updatedAt == null) {
                emailAccount.updatedAt = Instant.now();
            }
        }
    }

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
            return Optional.empty();
        }
    }

    private static final class EmptyImportedMessageRepository extends ImportedMessageRepository {
        @Override
        public List<Object[]> summarizeBySource() {
            return List.of();
        }
    }

    private static final class StaticSourcePollEventService extends SourcePollEventService {
        private final AdminPollEventSummary event;

        private StaticSourcePollEventService(AdminPollEventSummary event) {
            this.event = event;
        }

        @Override
        public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
            return Optional.ofNullable(event);
        }
    }

    private static final class FakeUserPollingSettingsService extends UserPollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsForUser(Long userId) {
            return new PollingSettingsService.EffectivePollingSettings(true, "5m", java.time.Duration.ofMinutes(5), 50);
        }
    }

    private static final class FakeSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(dev.inboxbridge.domain.RuntimeEmailAccount bridge) {
            return new PollingSettingsService.EffectivePollingSettings(true, "5m", java.time.Duration.ofMinutes(5), 50);
        }
    }

    private static final class NoopSourcePollingStateService extends SourcePollingStateService {
        @Override
        public Optional<dev.inboxbridge.dto.SourcePollingStateView> viewForSource(String sourceId) {
            return Optional.empty();
        }

        @Override
        public java.util.Map<String, dev.inboxbridge.dto.SourcePollingStateView> viewBySourceIds(List<String> sourceIds) {
            return java.util.Map.of();
        }
    }

    private static final class StaticSourcePollingStateService extends SourcePollingStateService {
        private final dev.inboxbridge.dto.SourcePollingStateView state;

        private StaticSourcePollingStateService(dev.inboxbridge.dto.SourcePollingStateView state) {
            this.state = state;
        }

        @Override
        public Optional<dev.inboxbridge.dto.SourcePollingStateView> viewForSource(String sourceId) {
            return Optional.ofNullable(state);
        }

        @Override
        public java.util.Map<String, dev.inboxbridge.dto.SourcePollingStateView> viewBySourceIds(List<String> sourceIds) {
            if (state == null || sourceIds.isEmpty()) {
                return java.util.Map.of();
            }
            return java.util.Map.of(sourceIds.getFirst(), state);
        }
    }

    private static final class FakeOAuthCredentialService extends OAuthCredentialService {
        private final StoredOAuthCredential credential;

        private FakeOAuthCredentialService(StoredOAuthCredential credential) {
            this.credential = credential;
        }

        @Override
        public boolean secureStorageConfigured() {
            return credential != null;
        }

        @Override
        public Optional<StoredOAuthCredential> findMicrosoftCredential(String sourceId) {
            if (credential == null || !credential.subjectKey().equals(sourceId)) {
                return Optional.empty();
            }
            return Optional.of(credential);
        }

        @Override
        public Optional<StoredOAuthCredential> findGoogleCredential(String subjectKey) {
            return Optional.empty();
        }
    }

    private static final class FakeEnvSourceService extends EnvSourceService {
        @Override
        public List<IndexedSource> configuredSources() {
            return List.of();
        }
    }

    private static final class FakeUserGmailConfigRepository extends UserGmailConfigRepository {
        private final UserGmailConfig config;

        private FakeUserGmailConfigRepository(UserGmailConfig config) {
            this.config = config;
        }

        @Override
        public Optional<UserGmailConfig> findByUserId(Long userId) {
            if (config == null || !config.userId.equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(config);
        }
    }

    private UserGmailConfig gmailConfig(Long userId, Instant updatedAt, boolean hasRefreshToken) {
        UserGmailConfig config = new UserGmailConfig();
        config.userId = userId;
        config.destinationUser = "me";
        config.redirectUri = "https://localhost:3000/api/google-oauth/callback";
        config.createMissingLabels = true;
        config.neverMarkSpam = false;
        config.processForCalendar = false;
        config.updatedAt = updatedAt;
        if (hasRefreshToken) {
            config.refreshTokenCiphertext = "cipher:refresh";
            config.refreshTokenNonce = "nonce";
        }
        return config;
    }

    private static final class FakeMailSourceClient extends MailSourceClient {
        private RuntimeEmailAccount lastBridge;

        @Override
        public dev.inboxbridge.dto.EmailAccountConnectionTestResult testConnection(RuntimeEmailAccount bridge) {
            this.lastBridge = bridge;
            return new dev.inboxbridge.dto.EmailAccountConnectionTestResult(
                    true,
                    "Connection test succeeded.",
                    bridge.protocol().name(),
                    bridge.host(),
                    bridge.port(),
                    bridge.tls(),
                    bridge.authMethod().name(),
                    bridge.oauthProvider().name(),
                    true,
                    bridge.folder().orElse("INBOX"),
                    true,
                    bridge.unreadOnly(),
                    Boolean.TRUE,
                    bridge.unreadOnly() ? Boolean.TRUE : null,
                    0,
                    0,
                    Boolean.FALSE,
                    null);
        }

        @Override
        public List<String> listFolders(RuntimeEmailAccount bridge) {
            this.lastBridge = bridge;
            return List.of("INBOX", "Archive");
        }
    }
}
