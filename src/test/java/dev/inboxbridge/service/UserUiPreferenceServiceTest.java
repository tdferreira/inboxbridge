package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.UserUiPreferenceView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserUiPreference;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;

class UserUiPreferenceServiceTest {

    @Test
    void defaultViewStartsWithCollapsedStateDisabled() {
        UserUiPreferenceService service = new UserUiPreferenceService();

        UserUiPreferenceView view = service.defaultView();

        assertFalse(view.persistLayout());
        assertFalse(view.layoutEditEnabled());
        assertFalse(view.quickSetupCollapsed());
        assertFalse(view.quickSetupDismissed());
        assertFalse(view.quickSetupPinnedVisible());
        assertFalse(view.gmailDestinationCollapsed());
        assertFalse(view.userPollingCollapsed());
        assertFalse(view.userStatsCollapsed());
        assertFalse(view.sourceBridgesCollapsed());
        assertFalse(view.adminQuickSetupCollapsed());
        assertFalse(view.systemDashboardCollapsed());
        assertFalse(view.oauthAppsCollapsed());
        assertFalse(view.globalStatsCollapsed());
        assertFalse(view.userManagementCollapsed());
        assertEquals(UserUiPreferenceService.DEFAULT_USER_SECTION_ORDER, view.userSectionOrder());
        assertEquals(UserUiPreferenceService.DEFAULT_ADMIN_SECTION_ORDER, view.adminSectionOrder());
        assertEquals("en", view.language());
    }

    @Test
    void updatePersistsPerUserLayoutPreferences() {
        UserUiPreferenceService service = new UserUiPreferenceService();
        InMemoryUserUiPreferenceRepository repository = new InMemoryUserUiPreferenceRepository();
        service.repository = repository;
        AppUser user = new AppUser();
        user.id = 11L;

        UserUiPreferenceView updated = service.update(user, new UpdateUserUiPreferenceRequest(
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                java.util.List.of("sourceBridges", "gmail"),
                java.util.List.of("userManagement"),
                "pt-PT"));

        assertTrue(updated.persistLayout());
        assertTrue(updated.layoutEditEnabled());
        assertTrue(updated.quickSetupCollapsed());
        assertFalse(updated.quickSetupDismissed());
        assertTrue(updated.quickSetupPinnedVisible());
        assertFalse(updated.userPollingCollapsed());
        assertTrue(updated.userStatsCollapsed());
        assertTrue(updated.sourceBridgesCollapsed());
        assertFalse(updated.adminQuickSetupCollapsed());
        assertFalse(updated.systemDashboardCollapsed());
        assertFalse(updated.oauthAppsCollapsed());
        assertTrue(updated.globalStatsCollapsed());
        assertTrue(updated.userManagementCollapsed());
        assertFalse(updated.gmailDestinationCollapsed());
        assertEquals(java.util.List.of("sourceBridges", "gmail", "quickSetup", "userPolling", "userStats"), updated.userSectionOrder());
        assertEquals(java.util.List.of("userManagement", "adminQuickSetup", "systemDashboard", "oauthApps", "globalStats"), updated.adminSectionOrder());
        assertEquals("pt-PT", updated.language());
        assertEquals(Optional.of(updated), service.viewForUser(user.id));
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
