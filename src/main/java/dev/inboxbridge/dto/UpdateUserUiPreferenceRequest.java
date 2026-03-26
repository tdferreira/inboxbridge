package dev.inboxbridge.dto;

/**
 * Updates the authenticated user's persisted admin-ui layout preferences.
 */
public record UpdateUserUiPreferenceRequest(
        Boolean persistLayout,
        Boolean quickSetupCollapsed,
        Boolean gmailDestinationCollapsed,
        Boolean userPollingCollapsed,
        Boolean sourceBridgesCollapsed,
        Boolean systemDashboardCollapsed,
        Boolean userManagementCollapsed,
        String language) {
}
