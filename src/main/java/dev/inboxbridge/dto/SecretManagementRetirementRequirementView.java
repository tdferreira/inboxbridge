package dev.inboxbridge.dto;

import java.util.List;

/**
 * Describes one backend-verified prerequisite for safely retiring legacy
 * secret-management key material after a rotation or provider migration.
 */
public record SecretManagementRetirementRequirementView(
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
