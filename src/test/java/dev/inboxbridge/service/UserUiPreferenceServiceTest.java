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
        assertFalse(view.quickSetupCollapsed());
        assertFalse(view.gmailDestinationCollapsed());
        assertFalse(view.sourceBridgesCollapsed());
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
                false,
                true,
                false,
                true));

        assertTrue(updated.persistLayout());
        assertTrue(updated.quickSetupCollapsed());
        assertTrue(updated.sourceBridgesCollapsed());
        assertTrue(updated.userManagementCollapsed());
        assertFalse(updated.gmailDestinationCollapsed());
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
