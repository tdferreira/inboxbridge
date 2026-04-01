package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.UserUiNotificationView;
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
    static final List<String> DEFAULT_USER_SECTION_ORDER = List.of("quickSetup", "destination", "sourceEmailAccounts", "userPolling", "remoteControl", "userStats");
    static final List<String> DEFAULT_ADMIN_SECTION_ORDER = List.of("adminQuickSetup", "systemDashboard", "oauthApps", "userManagement", "authSecurity", "globalStats");
    static final int MAX_NOTIFICATION_HISTORY = 50;
    private static final TypeReference<List<UserUiNotificationView>> NOTIFICATION_HISTORY_TYPE = new TypeReference<>() {
    };

    @Inject
    UserUiPreferenceRepository repository;

    @Inject
    ObjectMapper objectMapper;

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
                false,
                false,
                DEFAULT_USER_SECTION_ORDER,
                DEFAULT_ADMIN_SECTION_ORDER,
                DEFAULT_LANGUAGE,
                List.of());
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
        preference.adminQuickSetupDismissed = request.adminQuickSetupDismissed() != null && request.adminQuickSetupDismissed();
        preference.adminQuickSetupPinnedVisible = request.adminQuickSetupPinnedVisible() != null && request.adminQuickSetupPinnedVisible();
        preference.destinationMailboxCollapsed = request.destinationMailboxCollapsed() != null && request.destinationMailboxCollapsed();
        preference.userPollingCollapsed = request.userPollingCollapsed() != null && request.userPollingCollapsed();
        preference.userStatsCollapsed = request.userStatsCollapsed() != null && request.userStatsCollapsed();
        preference.sourceEmailAccountsCollapsed = request.sourceEmailAccountsCollapsed() != null && request.sourceEmailAccountsCollapsed();
        preference.adminQuickSetupCollapsed = request.adminQuickSetupCollapsed() != null && request.adminQuickSetupCollapsed();
        preference.systemDashboardCollapsed = request.systemDashboardCollapsed() != null && request.systemDashboardCollapsed();
        preference.oauthAppsCollapsed = request.oauthAppsCollapsed() != null && request.oauthAppsCollapsed();
        preference.globalStatsCollapsed = request.globalStatsCollapsed() != null && request.globalStatsCollapsed();
        preference.userManagementCollapsed = request.userManagementCollapsed() != null && request.userManagementCollapsed();
        preference.userSectionOrder = joinSectionOrder(normalizeSectionOrder(request.userSectionOrder(), DEFAULT_USER_SECTION_ORDER));
        preference.adminSectionOrder = joinSectionOrder(normalizeSectionOrder(request.adminSectionOrder(), DEFAULT_ADMIN_SECTION_ORDER));
        preference.language = normalizeLanguage(request.language());
        preference.notificationHistory = serializeNotificationHistory(normalizeNotificationHistory(request.notificationHistory()));
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
                preference.adminQuickSetupDismissed,
                preference.adminQuickSetupPinnedVisible,
                preference.destinationMailboxCollapsed,
                preference.userPollingCollapsed,
                preference.userStatsCollapsed,
                preference.sourceEmailAccountsCollapsed,
                preference.adminQuickSetupCollapsed,
                preference.systemDashboardCollapsed,
                preference.oauthAppsCollapsed,
                preference.globalStatsCollapsed,
                preference.userManagementCollapsed,
                normalizeSectionOrder(splitSectionOrder(preference.userSectionOrder), DEFAULT_USER_SECTION_ORDER),
                normalizeSectionOrder(splitSectionOrder(preference.adminSectionOrder), DEFAULT_ADMIN_SECTION_ORDER),
                normalizeLanguage(preference.language),
                normalizeNotificationHistory(deserializeNotificationHistory(preference.notificationHistory)));
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
                    .map(this::normalizeLegacySectionId)
                    .filter(defaults::contains)
                    .forEach(ordered::add);
        }
        defaults.forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private String normalizeLegacySectionId(String sectionId) {
        if (sectionId == null || sectionId.isBlank()) {
            return "";
        }
        return switch (sectionId) {
            case "gmail" -> "destination";
            case "sourceBridges" -> "sourceEmailAccounts";
            default -> sectionId;
        };
    }

    private String joinSectionOrder(List<String> values) {
        return values.stream().collect(Collectors.joining(","));
    }

    private List<UserUiNotificationView> deserializeNotificationHistory(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawValue, NOTIFICATION_HISTORY_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String serializeNotificationHistory(List<UserUiNotificationView> notifications) {
        try {
            return objectMapper.writeValueAsString(normalizeNotificationHistory(notifications));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize notification history", e);
        }
    }

    private List<UserUiNotificationView> normalizeNotificationHistory(List<UserUiNotificationView> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        List<UserUiNotificationView> normalized = notifications.stream()
                .filter(java.util.Objects::nonNull)
                .filter(notification -> notification.id() != null && !notification.id().isBlank())
                .filter(notification -> notification.message() != null || notification.copyText() != null)
                .map(notification -> new UserUiNotificationView(
                        notification.id().trim(),
                        notification.message(),
                        notification.copyText(),
                        normalizeNotificationTone(notification.tone()),
                        normalizeOptionalText(notification.targetId()),
                        normalizeOptionalText(notification.groupKey()),
                        notification.createdAt(),
                        notification.floatingVisible(),
                        notification.autoCloseMs()))
                .toList();
        if (normalized.size() <= MAX_NOTIFICATION_HISTORY) {
            return normalized;
        }
        return normalized.subList(normalized.size() - MAX_NOTIFICATION_HISTORY, normalized.size());
    }

    private String normalizeNotificationTone(String tone) {
        if (tone == null || tone.isBlank()) {
            return "success";
        }
        return switch (tone.trim()) {
            case "success", "warning", "error", "info" -> tone.trim();
            default -> "success";
        };
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
