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
        Boolean gmailDestinationCollapsed,
        Boolean userPollingCollapsed,
        Boolean userStatsCollapsed,
        Boolean sourceBridgesCollapsed,
        Boolean systemDashboardCollapsed,
        Boolean globalStatsCollapsed,
        Boolean userManagementCollapsed,
        List<String> userSectionOrder,
        List<String> adminSectionOrder,
        String language) {
}
