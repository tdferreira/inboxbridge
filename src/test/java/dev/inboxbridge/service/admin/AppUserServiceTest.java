package dev.inboxbridge.service.admin;

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
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SourcePollEventRepository;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserPasskeyRepository;
import dev.inboxbridge.persistence.UserPollingSettingRepository;
import dev.inboxbridge.persistence.UserSessionRepository;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;
import dev.inboxbridge.service.auth.PasswordHashService;
import dev.inboxbridge.service.auth.UserSessionService;
import dev.inboxbridge.service.oauth.OAuthCredentialService;

class AppUserServiceTest {

    @Test
    void registerUserCreatesPendingInactiveAccount() {
        AppUserService service = service();

        AppUser user = service.registerUser(new RegisterUserRequest("alice@example.com", "Secret#123", "Secret#123", ""));

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
    void createUserRejectsDuplicateUsername() {
        AppUserService service = service();
        service.createUser(new CreateUserRequest("bob@example.com", "Secret#123", "USER"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createUser(new CreateUserRequest("bob@example.com", "Secret#123", "USER")));

        assertEquals("Username already exists", error.getMessage());
    }

    @Test
    void cannotRemoveLastApprovedAdmin() {
        AppUserService service = service();
        AppUser admin = service.createUser(new CreateUserRequest("admin2@example.com", "Secret#123", "ADMIN"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateUser(null, admin.id, new UpdateUserRequest("USER", null, null, null)));

        assertEquals("At least one approved active admin must remain.", error.getMessage());
    }

    @Test
    void adminCannotDemoteSelfEvenWhenAnotherAdminExists() {
        AppUserService service = service();
        AppUser admin = service.createUser(new CreateUserRequest("owner@example.com", "Secret#123", "ADMIN"));
        service.createUser(new CreateUserRequest("backup@example.com", "Secret#123", "ADMIN"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateUser(admin, admin.id, new UpdateUserRequest("USER", null, null, null)));

        assertEquals("Admins cannot remove their own admin rights.", error.getMessage());
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

    @Test
    void registerUserRequiresMatchingPasswordConfirmation() {
        AppUserService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerUser(new RegisterUserRequest("eve@example.com", "Secret#123", "Other#123", "")));

        assertEquals("New password confirmation does not match", error.getMessage());
    }

    @Test
    void removePasswordRequiresPasskey() {
        AppUserService service = service();
        AppUser user = service.createUser(new CreateUserRequest("frank@example.com", "Secret#123", "USER"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.removePassword(user, "Secret#123"));

        assertEquals("Register a passkey before removing the password.", error.getMessage());
    }

    @Test
    void removePasswordAllowsPasskeyOnlyAccount() {
        AppUserService service = service(1);
        AppUser user = service.createUser(new CreateUserRequest("grace@example.com", "Secret#123", "USER"));

        service.removePassword(user, "Secret#123");

        AppUser reloaded = service.findById(user.id).orElseThrow();
        assertFalse(service.hasPassword(reloaded));
        assertFalse(reloaded.mustChangePassword);
    }

    @Test
    void changePasswordAllowsPasswordlessAccountToSetNewPassword() {
        AppUserService service = service(1);
        AppUser user = service.createUser(new CreateUserRequest("heidi@example.com", "Secret#123", "USER"));
        service.removePassword(user, "Secret#123");

        service.changePassword(user, "", "Better#456", "Better#456");

        AppUser reloaded = service.findById(user.id).orElseThrow();
        assertTrue(service.passwordMatches(reloaded, "Better#456"));
        assertFalse(reloaded.mustChangePassword);
    }

    @Test
    void removePasswordRequiresCurrentPasswordConfirmation() {
        AppUserService service = service(1);
        AppUser user = service.createUser(new CreateUserRequest("ivan@example.com", "Secret#123", "USER"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.removePassword(user, "Wrong#123"));

        assertEquals("Current password is incorrect", error.getMessage());
    }

    @Test
    void switchToSingleUserModeDisablesOtherAccountsButKeepsActingAdmin() {
        AppUserService service = service();
        AppUser actor = service.createUser(new CreateUserRequest("owner@example.com", "Secret#123", "ADMIN"));
        AppUser activeUser = service.createUser(new CreateUserRequest("alice@example.com", "Secret#123", "USER"));
        AppUser inactiveUser = service.createUser(new CreateUserRequest("bob@example.com", "Secret#123", "USER"));
        inactiveUser.active = false;

        service.switchToSingleUserMode(actor);

        assertTrue(service.findById(actor.id).orElseThrow().active);
        assertFalse(service.findById(actor.id).orElseThrow().disabledBySingleUserMode);
        assertFalse(service.findById(activeUser.id).orElseThrow().active);
        assertTrue(service.findById(activeUser.id).orElseThrow().disabledBySingleUserMode);
        assertFalse(service.findById(inactiveUser.id).orElseThrow().active);
        assertFalse(service.findById(inactiveUser.id).orElseThrow().disabledBySingleUserMode);
    }

    @Test
    void switchToMultiUserModeReactivatesOnlyAccountsDisabledBySingleUserMode() {
        AppUserService service = service();
        AppUser actor = service.createUser(new CreateUserRequest("owner@example.com", "Secret#123", "ADMIN"));
        AppUser activeUser = service.createUser(new CreateUserRequest("alice@example.com", "Secret#123", "USER"));
        AppUser inactiveUser = service.createUser(new CreateUserRequest("bob@example.com", "Secret#123", "USER"));
        inactiveUser.active = false;

        service.switchToSingleUserMode(actor);
        service.switchToMultiUserMode();

        assertTrue(service.findById(activeUser.id).orElseThrow().active);
        assertFalse(service.findById(activeUser.id).orElseThrow().disabledBySingleUserMode);
        assertFalse(service.findById(inactiveUser.id).orElseThrow().active);
        assertFalse(service.findById(inactiveUser.id).orElseThrow().disabledBySingleUserMode);
    }

    @Test
    void deleteUserRejectsDeletingSelf() {
        AppUserService service = service();
        AppUser actor = service.createUser(new CreateUserRequest("owner@example.com", "Secret#123", "ADMIN"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteUser(actor, actor.id));

        assertEquals("Admins cannot delete their own account.", error.getMessage());
    }

    private AppUserService service() {
        return service(0);
    }

    private AppUserService service(long passkeyCount) {
        AppUserService service = new AppUserService();
        service.repository = new InMemoryAppUserRepository();
        service.passwordHashService = new PasswordHashService();
        service.userEmailAccountRepository = new EmptyUserEmailAccountRepository();
        service.userGmailConfigRepository = new EmptyUserGmailConfigRepository();
        service.userPasskeyRepository = new FixedPasskeyRepository(passkeyCount);
        service.userPollingSettingRepository = new EmptyUserPollingSettingRepository();
        service.userUiPreferenceRepository = new EmptyUserUiPreferenceRepository();
        service.sourcePollingSettingRepository = new EmptySourcePollingSettingRepository();
        service.sourcePollingStateRepository = new EmptySourcePollingStateRepository();
        service.sourcePollEventRepository = new EmptySourcePollEventRepository();
        service.importedMessageRepository = new EmptyImportedMessageRepository();
        service.oAuthCredentialService = new OAuthCredentialService();
        service.oAuthCredentialService.setRepository(new InMemoryOAuthCredentialRepository());
        service.userSessionService = new UserSessionService();
        service.userSessionService.setRepository(new InMemoryUserSessionRepository());
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

        @Override
        public void delete(AppUser entity) {
            users.remove(entity);
        }

        @Override
        public List<AppUser> listDisabledBySingleUserMode() {
            return users.stream().filter(user -> user.disabledBySingleUserMode).toList();
        }
    }

    private static class EmptyUserEmailAccountRepository extends UserEmailAccountRepository {
        @Override
        public List<dev.inboxbridge.persistence.UserEmailAccount> listByUserId(Long userId) {
            return List.of();
        }
    }

    private static class EmptyUserGmailConfigRepository extends UserGmailConfigRepository {
        @Override
        public Optional<dev.inboxbridge.persistence.UserGmailConfig> findByUserId(Long userId) {
            return Optional.empty();
        }

        @Override
        public long deleteByUserId(Long userId) {
            return 0;
        }
    }

    private static class FixedPasskeyRepository extends UserPasskeyRepository {
        private final long count;

        private FixedPasskeyRepository(long count) {
            this.count = count;
        }

        @Override
        public long countByUserId(Long userId) {
            return count;
        }

        @Override
        public long deleteByUserId(Long userId) {
            return 0;
        }
    }

    private static class EmptyUserPollingSettingRepository extends UserPollingSettingRepository {
        @Override
        public long deleteByUserId(Long userId) {
            return 0;
        }
    }

    private static class EmptyUserUiPreferenceRepository extends UserUiPreferenceRepository {
        @Override
        public long deleteByUserId(Long userId) {
            return 0;
        }
    }

    private static class EmptySourcePollingSettingRepository extends SourcePollingSettingRepository {
        @Override
        public long deleteBySourceIds(List<String> sourceIds) {
            return 0;
        }
    }

    private static class EmptySourcePollingStateRepository extends SourcePollingStateRepository {
        @Override
        public long deleteBySourceIds(List<String> sourceIds) {
            return 0;
        }
    }

    private static class EmptySourcePollEventRepository extends SourcePollEventRepository {
        @Override
        public long deleteBySourceIds(List<String> sourceIds) {
            return 0;
        }
    }

    private static class EmptyImportedMessageRepository extends ImportedMessageRepository {
        @Override
        public long deleteBySourceAccountIds(List<String> sourceAccountIds) {
            return 0;
        }
    }

    private static class InMemoryOAuthCredentialRepository extends OAuthCredentialRepository {
        private final List<OAuthCredential> credentials = new ArrayList<>();

        @Override
        public Optional<OAuthCredential> findByProviderAndSubject(String provider, String subjectKey) {
            return credentials.stream()
                    .filter(credential -> provider.equals(credential.provider) && subjectKey.equals(credential.subjectKey))
                    .findFirst();
        }

        @Override
        public long deleteByProviderAndSubjects(String provider, List<String> subjectKeys) {
            long before = credentials.size();
            credentials.removeIf(credential -> provider.equals(credential.provider) && subjectKeys.contains(credential.subjectKey));
            return before - credentials.size();
        }
    }

    private static class InMemoryUserSessionRepository extends UserSessionRepository {
        @Override
        public void deleteExpiredSessions() {
        }

        @Override
        public void deleteByUserId(Long userId) {
        }
    }
}
