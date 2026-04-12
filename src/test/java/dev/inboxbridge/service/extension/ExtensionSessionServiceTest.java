package dev.inboxbridge.service.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.ExtensionSecurityConfig;
import dev.inboxbridge.dto.ExtensionSessionCreateRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;

class ExtensionSessionServiceTest {

    @Test
    void createSessionStoresOnlyTokenHashAndListsSessionMetadata() {
        ExtensionSessionService service = configuredService();
        InMemoryExtensionSessionRepository repository = (InMemoryExtensionSessionRepository) service.repository;

        AppUser user = new AppUser();
        user.id = 11L;

        var created = service.createSession(user, new ExtensionSessionCreateRequest("Chrome", "chromium", "0.1.0"));

        assertNotNull(created.token());
        ExtensionSession persisted = repository.byId.get(created.id());
        assertNotNull(persisted);
        assertNotEquals(created.token(), persisted.tokenHash);
        assertEquals(service.hashToken(created.token()), persisted.tokenHash);
        assertEquals(List.of(created.id()), service.listSessions(user).stream().map(view -> view.id()).toList());
    }

    @Test
    void authenticatedSessionsRotateRefreshTokensAndExpireAccessTokens() {
        ExtensionSessionService service = configuredService();
        InMemoryExtensionSessionRepository repository = (InMemoryExtensionSessionRepository) service.repository;

        AppUser user = new AppUser();
        user.id = 7L;
        var created = service.createAuthenticatedSession(user, "Desktop", "chromium", "0.2.0");

        assertTrue(service.authenticate(created.accessToken()).isPresent());
        assertNotNull(created.session().refreshTokenHash);
        assertNotNull(created.session().accessExpiresAt);

        String previousAccessHash = created.session().tokenHash;
        String previousRefreshHash = created.session().refreshTokenHash;
        var refreshed = service.refresh(created.refreshToken());

        assertTrue(refreshed.isPresent());
        assertNotEquals(previousAccessHash, repository.byId.get(created.session().id).tokenHash);
        assertNotEquals(previousRefreshHash, repository.byId.get(created.session().id).refreshTokenHash);
        assertTrue(service.authenticate(created.accessToken()).isEmpty());
        assertTrue(service.authenticate(refreshed.orElseThrow().accessToken()).isPresent());
    }

    @Test
    void authenticateUpdatesLastUsedAndRejectsRevokedOrExpiredSession() {
        ExtensionSessionService service = configuredService();
        InMemoryExtensionSessionRepository repository = (InMemoryExtensionSessionRepository) service.repository;

        AppUser user = new AppUser();
        user.id = 7L;
        var created = service.createSession(user, new ExtensionSessionCreateRequest(null, null, null));

        var authenticated = service.authenticate(created.token());

        assertTrue(authenticated.isPresent());
        assertEquals(user.id, authenticated.get().userId());
        assertNotNull(repository.byId.get(created.id()).lastUsedAt);

        assertTrue(service.revokeSession(user, created.id()));
        assertTrue(service.authenticate(created.token()).isEmpty());

        var rotating = service.createAuthenticatedSession(user, null, null, null);
        rotating.session().accessExpiresAt = java.time.Instant.now().minusSeconds(1);
        assertTrue(service.authenticate(rotating.accessToken()).isEmpty());
    }

    private static ExtensionSessionService configuredService() {
        ExtensionSessionService service = new ExtensionSessionService();
        service.repository = new InMemoryExtensionSessionRepository();
        service.extensionSecurityConfig = new ExtensionSecurityConfig() {
            @Override public Duration accessTokenTtl() { return Duration.ofHours(1); }
            @Override public Duration refreshTokenTtl() { return Duration.ofDays(30); }
        };
        return service;
    }

    static final class InMemoryExtensionSessionRepository extends ExtensionSessionRepository {
        final Map<Long, ExtensionSession> byId = new HashMap<>();
        long nextId = 1L;

        @Override
        public void persist(ExtensionSession entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            byId.put(entity.id, entity);
        }

        @Override
        public Optional<ExtensionSession> findByTokenHash(String tokenHash) {
            return byId.values().stream().filter(session -> tokenHash.equals(session.tokenHash)).findFirst();
        }

        @Override
        public Optional<ExtensionSession> findByRefreshTokenHash(String refreshTokenHash) {
            return byId.values().stream()
                    .filter(session -> refreshTokenHash.equals(session.refreshTokenHash))
                    .findFirst();
        }

        @Override
        public List<ExtensionSession> listByUserId(Long userId) {
            return byId.values().stream()
                    .filter(session -> userId.equals(session.userId))
                    .sorted((left, right) -> right.createdAt.compareTo(left.createdAt))
                    .toList();
        }

        @Override
        public Optional<ExtensionSession> findByIdAndUserId(Long id, Long userId) {
            return Optional.ofNullable(byId.get(id)).filter(session -> userId.equals(session.userId));
        }

        @Override
        public Optional<ExtensionSession> findByIdOptional(Long id) {
            return Optional.ofNullable(byId.get(id));
        }
    }
}
