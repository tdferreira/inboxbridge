package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.CreateUserRequest;
import dev.inboxbridge.dto.RegisterUserRequest;
import dev.inboxbridge.dto.UpdateUserRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserBridgeRepository;
import dev.inboxbridge.persistence.UserGmailConfigRepository;

class AppUserServiceTest {

    @Test
    void registerUserCreatesPendingInactiveAccount() {
        AppUserService service = service();

        AppUser user = service.registerUser(new RegisterUserRequest("alice@example.com", "Secret#123"));

        assertEquals(AppUser.Role.USER, user.role);
        assertFalse(user.active);
        assertFalse(user.approved);
        assertFalse(user.mustChangePassword);
    }

    @Test
    void createUserCreatesApprovedActiveAccount() {
        AppUserService service = service();

        AppUser user = service.createUser(new CreateUserRequest("bob@example.com", "Secret#123", "ADMIN"));

        assertEquals(AppUser.Role.ADMIN, user.role);
        assertTrue(user.active);
        assertTrue(user.approved);
        assertTrue(user.mustChangePassword);
    }

    @Test
    void cannotRemoveLastApprovedAdmin() {
        AppUserService service = service();
        AppUser admin = service.createUser(new CreateUserRequest("admin2@example.com", "Secret#123", "ADMIN"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateUser(admin.id, new UpdateUserRequest("USER", null, null, null)));

        assertEquals("At least one approved active admin must remain.", error.getMessage());
    }

    @Test
    void changePasswordPersistsStrongerPasswordAndClearsResetFlag() {
        AppUserService service = service();
        AppUser user = service.createUser(new CreateUserRequest("carol@example.com", "Secret#123", "USER"));

        service.changePassword(user, "Secret#123", "Better#456", "Better#456");

        AppUser reloaded = service.findById(user.id).orElseThrow();
        assertTrue(service.passwordMatches(reloaded, "Better#456"));
        assertFalse(service.passwordMatches(reloaded, "Secret#123"));
        assertFalse(reloaded.mustChangePassword);
    }

    @Test
    void changePasswordRejectsWeakOrReusedPassword() {
        AppUserService service = service();
        AppUser user = service.createUser(new CreateUserRequest("dave@example.com", "Secret#123", "USER"));

        IllegalArgumentException reused = assertThrows(
                IllegalArgumentException.class,
                () -> service.changePassword(user, "Secret#123", "Secret#123", "Secret#123"));
        IllegalArgumentException weak = assertThrows(
                IllegalArgumentException.class,
                () -> service.changePassword(user, "Secret#123", "weakpass", "weakpass"));

        assertEquals("New password must be different from the current password", reused.getMessage());
        assertEquals("Password must contain at least one uppercase letter", weak.getMessage());
    }

    private AppUserService service() {
        AppUserService service = new AppUserService();
        service.repository = new InMemoryAppUserRepository();
        service.passwordHashService = new PasswordHashService();
        service.userBridgeRepository = new EmptyUserBridgeRepository();
        service.userGmailConfigRepository = new EmptyUserGmailConfigRepository();
        return service;
    }

    private static class InMemoryAppUserRepository extends AppUserRepository {
        private final List<AppUser> users = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<AppUser> findByUsername(String username) {
            return users.stream().filter(user -> user.username.equals(username)).findFirst();
        }

        @Override
        public Optional<AppUser> findByIdOptional(Long id) {
            return users.stream().filter(user -> user.id.equals(id)).findFirst();
        }

        @Override
        public List<AppUser> listAll() {
            return new ArrayList<>(users);
        }

        @Override
        public long count() {
            return users.size();
        }

        @Override
        public long countApprovedAdmins() {
            return users.stream()
                    .filter(user -> user.role == AppUser.Role.ADMIN && user.active && user.approved)
                    .count();
        }

        @Override
        public void persist(AppUser user) {
            if (user.id == null) {
                user.id = sequence++;
                users.add(user);
            }
        }
    }

    private static class EmptyUserBridgeRepository extends UserBridgeRepository {
        @Override
        public List<dev.inboxbridge.persistence.UserBridge> listByUserId(Long userId) {
            return List.of();
        }
    }

    private static class EmptyUserGmailConfigRepository extends UserGmailConfigRepository {
        @Override
        public Optional<dev.inboxbridge.persistence.UserGmailConfig> findByUserId(Long userId) {
            return Optional.empty();
        }
    }
}
