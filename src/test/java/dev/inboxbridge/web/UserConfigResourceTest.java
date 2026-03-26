package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.dto.UserPollingSettingsView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.UserPollingSettingsService;
import jakarta.ws.rs.BadRequestException;

class UserConfigResourceTest {

    @Test
    void pollingSettingsReturnsUserView() {
        UserConfigResource resource = resource();
        resource.userPollingSettingsService = new FakeUserPollingSettingsService();

        UserPollingSettingsView response = resource.pollingSettings();

        assertEquals("3m", response.effectivePollInterval());
        assertEquals(25, response.effectiveFetchWindow());
    }

    @Test
    void pollingSettingsUpdateSurfacesValidationErrors() {
        UserConfigResource resource = resource();
        resource.userPollingSettingsService = new ErrorUserPollingSettingsService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.updatePollingSettings(new UpdateUserPollingSettingsRequest(Boolean.TRUE, "1s", Integer.valueOf(10))));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    private UserConfigResource resource() {
        UserConfigResource resource = new UserConfigResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";
        resource.currentUserContext.setUser(user);
        return resource;
    }

    private static final class FakeUserPollingSettingsService extends UserPollingSettingsService {
        @Override
        public Optional<UserPollingSettingsView> viewForUser(Long userId) {
            return Optional.of(defaultView(userId));
        }

        @Override
        public UserPollingSettingsView defaultView(Long userId) {
            return new UserPollingSettingsView(true, null, true, "5m", "3m", "3m", 50, Integer.valueOf(25), 25);
        }
    }

    private static final class ErrorUserPollingSettingsService extends UserPollingSettingsService {
        @Override
        public UserPollingSettingsView update(dev.inboxbridge.persistence.AppUser user, UpdateUserPollingSettingsRequest request) {
            throw new IllegalArgumentException("Poll interval must be at least 5 seconds");
        }
    }
}
