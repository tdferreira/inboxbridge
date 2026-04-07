package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserPasskey;
import dev.inboxbridge.persistence.UserPasskeyRepository;
import dev.inboxbridge.service.admin.AppUserService;

class PasskeyServiceTest {

    @Test
    void cannotRemoveLastPasskeyFromPasswordlessAccount() {
        PasskeyService service = new PasskeyService();
        service.appUserService = fakeUsers(false);
        service.userPasskeyRepository = new InMemoryUserPasskeyRepository(1L, 1);

        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "alice";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteForUser(user, 1L));

        assertEquals("You cannot remove the only passkey from a passwordless account.", error.getMessage());
    }

    @Test
    void canRemovePasskeyWhenPasswordStillExists() {
        PasskeyService service = new PasskeyService();
        service.appUserService = fakeUsers(true);
        InMemoryUserPasskeyRepository repository = new InMemoryUserPasskeyRepository(1L, 1);
        service.userPasskeyRepository = repository;

        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "alice";

        assertDoesNotThrow(() -> service.deleteForUser(user, 1L));
        assertEquals(true, repository.passkey.deleted);
    }

    private AppUserService fakeUsers(boolean passwordConfigured) {
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "alice";
        user.passwordHash = passwordConfigured ? "hash" : null;
        user.active = true;
        user.approved = true;
        user.role = AppUser.Role.USER;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;

        return new AppUserService() {
            @Override
            public Optional<AppUser> findById(Long id) {
                return user.id.equals(id) ? Optional.of(user) : Optional.empty();
            }

            @Override
            public boolean hasPassword(AppUser candidate) {
                return candidate.passwordHash != null && !candidate.passwordHash.isBlank();
            }
        };
    }

    private static final class InMemoryUserPasskeyRepository extends UserPasskeyRepository {
        private final TestUserPasskey passkey = new TestUserPasskey();
        private final long count;

        private InMemoryUserPasskeyRepository(Long userId, long count) {
            this.count = count;
            this.passkey.id = 1L;
            this.passkey.userId = userId;
            this.passkey.label = "Laptop";
        }

        @Override
        public Optional<UserPasskey> findByIdOptional(Long id) {
            return Long.valueOf(1L).equals(id) ? Optional.of(passkey) : Optional.empty();
        }

        @Override
        public long countByUserId(Long userId) {
            return count;
        }
    }

    private static final class TestUserPasskey extends UserPasskey {
        private boolean deleted;

        @Override
        public void delete() {
            deleted = true;
        }
    }
}
