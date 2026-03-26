package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;

class AuthServiceTest {

    @Test
    void loginRejectsUnapprovedUsers() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", true, false, 0);
        service.userSessionService = new FakeUserSessionService();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login("alice@example.com", "secret-123"));

        assertEquals("Invalid username or password", error.getMessage());
    }

    @Test
    void requireAuthenticatedUserRejectsSessionForInactiveUser() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", false, true, 0);
        service.userSessionService = new FakeUserSessionService();

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

        AuthService.LoginResult result = service.login("alice@example.com", "secret-123");

        assertEquals(AuthService.LoginStatus.PASSKEY_REQUIRED, result.status());
        assertNotNull(result.passkeyChallenge());
    }

    @Test
    void loginRequestsPasskeyForPasswordlessPasskeyAccount() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", null, true, true, 1);
        service.userSessionService = new FakeUserSessionService();
        service.passkeyService = new FakePasskeyService();

        AuthService.LoginResult result = service.login("alice@example.com", "anything");

        assertEquals(AuthService.LoginStatus.PASSKEY_REQUIRED, result.status());
        assertNotNull(result.passkeyChallenge());
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
            {
                this.passwordHashService = hasher;
            }

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
        @Override
        public String createSession(AppUser user) {
            return "session-1";
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
}
