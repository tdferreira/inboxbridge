package dev.inboxbridge.service.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.RemoteSessionRepository;
import dev.inboxbridge.persistence.UserSession;

class RemoteSessionServiceTest {

    @Test
    void createSessionHashesTokensAndNormalizesClientMetadata() {
        InMemoryRemoteSessionRepository repository = new InMemoryRemoteSessionRepository();
        RemoteSessionService service = configuredService(repository);
        AppUser user = user(7L, "alice");

        RemoteSessionService.CreatedRemoteSession created = service.createSession(
                user,
                " 203.0.113.5 ",
                " Lisbon ",
                "x".repeat(600),
                null);

        assertNotNull(created.sessionToken());
        assertNotNull(created.csrfToken());
        assertEquals(user.id, created.session().userId);
        assertEquals(Duration.ofHours(12), Duration.between(created.session().createdAt, created.session().expiresAt));
        assertEquals("203.0.113.5", created.session().clientIp);
        assertEquals("Lisbon", created.session().locationLabel);
        assertEquals(512, created.session().userAgent.length());
        assertEquals(UserSession.LoginMethod.PASSWORD, created.session().loginMethod);
        assertNotEquals(created.sessionToken(), created.session().tokenHash);
        assertNotEquals(created.csrfToken(), created.session().csrfTokenHash);
    }

    @Test
    void findValidSessionRefreshesLastSeenAndRejectsBlankExpiredOrRevokedTokens() {
        InMemoryRemoteSessionRepository repository = new InMemoryRemoteSessionRepository();
        RemoteSessionService service = configuredService(repository);
        AppUser user = user(7L, "alice");

        RemoteSessionService.CreatedRemoteSession created = service.createSession(
                user,
                null,
                null,
                null,
                UserSession.LoginMethod.PASSKEY);

        Instant firstSeen = created.session().lastSeenAt;
        Optional<RemoteSession> found = service.findValidSession(created.sessionToken());
        assertTrue(found.isPresent());
        assertTrue(!found.orElseThrow().lastSeenAt.isBefore(firstSeen));

        assertTrue(service.findValidSession(" ").isEmpty());

        created.session().revokedAt = Instant.now();
        assertTrue(service.findValidSession(created.sessionToken()).isEmpty());

        RemoteSessionService.CreatedRemoteSession expired = service.createSession(user, null, null, null, null);
        expired.session().revokedAt = null;
        expired.session().expiresAt = Instant.now().minusSeconds(1);
        assertTrue(service.findValidSession(expired.sessionToken()).isEmpty());
    }

    @Test
    void validatesCsrfTokensAndSupportsExplicitInvalidation() {
        InMemoryRemoteSessionRepository repository = new InMemoryRemoteSessionRepository();
        RemoteSessionService service = configuredService(repository);
        AppUser user = user(7L, "alice");

        RemoteSessionService.CreatedRemoteSession created = service.createSession(user, null, null, null, null);

        assertTrue(service.csrfMatches(created.session(), created.csrfToken()));
        assertTrue(!service.csrfMatches(created.session(), "wrong"));

        service.invalidate(created.sessionToken());

        assertNotNull(created.session().revokedAt);
    }

