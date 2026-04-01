package dev.inboxbridge.dto;

import java.util.List;

/**
 * Updates the authenticated user's persisted admin-ui layout preferences.
 */
public record UpdateUserUiPreferenceRequest(
        Boolean persistLayout,
        Boolean layoutEditEnabled,
        Boolean quickSetupCollapsed,
        Boolean quickSetupDismissed,
        Boolean quickSetupPinnedVisible,
        Boolean adminQuickSetupDismissed,
        Boolean adminQuickSetupPinnedVisible,
        Boolean destinationMailboxCollapsed,
        Boolean userPollingCollapsed,
        Boolean userStatsCollapsed,
        Boolean sourceEmailAccountsCollapsed,
        Boolean adminQuickSetupCollapsed,
        Boolean systemDashboardCollapsed,
        Boolean oauthAppsCollapsed,
        Boolean globalStatsCollapsed,
        Boolean userManagementCollapsed,
        List<String> userSectionOrder,
        List<String> adminSectionOrder,
        String language,
        List<UserUiNotificationView> notificationHistory) {
}
