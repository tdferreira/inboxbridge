package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        service.appUserService = fakeUsers("alice@example.com", "secret-123", true, false);
        service.userSessionService = new FakeUserSessionService();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login("alice@example.com", "secret-123"));

        assertEquals("Invalid username or password", error.getMessage());
    }

    @Test
    void requireAuthenticatedUserRejectsSessionForInactiveUser() {
        AuthService service = new AuthService();
        service.appUserService = fakeUsers("alice@example.com", "secret-123", false, true);
        service.userSessionService = new FakeUserSessionService();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireAuthenticatedUser("session-1"));

        assertEquals("Not authenticated", error.getMessage());
    }

    private AppUserService fakeUsers(String username, String password, boolean active, boolean approved) {
        PasswordHashService passwordHashService = new PasswordHashService();
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = username;
        user.passwordHash = passwordHashService.hash(password);
        user.role = AppUser.Role.USER;
        user.active = active;
        user.approved = approved;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;

        return new AppUserService() {
            {
                this.passwordHashService = passwordHashService;
            }

            @Override
            public Optional<AppUser> findByUsername(String candidate) {
                return username.equals(candidate) ? Optional.of(user) : Optional.empty();
            }

            @Override
            public Optional<AppUser> findById(Long id) {
                return user.id.equals(id) ? Optional.of(user) : Optional.empty();
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
}
