package dev.inboxbridge.dto;

import java.util.List;

/**
 * Describes the operator-facing recovery procedure when the latest
 * secret-management action failed or ended with outstanding verification
 * warnings.
 */
public record SecretManagementRecoveryGuideView(
        String title,
        String summary,
        String triggerReason,
        String currentMode,
        String providerId,
        String latestRequestStatus,
        String latestRequestMessage,
        boolean rollbackRecommended,
        List<String> containmentSteps,
        List<String> rollbackSteps,
        List<String> validationSteps,
        List<String> evidenceItems) {
}
