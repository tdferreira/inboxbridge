package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Optional;

import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.UserUiPreferenceView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserUiPreference;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Owns the small set of per-user admin-ui preferences, including optionally
 * persisted layout state and the authenticated user's language choice.
 */
@ApplicationScoped
public class UserUiPreferenceService {

    static final String DEFAULT_LANGUAGE = "en";

    @Inject
    UserUiPreferenceRepository repository;

    public Optional<UserUiPreferenceView> viewForUser(Long userId) {
        return repository.findByUserId(userId).map(this::toView);
    }

    public UserUiPreferenceView defaultView() {
        return new UserUiPreferenceView(false, false, false, false, false, false, false, DEFAULT_LANGUAGE);
    }

    @Transactional
    public UserUiPreferenceView update(AppUser user, UpdateUserUiPreferenceRequest request) {
        UserUiPreference preference = repository.findByUserId(user.id).orElseGet(UserUiPreference::new);
        if (preference.id == null) {
            preference.userId = user.id;
        }
        preference.persistLayout = request.persistLayout() != null && request.persistLayout();
        preference.quickSetupCollapsed = request.quickSetupCollapsed() != null && request.quickSetupCollapsed();
        preference.gmailDestinationCollapsed = request.gmailDestinationCollapsed() != null && request.gmailDestinationCollapsed();
        preference.userPollingCollapsed = request.userPollingCollapsed() != null && request.userPollingCollapsed();
        preference.sourceBridgesCollapsed = request.sourceBridgesCollapsed() != null && request.sourceBridgesCollapsed();
        preference.systemDashboardCollapsed = request.systemDashboardCollapsed() != null && request.systemDashboardCollapsed();
        preference.userManagementCollapsed = request.userManagementCollapsed() != null && request.userManagementCollapsed();
        preference.language = normalizeLanguage(request.language());
        preference.updatedAt = Instant.now();
        repository.persist(preference);
        return toView(preference);
    }

    private UserUiPreferenceView toView(UserUiPreference preference) {
        return new UserUiPreferenceView(
                preference.persistLayout,
                preference.quickSetupCollapsed,
                preference.gmailDestinationCollapsed,
                preference.userPollingCollapsed,
                preference.sourceBridgesCollapsed,
                preference.systemDashboardCollapsed,
                preference.userManagementCollapsed,
                normalizeLanguage(preference.language));
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim();
    }
}
