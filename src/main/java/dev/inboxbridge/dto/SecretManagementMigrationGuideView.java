package dev.inboxbridge.dto;

import java.util.List;

/**
 * Summarizes the recommended cutover procedure for switching InboxBridge from
 * the current secret-provider mode to another configured target mode.
 */
public record SecretManagementMigrationGuideView(
        String currentMode,
        String currentProviderId,
        String targetMode,
        String targetProviderId,
        boolean targetReady,
        boolean current,
        boolean continueReady,
        String title,
        String summary,
        String executionMethod,
        List<SecretManagementMigrationCheckView> checks,
        List<String> beforeSwitchSteps,
        List<String> switchSteps,
        List<String> afterSwitchSteps,
        List<SecretReencryptionRequirementView> postSwitchRequirements) {
}
