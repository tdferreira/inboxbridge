package dev.inboxbridge.dto;

import java.util.List;

/**
 * Describes one backend-verified prerequisite for a high-risk secret
 * re-encryption operation.
 */
public record SecretReencryptionRequirementView(
        String requirementId,
        String title,
        String detail,
        List<String> remediationSteps,
        List<String> configReferences,
        String actionTargetId,
        String actionLabel,
        boolean satisfied,
        boolean blocking) {
}
