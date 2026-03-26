package dev.inboxbridge.dto;

/**
 * Describes the authenticated user's persisted admin-ui layout preferences.
 */
public record UserUiPreferenceView(
        boolean persistLayout,
        boolean quickSetupCollapsed,
        boolean gmailDestinationCollapsed,
        boolean sourceBridgesCollapsed,
        boolean systemDashboardCollapsed,
        boolean userManagementCollapsed,
        String language) {
}
