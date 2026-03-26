package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.persistence.AppUser;
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

    private UserBridgeService service() {
        UserBridgeService service = new UserBridgeService();
        service.repository = new InMemoryUserBridgeRepository();
        service.secretEncryptionService = new FakeSecretEncryptionService();
        service.sourcePollEventService = new NoopSourcePollEventService();
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

    private static final class FakeSecretEncryptionService extends SecretEncryptionService {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String keyVersion() {
            return "test";
        }

        @Override
        public EncryptedValue encrypt(String value, String context) {
            return new EncryptedValue("cipher:" + value, "nonce");
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
}
