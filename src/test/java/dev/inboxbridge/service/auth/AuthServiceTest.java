package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.service.admin.AppUserService;

class AuthServiceTest {

    @Test
    void loginRejectsUnapprovedUsers() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", true, false, 0);
        service.userSessionService = new FakeUserSessionService();
        service.geoIpLocationService = new FakeGeoIpLocationService(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login("alice@example.com", "secret-123", "203.0.113.5", "JUnit"));

        assertEquals("Invalid username or password", error.getMessage());
    }

    @Test
    void requireAuthenticatedUserRejectsSessionForInactiveUser() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", false, true, 0);
        service.userSessionService = new FakeUserSessionService();
        service.geoIpLocationService = new FakeGeoIpLocationService(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireAuthenticatedUser("session-1"));

        assertEquals("Not authenticated", error.getMessage());
    }

    @Test
    void loginRejectsPasswordWhenAccountHasPasskey() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", true, true, 1);
        service.userSessionService = new FakeUserSessionService();
        service.passkeyService = new FakePasskeyService();
        service.geoIpLocationService = new FakeGeoIpLocationService(null);

        AuthService.LoginResult result = service.login("alice@example.com", "secret-123", "203.0.113.5", "JUnit");

        assertEquals(AuthService.LoginStatus.PASSKEY_REQUIRED, result.status());
        assertNotNull(result.passkeyChallenge());
    }

    @Test
    void loginRequestsPasskeyForPasswordlessPasskeyAccount() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", null, true, true, 1);
        service.userSessionService = new FakeUserSessionService();
        service.passkeyService = new FakePasskeyService();
        service.geoIpLocationService = new FakeGeoIpLocationService(null);

        AuthService.LoginResult result = service.login("alice@example.com", "anything", "203.0.113.5", "JUnit");

        assertEquals(AuthService.LoginStatus.PASSKEY_REQUIRED, result.status());
        assertNotNull(result.passkeyChallenge());
    }

    @Test
    void loginStoresPasswordSessionMetadata() {
        AuthService service = new AuthService();
        FakeUserSessionService sessionService = new FakeUserSessionService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", true, true, 0);
        service.userSessionService = sessionService;
        service.geoIpLocationService = new FakeGeoIpLocationService("Lisbon, Portugal");

        AuthService.LoginResult result = service.login("alice@example.com", "secret-123", "203.0.113.8", "JUnit Browser");

        assertEquals(AuthService.LoginStatus.AUTHENTICATED, result.status());
        assertEquals("203.0.113.8", sessionService.lastClientIp);
        assertEquals("Lisbon, Portugal", sessionService.lastLocationLabel);
        assertEquals("JUnit Browser", sessionService.lastUserAgent);
        assertEquals(UserSession.LoginMethod.PASSWORD, sessionService.lastLoginMethod);
    }

    @Test
    void passkeyLoginStoresCombinedMethodWhenPasswordWasVerified() {
        AuthService service = new AuthService();
        FakeUserSessionService sessionService = new FakeUserSessionService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", true, true, 1);
        service.userSessionService = sessionService;
        service.geoIpLocationService = new FakeGeoIpLocationService("Paris, France");

        AppUser user = service.appUserService.findByUsername("alice@example.com").orElseThrow();
        AuthService.AuthenticatedSession session = service.loginWithPasskey(
                new PasskeyService.PasskeyAuthenticationResult(user, true),
                "198.51.100.7",
                "JUnit Browser");

        assertEquals("session-1", session.token());
        assertEquals("csrf-1", session.csrfToken());
        assertEquals("Paris, France", sessionService.lastLocationLabel);
        assertEquals(UserSession.LoginMethod.PASSWORD_PLUS_PASSKEY, sessionService.lastLoginMethod);
        assertTrue(sessionService.lastUserAgent.contains("JUnit"));
    }

    private AppUserService fakeUsers(String username, String password, boolean active, boolean approved, long passkeyCount) {
        PasswordHashService hasher = new PasswordHashService();
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = username;
        user.passwordHash = password == null ? null : hasher.hash(password);
        user.role = AppUser.Role.USER;
        user.active = active;
        user.approved = approved;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;

        return new AppUserService() {
            @Override
            public Optional<AppUser> findByUsername(String candidate) {
                return username.equals(candidate) ? Optional.of(user) : Optional.empty();
            }

            @Override
            public Optional<AppUser> findById(Long id) {
                return user.id.equals(id) ? Optional.of(user) : Optional.empty();
            }

            @Override
            public boolean requiresPasskey(AppUser candidate) {
                return passkeyCount > 0;
            }

            @Override
            public boolean hasPassword(AppUser candidate) {
                return candidate.passwordHash != null && !candidate.passwordHash.isBlank();
            }

            @Override
            public boolean passwordMatches(AppUser candidate, String rawPassword) {
                return hasPassword(candidate) && rawPassword != null && hasher.matches(rawPassword, candidate.passwordHash);
            }
        };
    }

    private static final class FakeUserSessionService extends UserSessionService {
        private String lastClientIp;
        private String lastLocationLabel;
        private String lastUserAgent;
        private UserSession.LoginMethod lastLoginMethod;

        @Override
        public CreatedUserSession createSession(AppUser user, String clientIp, String locationLabel, String userAgent, UserSession.LoginMethod loginMethod) {
            this.lastClientIp = clientIp;
            this.lastLocationLabel = locationLabel;
            this.lastUserAgent = userAgent;
            this.lastLoginMethod = loginMethod;
            return new CreatedUserSession("session-1", "csrf-1", new UserSession());
        }

        @Override
        public Optional<UserSession> findValidSession(String token) {
            UserSession session = new UserSession();
            session.userId = 1L;
            return Optional.of(session);
        }
    }

    private static final class FakePasskeyService extends PasskeyService {
        @Override
        public dev.inboxbridge.dto.StartPasskeyCeremonyResponse startAuthenticationForUser(AppUser user, boolean passwordVerified) {
            return new dev.inboxbridge.dto.StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}");
        }
    }

    private static final class FakeGeoIpLocationService extends GeoIpLocationService {
        private final String locationLabel;

        private FakeGeoIpLocationService(String locationLabel) {
            this.locationLabel = locationLabel;
        }

        @Override
        public Optional<String> resolveLocation(String clientIp) {
            return Optional.ofNullable(locationLabel);
        }
    }
}
