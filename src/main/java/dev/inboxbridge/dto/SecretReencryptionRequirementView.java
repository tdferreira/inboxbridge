package dev.inboxbridge.dto;

/**
 * Describes one backend-verified prerequisite for a high-risk secret
 * re-encryption operation.
 */
public record SecretReencryptionRequirementView(
        String requirementId,
        String title,
        String detail,
        boolean satisfied,
        boolean blocking) {
}
