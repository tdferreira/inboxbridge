package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    static final List<String> DEFAULT_USER_SECTION_ORDER = List.of("quickSetup", "gmail", "userPolling", "userStats", "sourceBridges");
    static final List<String> DEFAULT_ADMIN_SECTION_ORDER = List.of("adminQuickSetup", "systemDashboard", "oauthApps", "globalStats", "userManagement");

    @Inject
    UserUiPreferenceRepository repository;

    public Optional<UserUiPreferenceView> viewForUser(Long userId) {
        return repository.findByUserId(userId).map(this::toView);
    }

    public UserUiPreferenceView defaultView() {
        return new UserUiPreferenceView(
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
                DEFAULT_USER_SECTION_ORDER,
                DEFAULT_ADMIN_SECTION_ORDER,
                DEFAULT_LANGUAGE);
    }

    @Transactional
    public UserUiPreferenceView update(AppUser user, UpdateUserUiPreferenceRequest request) {
        UserUiPreference preference = repository.findByUserId(user.id).orElseGet(UserUiPreference::new);
        if (preference.id == null) {
            preference.userId = user.id;
        }
        preference.persistLayout = request.persistLayout() != null && request.persistLayout();
        preference.layoutEditEnabled = request.layoutEditEnabled() != null && request.layoutEditEnabled();
        preference.quickSetupCollapsed = request.quickSetupCollapsed() != null && request.quickSetupCollapsed();
        preference.quickSetupDismissed = request.quickSetupDismissed() != null && request.quickSetupDismissed();
        preference.quickSetupPinnedVisible = request.quickSetupPinnedVisible() != null && request.quickSetupPinnedVisible();
        preference.gmailDestinationCollapsed = request.gmailDestinationCollapsed() != null && request.gmailDestinationCollapsed();
        preference.userPollingCollapsed = request.userPollingCollapsed() != null && request.userPollingCollapsed();
        preference.userStatsCollapsed = request.userStatsCollapsed() != null && request.userStatsCollapsed();
        preference.sourceBridgesCollapsed = request.sourceBridgesCollapsed() != null && request.sourceBridgesCollapsed();
        preference.adminQuickSetupCollapsed = request.adminQuickSetupCollapsed() != null && request.adminQuickSetupCollapsed();
        preference.systemDashboardCollapsed = request.systemDashboardCollapsed() != null && request.systemDashboardCollapsed();
        preference.oauthAppsCollapsed = request.oauthAppsCollapsed() != null && request.oauthAppsCollapsed();
        preference.globalStatsCollapsed = request.globalStatsCollapsed() != null && request.globalStatsCollapsed();
        preference.userManagementCollapsed = request.userManagementCollapsed() != null && request.userManagementCollapsed();
        preference.userSectionOrder = joinSectionOrder(normalizeSectionOrder(request.userSectionOrder(), DEFAULT_USER_SECTION_ORDER));
        preference.adminSectionOrder = joinSectionOrder(normalizeSectionOrder(request.adminSectionOrder(), DEFAULT_ADMIN_SECTION_ORDER));
        preference.language = normalizeLanguage(request.language());
        preference.updatedAt = Instant.now();
        repository.persist(preference);
        return toView(preference);
    }

    private UserUiPreferenceView toView(UserUiPreference preference) {
        return new UserUiPreferenceView(
                preference.persistLayout,
                preference.layoutEditEnabled,
                preference.quickSetupCollapsed,
                preference.quickSetupDismissed,
                preference.quickSetupPinnedVisible,
                preference.gmailDestinationCollapsed,
                preference.userPollingCollapsed,
                preference.userStatsCollapsed,
                preference.sourceBridgesCollapsed,
                preference.adminQuickSetupCollapsed,
                preference.systemDashboardCollapsed,
                preference.oauthAppsCollapsed,
                preference.globalStatsCollapsed,
                preference.userManagementCollapsed,
                normalizeSectionOrder(splitSectionOrder(preference.userSectionOrder), DEFAULT_USER_SECTION_ORDER),
                normalizeSectionOrder(splitSectionOrder(preference.adminSectionOrder), DEFAULT_ADMIN_SECTION_ORDER),
                normalizeLanguage(preference.language));
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim();
    }

    private List<String> splitSectionOrder(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(storedValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> normalizeSectionOrder(List<String> requested, List<String> defaults) {
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        if (requested != null) {
            requested.stream()
                    .filter(defaults::contains)
                    .forEach(ordered::add);
        }
        defaults.forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private String joinSectionOrder(List<String> values) {
        return values.stream().collect(Collectors.joining(","));
    }
}
