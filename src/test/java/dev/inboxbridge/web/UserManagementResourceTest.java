package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminResetPasswordRequest;
import dev.inboxbridge.dto.UpdateUserRequest;
import dev.inboxbridge.dto.UserSummaryResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.ApplicationModeService;
import dev.inboxbridge.service.PasskeyService;
import dev.inboxbridge.service.UserBridgeService;
import dev.inboxbridge.service.UserGmailConfigService;
import dev.inboxbridge.service.UserPollingSettingsService;
import jakarta.ws.rs.BadRequestException;

class UserManagementResourceTest {

    @Test
    void updateUserSurfacesSelfAdminProtection() {
        UserManagementResource resource = resource();
        resource.appUserService = new FakeAppUserService("Admins cannot remove their own admin rights.");

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.updateUser(7L, new UpdateUserRequest("USER", null, null, null)));

        assertEquals("Admins cannot remove their own admin rights.", error.getMessage());
    }

    @Test
    void resetPasskeysReturnsDeletedCount() {
        UserManagementResource resource = resource();
        resource.passkeyService = new FakePasskeyService();

        java.util.Map<String, Object> payload = resource.resetPasskeys(7L);

        assertEquals(3L, payload.get("deleted"));
    }

    @Test
    void resetPasswordDelegatesToService() {
        UserManagementResource resource = resource();

        UserSummaryResponse summary = resource.resetPassword(7L, new AdminResetPasswordRequest("TempPass#123", "TempPass#123"));

        assertEquals(7L, summary.id());
        assertEquals("alice@example.com", summary.username());
    }

    @Test
    void listUsersIsBlockedWhenMultiUserModeIsDisabled() {
        UserManagementResource resource = resource();
        resource.applicationModeService = new FakeApplicationModeService(false);

        BadRequestException error = assertThrows(BadRequestException.class, resource::listUsers);

        assertEquals("Multi-user mode is disabled for this deployment.", error.getMessage());
    }

    private UserManagementResource resource() {
        UserManagementResource resource = new UserManagementResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser admin = new AppUser();
        admin.id = 1L;
        admin.username = "admin";
        admin.role = AppUser.Role.ADMIN;
        resource.currentUserContext.setUser(admin);
        resource.appUserService = new FakeAppUserService(null);
        resource.applicationModeService = new FakeApplicationModeService(true);
        resource.userGmailConfigService = new UserGmailConfigService();
        resource.userBridgeService = new UserBridgeService();
        resource.userPollingSettingsService = new UserPollingSettingsService();
        resource.passkeyService = new PasskeyService();
        return resource;
    }

    private static final class FakeAppUserService extends AppUserService {
        private final String updateError;

        private FakeAppUserService(String updateError) {
            this.updateError = updateError;
        }

        @Override
        public AppUser updateUser(AppUser actor, Long userId, UpdateUserRequest request) {
            if (updateError != null) {
                throw new IllegalArgumentException(updateError);
            }
            AppUser user = new AppUser();
            user.id = userId;
            user.username = "alice@example.com";
            user.role = AppUser.Role.USER;
            user.active = true;
            user.approved = true;
            return user;
        }

        @Override
        public AppUser adminResetPassword(AppUser actor, Long userId, AdminResetPasswordRequest request) {
            AppUser user = new AppUser();
            user.id = userId;
            user.username = "alice@example.com";
            user.role = AppUser.Role.USER;
            user.active = true;
            user.approved = true;
            user.mustChangePassword = true;
            return user;
        }

        @Override
        public UserSummaryResponse toSummary(AppUser user) {
            return new UserSummaryResponse(user.id, user.username, user.role.name(), user.active, user.approved, user.mustChangePassword, true, false, 0, 0);
        }

        @Override
        public Optional<AppUser> findById(Long id) {
            AppUser user = new AppUser();
            user.id = id;
            user.username = "alice@example.com";
            user.role = AppUser.Role.USER;
            user.active = true;
            user.approved = true;
            return Optional.of(user);
        }
    }

    private static final class FakePasskeyService extends PasskeyService {
        @Override
        public long resetForUser(Long userId) {
            return 3;
        }

        @Override
        public List<dev.inboxbridge.dto.PasskeyView> listForUser(Long userId) {
            return List.of();
        }
    }

    private static final class FakeApplicationModeService extends ApplicationModeService {
        private final boolean enabled;

        private FakeApplicationModeService(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean multiUserEnabled() {
            return enabled;
        }
    }
}
