package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.service.PollingSettingsService;
import jakarta.ws.rs.BadRequestException;

class AdminResourceTest {

    @Test
    void pollingSettingsReturnsCurrentView() {
        AdminResource resource = new AdminResource();
        resource.pollingSettingsService = new FakePollingSettingsService();

        AdminPollingSettingsView response = resource.pollingSettings();

        assertEquals("5m", response.defaultPollInterval());
        assertEquals("3m", response.effectivePollInterval());
    }

    @Test
    void updatePollingSettingsSurfacesValidationErrors() {
        AdminResource resource = new AdminResource();
        resource.pollingSettingsService = new ErrorPollingSettingsService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.updatePollingSettings(new UpdateAdminPollingSettingsRequest(Boolean.TRUE, "1s", Integer.valueOf(10))));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    private static class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public AdminPollingSettingsView view() {
            return new AdminPollingSettingsView(true, Boolean.TRUE, true, "5m", "3m", "3m", 50, Integer.valueOf(25), 25);
        }
    }

    private static final class ErrorPollingSettingsService extends PollingSettingsService {
        @Override
        public AdminPollingSettingsView update(UpdateAdminPollingSettingsRequest request) {
            throw new IllegalArgumentException("Poll interval must be at least 5 seconds");
        }
    }
}
