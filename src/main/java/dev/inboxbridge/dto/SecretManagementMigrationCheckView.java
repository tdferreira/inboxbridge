package dev.inboxbridge.dto;

import java.util.List;

/**
 * Describes one backend-validated preflight check in the operator-facing
 * secret-provider migration checklist.
 */
public record SecretManagementMigrationCheckView(
        String checkId,
        String title,
        boolean satisfied,
        String detail,
        List<String> configReferences) {
}
