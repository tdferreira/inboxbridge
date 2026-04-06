package dev.inboxbridge.dto;

import java.util.List;

/**
 * Describes the authenticated user's persisted admin-ui layout preferences.
 */
public record UserUiPreferenceView(
        boolean persistLayout,
        boolean layoutEditEnabled,
        boolean quickSetupCollapsed,
        boolean quickSetupDismissed,
        boolean quickSetupPinnedVisible,
        boolean adminQuickSetupDismissed,
        boolean adminQuickSetupPinnedVisible,
        boolean destinationMailboxCollapsed,
        boolean userPollingCollapsed,
        boolean userStatsCollapsed,
        boolean sourceEmailAccountsCollapsed,
        boolean adminQuickSetupCollapsed,
        boolean systemDashboardCollapsed,
        boolean oauthAppsCollapsed,
        boolean globalStatsCollapsed,
        boolean userManagementCollapsed,
        List<String> userSectionOrder,
        List<String> adminSectionOrder,
        String language,
        String dateFormat,
        String timezoneMode,
        String timezone,
        List<UserUiNotificationView> notificationHistory) {
}
