package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ChangePasswordRequest;
import dev.inboxbridge.dto.RemovePasswordRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
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
}