    @Test
    void recordsFormattedDeviceLocationAndValidatesCoordinates() {
        InMemoryRemoteSessionRepository repository = new InMemoryRemoteSessionRepository();
        RemoteSessionService service = configuredService(repository);
        AppUser user = user(7L, "alice");

        RemoteSessionService.CreatedRemoteSession created = service.createSession(user, null, null, null, null);

        service.recordDeviceLocation(created.session().id, 38.7223d, -9.1393d, 12.7d);

        assertEquals("38.7223, -9.1393 (\u00b113 m)", created.session().deviceLocationLabel);
        assertNotNull(created.session().deviceLocationCapturedAt);

        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceLocation(created.session().id, null, -9.1d, 10d));
        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceLocation(created.session().id, 95d, -9.1d, 10d));
        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceLocation(created.session().id, 38.7d, -181d, 10d));
        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceLocation(created.session().id, 38.7d, -9.1d, -1d));
        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceLocation(999L, 38.7d, -9.1d, 10d));
    }

    @Test
    void invalidatesSpecificAndOtherSessionsForTheUser() {
        InMemoryRemoteSessionRepository repository = new InMemoryRemoteSessionRepository();
        RemoteSessionService service = configuredService(repository);
        AppUser alice = user(7L, "alice");
        AppUser bob = user(8L, "bob");

        RemoteSession aliceOne = service.createSession(alice, null, null, null, null).session();
        RemoteSession aliceTwo = service.createSession(alice, null, null, null, null).session();
        RemoteSession bobSession = service.createSession(bob, null, null, null, null).session();

        service.invalidateSessionForUser(alice.id, aliceOne.id);
        assertNotNull(aliceOne.revokedAt);

        service.invalidateOtherSessions(alice.id);

        assertNotNull(aliceTwo.revokedAt);
        assertTrue(bobSession.revokedAt == null);
        assertThrows(IllegalArgumentException.class, () -> service.invalidateSessionForUser(alice.id, bobSession.id));
    }

    private static RemoteSessionService configuredService(InMemoryRemoteSessionRepository repository) {
        RemoteSessionService service = new RemoteSessionService();
        service.repository = repository;
        service.inboxBridgeConfig = new TestConfig(Duration.ofHours(12));
        return service;
    }

    private static AppUser user(Long id, String username) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = username;
        user.active = true;
        user.approved = true;
        user.role = AppUser.Role.USER;
        return user;
    }

    private static final class InMemoryRemoteSessionRepository extends RemoteSessionRepository {
        private final List<RemoteSession> sessions = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public void persist(RemoteSession session) {
            if (session.id == null) {
                session.id = sequence++;
            }
            sessions.removeIf(existing -> existing.id.equals(session.id));
            sessions.add(session);
        }

        @Override
        public Optional<RemoteSession> findByTokenHash(String tokenHash) {
            return sessions.stream().filter(session -> session.tokenHash.equals(tokenHash)).findFirst();
        }

        @Override
        public Optional<RemoteSession> findByIdForUser(Long sessionId, Long userId) {
            return sessions.stream().filter(session -> session.id.equals(sessionId) && session.userId.equals(userId)).findFirst();
        }

        @Override
        public Optional<RemoteSession> findByIdOptional(Long id) {
            return sessions.stream().filter(session -> session.id.equals(id)).findFirst();
        }

        @Override
        public List<RemoteSession> listRecentByUserId(Long userId, int limit) {
            return sessions.stream().filter(session -> session.userId.equals(userId)).limit(limit).toList();
        }

        @Override
        public List<RemoteSession> listActiveByUserId(Long userId, Instant now) {
            return sessions.stream()
                    .filter(session -> session.userId.equals(userId))
                    .filter(session -> session.revokedAt == null && session.expiresAt.isAfter(now))
                    .toList();
        }

        @Override
        public void revokeOtherByUserId(Long userId, Instant revokedAt) {
            sessions.stream()
                    .filter(session -> session.userId.equals(userId) && session.revokedAt == null)
                    .forEach(session -> session.revokedAt = revokedAt);
        }
    }

    private static final class TestConfig implements InboxBridgeConfig {
        private final Duration remoteSessionTtl;

        private TestConfig(Duration remoteSessionTtl) {
            this.remoteSessionTtl = remoteSessionTtl;
        }

        @Override public boolean pollEnabled() { return true; }
        @Override public String pollInterval() { return "5m"; }
        @Override public int fetchWindow() { return 50; }
        @Override public Duration sourceHostMinSpacing() { return Duration.ofSeconds(1); }
        @Override public int sourceHostMaxConcurrency() { return 2; }
        @Override public Duration destinationProviderMinSpacing() { return Duration.ofMillis(250); }
        @Override public int destinationProviderMaxConcurrency() { return 1; }
        @Override public Duration throttleLeaseTtl() { return Duration.ofMinutes(2); }
        @Override public int adaptiveThrottleMaxMultiplier() { return 6; }
        @Override public double successJitterRatio() { return 0.2d; }
        @Override public Duration maxSuccessJitter() { return Duration.ofSeconds(30); }
        @Override public boolean multiUserEnabled() { return true; }
        @Override public Gmail gmail() { return null; }
        @Override public Microsoft microsoft() { return null; }
        @Override public List<Source> sources() { return List.of(); }

        @Override
        public Security security() {
            return new Security() {
                @Override public Auth auth() { return null; }
                @Override public Passkeys passkeys() { return null; }
                @Override
                public Remote remote() {
                    return new Remote() {
                        @Override public boolean enabled() { return true; }
                        @Override public Duration sessionTtl() { return remoteSessionTtl; }
                        @Override public int pollRateLimitCount() { return 60; }
                        @Override public Duration pollRateLimitWindow() { return Duration.ofMinutes(1); }
                        @Override public Optional<String> serviceToken() { return Optional.empty(); }
                        @Override public Optional<String> serviceUsername() { return Optional.empty(); }
                    };
                }
            };
        }
    }
}
