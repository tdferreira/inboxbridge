package dev.inboxbridge.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.UserUiNotificationView;
import dev.inboxbridge.dto.UserUiPreferenceView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserUiPreference;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;

class UserUiPreferenceServiceTest {

    @Test
    void defaultViewStartsWithCollapsedStateDisabled() {
        UserUiPreferenceService service = new UserUiPreferenceService();
        service.objectMapper = new ObjectMapper();

        UserUiPreferenceView view = service.defaultView();

        assertFalse(view.persistLayout());
        assertFalse(view.layoutEditEnabled());
        assertFalse(view.quickSetupCollapsed());
        assertFalse(view.quickSetupDismissed());
        assertFalse(view.quickSetupPinnedVisible());
        assertFalse(view.adminQuickSetupDismissed());
        assertFalse(view.adminQuickSetupPinnedVisible());
        assertFalse(view.destinationMailboxCollapsed());
        assertFalse(view.userPollingCollapsed());
        assertFalse(view.userStatsCollapsed());
        assertFalse(view.sourceEmailAccountsCollapsed());
        assertFalse(view.adminQuickSetupCollapsed());
        assertFalse(view.systemDashboardCollapsed());
        assertFalse(view.oauthAppsCollapsed());
        assertFalse(view.globalStatsCollapsed());
        assertFalse(view.userManagementCollapsed());
        assertEquals(UserUiPreferenceService.DEFAULT_USER_SECTION_ORDER, view.userSectionOrder());
        assertEquals(UserUiPreferenceService.DEFAULT_ADMIN_SECTION_ORDER, view.adminSectionOrder());
        assertEquals("en", view.language());
        assertEquals("SYSTEM", view.themeMode());
        assertEquals("AUTO", view.dateFormat());
        assertEquals("AUTO", view.timezoneMode());
        assertEquals("", view.timezone());
        assertEquals(List.of(), view.notificationHistory());
    }

    @Test
    void updatePersistsPerUserLayoutPreferences() {
        UserUiPreferenceService service = new UserUiPreferenceService();
        InMemoryUserUiPreferenceRepository repository = new InMemoryUserUiPreferenceRepository();
        service.repository = repository;
        service.objectMapper = new ObjectMapper();
        AppUser user = new AppUser();
        user.id = 11L;

        UserUiPreferenceView updated = service.update(user, new UpdateUserUiPreferenceRequest(
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                java.util.List.of("sourceEmailAccounts", "destination"),
                java.util.List.of("userManagement", "authSecurity"),
                "pt-PT",
                "DARK_BLUE",
                "YMD_12",
                "MANUAL",
                "Europe/Lisbon",
                List.of(new UserUiNotificationView(
                        "note-1",
                        Map.of("kind", "translation", "key", "notifications.signedIn", "params", Map.of()),
                        null,
                        "success",
                        "password-panel-section",
                        null,
                        1234L,
                        true,
                        10_000L))));

        assertTrue(updated.persistLayout());
        assertTrue(updated.layoutEditEnabled());
        assertTrue(updated.quickSetupCollapsed());
        assertFalse(updated.quickSetupDismissed());
        assertFalse(updated.quickSetupPinnedVisible());
        assertTrue(updated.adminQuickSetupDismissed());
        assertTrue(updated.adminQuickSetupPinnedVisible());
        assertTrue(updated.destinationMailboxCollapsed());
        assertTrue(updated.userPollingCollapsed());
        assertTrue(updated.userStatsCollapsed());
        assertTrue(updated.sourceEmailAccountsCollapsed());
        assertFalse(updated.adminQuickSetupCollapsed());
        assertFalse(updated.systemDashboardCollapsed());
        assertFalse(updated.oauthAppsCollapsed());
        assertTrue(updated.globalStatsCollapsed());
        assertTrue(updated.userManagementCollapsed());
        assertEquals(java.util.List.of("sourceEmailAccounts", "destination", "quickSetup", "userPolling", "remoteControl", "userStats"), updated.userSectionOrder());
        assertEquals(java.util.List.of("userManagement", "authSecurity", "adminQuickSetup", "systemDashboard", "oauthApps", "globalStats"), updated.adminSectionOrder());
        assertEquals("pt-PT", updated.language());
        assertEquals("DARK_BLUE", updated.themeMode());
        assertEquals("YMD_12", updated.dateFormat());
        assertEquals("MANUAL", updated.timezoneMode());
        assertEquals("Europe/Lisbon", updated.timezone());
        assertEquals(1, updated.notificationHistory().size());
        assertEquals("note-1", updated.notificationHistory().getFirst().id());
        assertEquals(Optional.of(updated), service.viewForUser(user.id));
    }

    @Test
    void updateFallsBackToAutomaticDateFormatForUnknownValues() {
        UserUiPreferenceService service = new UserUiPreferenceService();
        InMemoryUserUiPreferenceRepository repository = new InMemoryUserUiPreferenceRepository();
        service.repository = repository;
        service.objectMapper = new ObjectMapper();
        AppUser user = new AppUser();
        user.id = 21L;

        UserUiPreferenceView updated = service.update(user, new UpdateUserUiPreferenceRequest(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                "en",
                "NOT_A_THEME",
                "NOT_A_REAL_FORMAT",
                "AUTO",
                "",
                List.of()));

        assertEquals("AUTO", updated.dateFormat());
        assertEquals("SYSTEM", updated.themeMode());
    }

    @Test
    void updatePersistsValidCustomDateFormatsInDateFormat() {
        UserUiPreferenceService service = new UserUiPreferenceService();
        InMemoryUserUiPreferenceRepository repository = new InMemoryUserUiPreferenceRepository();
        service.repository = repository;
        service.objectMapper = new ObjectMapper();
        AppUser user = new AppUser();
        user.id = 31L;

        UserUiPreferenceView updated = service.update(user, new UpdateUserUiPreferenceRequest(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                "en",
                "LIGHT",
                "ddd, MMM DD YY h:M:S A",
                "AUTO",
                "",
                List.of()));

        assertEquals("ddd, MMM DD YY h:M:S A", updated.dateFormat());
        assertEquals("LIGHT_GREEN", updated.themeMode());
    }

    @Test
    void updateKeepsExplicitThemeVariants() {
        UserUiPreferenceService service = new UserUiPreferenceService();
        InMemoryUserUiPreferenceRepository repository = new InMemoryUserUiPreferenceRepository();
        service.repository = repository;
        service.objectMapper = new ObjectMapper();
        AppUser user = new AppUser();
        user.id = 41L;

        UserUiPreferenceView updated = service.update(user, new UpdateUserUiPreferenceRequest(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                "en",
                "DARK_GREEN",
                "AUTO",
                "AUTO",
                "",
                List.of()));

        assertEquals("DARK_GREEN", updated.themeMode());
    }

    private static final class InMemoryUserUiPreferenceRepository extends UserUiPreferenceRepository {
        private final Map<Long, UserUiPreference> byUserId = new HashMap<>();
        private long nextId = 1L;

        @Override
        public Optional<UserUiPreference> findByUserId(Long userId) {
            return Optional.ofNullable(byUserId.get(userId));
        }

        @Override
        public void persist(UserUiPreference entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            byUserId.put(entity.userId, entity);
        }
    }
}
