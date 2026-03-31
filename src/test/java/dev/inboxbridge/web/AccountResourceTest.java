package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ChangePasswordRequest;
import dev.inboxbridge.dto.RemovePasswordRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.RemoteSessionService;
import dev.inboxbridge.service.UserGmailConfigService;
import jakarta.ws.rs.BadRequestException;

class AccountResourceTest {

    @Test
    void changePasswordRequiresMatchingConfirmation() {
        AccountResource resource = new AccountResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "alice";
        resource.currentUserContext.setUser(user);
        resource.appUserService = new FakeAppUserService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.changePassword(new ChangePasswordRequest("old-pass", "new-pass", "different-pass")));

        assertEquals("New password confirmation does not match", error.getMessage());
    }

    @Test
    void changePasswordSurfacesCurrentPasswordValidation() {
        AccountResource resource = new AccountResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 2L;
        user.username = "bob";
        resource.currentUserContext.setUser(user);
        resource.appUserService = new FakeAppUserService("Current password is incorrect");

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.changePassword(new ChangePasswordRequest("wrong", "Newpass#123", "Newpass#123")));

        assertEquals("Current password is incorrect", error.getMessage());
    }

    @Test
    void removePasswordSurfacesDomainValidation() {
        AccountResource resource = new AccountResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 3L;
        user.username = "charlie";
        resource.currentUserContext.setUser(user);
        resource.appUserService = new RemovePasswordErrorAppUserService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.removePassword(new RemovePasswordRequest("Current#123")));

        assertEquals("Register a passkey before removing the password.", error.getMessage());
    }

    @Test
    void unlinkGmailDelegatesToUserConfigService() {
        AccountResource resource = new AccountResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 4L;
        user.username = "dora";
        resource.currentUserContext.setUser(user);
        resource.userGmailConfigService = new UserGmailConfigService() {
            @Override
            public GmailUnlinkResult unlinkForUser(Long userId) {
                assertEquals(4L, userId);
                return new GmailUnlinkResult(true, true);
            }
        };

        UserGmailConfigService.GmailUnlinkResult result = resource.unlinkGmailAccount();

        assertEquals(true, result.providerRevocationAttempted());
        assertEquals(true, result.providerRevoked());
    }

    @Test
    void sessionsIncludeRemoteSessions() {
        AccountResource resource = new AccountResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 5L;
        user.username = "eve";
        resource.currentUserContext.setUser(user);
        resource.userSessionService = new FakeAppUserSessionService();
        resource.remoteSessionService = new FakeRemoteSessionService();
        resource.geoIpLocationService = new FakeGeoIpLocationService(true);

        var response = resource.sessions();

        assertEquals(2, response.activeSessions().size());
        assertEquals("REMOTE", response.activeSessions().get(0).sessionType());
        assertEquals("38.7223, -9.1393 (±25 m)", response.activeSessions().get(0).deviceLocationLabel());
        assertEquals(38.7223, response.activeSessions().get(0).deviceLatitude());
        assertEquals(-9.1393, response.activeSessions().get(0).deviceLongitude());
    }

    private static final class FakeAppUserService extends AppUserService {
        private final String errorMessage;

        private FakeAppUserService() {
            this(null);
        }

        private FakeAppUserService(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public void changePassword(AppUser user, String currentPassword, String newPassword, String confirmNewPassword) {
            if (errorMessage != null) {
                throw new IllegalArgumentException(errorMessage);
            }
            if (!newPassword.equals(confirmNewPassword)) {
                throw new IllegalArgumentException("New password confirmation does not match");
            }
        }
    }

    private static final class RemovePasswordErrorAppUserService extends AppUserService {
        @Override
        public void removePassword(AppUser user, String currentPassword) {
            throw new IllegalArgumentException("Register a passkey before removing the password.");
        }
    }

    private static final class FakeAppUserSessionService extends dev.inboxbridge.service.UserSessionService {
        @Override
        public java.util.List<dev.inboxbridge.persistence.UserSession> listRecentSessions(Long userId, int limit) {
            dev.inboxbridge.persistence.UserSession session = new dev.inboxbridge.persistence.UserSession();
            session.id = 21L;
            session.userId = userId;
            session.createdAt = java.time.Instant.parse("2026-03-31T09:00:00Z");
            session.lastSeenAt = java.time.Instant.parse("2026-03-31T09:05:00Z");
            session.expiresAt = java.time.Instant.now().plusSeconds(3600);
            session.deviceLocationLabel = "38.7223, -9.1393 (±25 m)";
            session.deviceLatitude = 38.7223;
            session.deviceLongitude = -9.1393;
            session.deviceLocationCapturedAt = java.time.Instant.parse("2026-03-31T09:03:00Z");
            return java.util.List.of(session);
        }

        @Override
        public java.util.List<dev.inboxbridge.persistence.UserSession> listActiveSessions(Long userId) {
            return listRecentSessions(userId, 5);
        }
    }

    private static final class FakeRemoteSessionService extends RemoteSessionService {
        @Override
        public java.util.List<RemoteSession> listRecentSessions(Long userId, int limit) {
            RemoteSession session = new RemoteSession();
            session.id = 22L;
            session.userId = userId;
            session.createdAt = java.time.Instant.parse("2026-03-31T10:00:00Z");
            session.lastSeenAt = java.time.Instant.parse("2026-03-31T10:05:00Z");
            session.expiresAt = java.time.Instant.now().plusSeconds(7200);
            session.deviceLocationLabel = "38.7223, -9.1393 (±25 m)";
            session.deviceLatitude = 38.7223;
            session.deviceLongitude = -9.1393;
            session.deviceLocationCapturedAt = java.time.Instant.parse("2026-03-31T10:03:00Z");
            return java.util.List.of(session);
        }

        @Override
        public java.util.List<RemoteSession> listActiveSessions(Long userId) {
            return listRecentSessions(userId, 5);
        }
    }

    private static final class FakeGeoIpLocationService extends dev.inboxbridge.service.GeoIpLocationService {
        private final boolean configured;

        private FakeGeoIpLocationService(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }
}
