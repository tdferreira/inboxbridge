package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.BridgeConnectionTestResult;
import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.dto.UserBridgeView;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.persistence.UserBridgeRepository;

class UserBridgeServiceTest {

    @Test
    void createRejectsDuplicateMailFetcherId() {
        UserBridgeService service = service();
        AppUser firstUser = user(1L);
        service.upsert(firstUser, request(null, "shared-id"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.upsert(user(2L), request(null, "shared-id")));

        assertEquals("Mail fetcher ID already exists", error.getMessage());
    }

    @Test
    void renameRejectsDuplicateMailFetcherId() {
        UserBridgeService service = service();
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
        UserBridgeService service = service();
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

        UserBridge bridge = service.repository.findByBridgeId("outlook-main").orElseThrow();
        bridge.oauthRefreshTokenCiphertext = null;
        bridge.oauthRefreshTokenNonce = null;

        UserBridgeView view = service.listForUser(owner.id).getFirst();

        assertTrue(view.oauthRefreshTokenConfigured());
        assertEquals("DATABASE", view.tokenStorageMode());
        assertEquals("refresh-token", service.decryptRefreshToken(bridge));
    }

    @Test
    void listHidesStaleMissingRefreshTokenErrorWhenNewerCredentialExists() {
        UserBridgeService service = service();
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
                        "Source outlook-main failed: Source outlook-main is configured for OAuth2 but has no refresh token"));

        UserBridgeView view = service.listForUser(owner.id).getFirst();

        assertEquals(null, view.lastEvent());
    }

    @Test
    void testConnectionUsesStoredPasswordWhenEditingWithoutReenteringIt() {
        UserBridgeService service = service();
        AppUser owner = user(1L);
        service.upsert(owner, request(null, "fetcher-a"));

        BridgeConnectionTestResult result = service.testConnection(owner, request("fetcher-a", "fetcher-a"));

        assertTrue(result.success());
        assertEquals("IMAP", result.protocol());
        assertTrue(result.authenticated());
        assertTrue(result.folderAccessible());
        assertEquals(Boolean.FALSE, result.sampleMessageAvailable());
        assertEquals("Secret#123", ((FakeMailSourceClient) service.mailSourceClient).lastBridge.password());
    }

    private UserBridgeService service() {
        UserBridgeService service = new UserBridgeService();
        service.repository = new InMemoryUserBridgeRepository();
        service.secretEncryptionService = new FakeSecretEncryptionService();
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.importedMessageRepository = new EmptyImportedMessageRepository();
        service.userPollingSettingsService = new FakeUserPollingSettingsService();
        service.sourcePollingSettingsService = new FakeSourcePollingSettingsService();
        service.sourcePollingStateService = new NoopSourcePollingStateService();
        service.oAuthCredentialService = new FakeOAuthCredentialService(null);
        service.envSourceService = new FakeEnvSourceService();
        service.mailSourceClient = new FakeMailSourceClient();
        return service;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = "user-" + id;
        return user;
    }

    private UpdateUserBridgeRequest request(String originalBridgeId, String bridgeId) {
        return new UpdateUserBridgeRequest(
                originalBridgeId,
                bridgeId,
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

    private UpdateUserBridgeRequest oauthRequest(String bridgeId) {
        return new UpdateUserBridgeRequest(
                null,
                bridgeId,
                true,
                "IMAP",
                "outlook.office365.com",
                993,
                true,
                "OAUTH2",
                "MICROSOFT",
                "user@example.com",
                "",
                "",
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

    private static final class InMemoryUserBridgeRepository extends UserBridgeRepository {
        private final List<UserBridge> bridges = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<UserBridge> findByBridgeId(String bridgeId) {
            return bridges.stream().filter(bridge -> bridge.bridgeId.equals(bridgeId)).findFirst();
        }

        @Override
        public List<UserBridge> listByUserId(Long userId) {
            return bridges.stream()
                    .filter(bridge -> bridge.userId.equals(userId))
                    .sorted((left, right) -> left.bridgeId.compareTo(right.bridgeId))
                    .toList();
        }

        @Override
        public void persist(UserBridge bridge) {
            if (bridge.id == null) {
                bridge.id = sequence++;
                if (bridge.createdAt == null) {
                    bridge.createdAt = Instant.now();
                }
                bridges.add(bridge);
            }
            if (bridge.updatedAt == null) {
                bridge.updatedAt = Instant.now();
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
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(dev.inboxbridge.domain.RuntimeBridge bridge) {
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
    }

    private static final class FakeEnvSourceService extends EnvSourceService {
        @Override
        public List<IndexedSource> configuredSources() {
            return List.of();
        }
    }

    private static final class FakeMailSourceClient extends MailSourceClient {
        private RuntimeBridge lastBridge;

        @Override
        public dev.inboxbridge.dto.BridgeConnectionTestResult testConnection(RuntimeBridge bridge) {
            this.lastBridge = bridge;
            return new dev.inboxbridge.dto.BridgeConnectionTestResult(
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
    }
}
