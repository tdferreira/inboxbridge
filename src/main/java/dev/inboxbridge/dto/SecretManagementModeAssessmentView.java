package dev.inboxbridge.dto;

import java.util.List;

/**
 * Describes how ready a specific secret-management mode is to become the next
 * active encryption target for this deployment.
 */
public record SecretManagementModeAssessmentView(
        String mode,
        String providerId,
        boolean current,
        boolean healthy,
        boolean writable,
        String statusMessage,
        String activeKeyVersion,
        String activeKeyId,
        List<String> configReferences,
        List<String> remediationSteps) {
}
