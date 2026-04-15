package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.List;

/**
 * Captures the post-cleanup verification result after operators remove legacy
 * key material and redeploy the active secret-management configuration.
 */
public record SecretManagementRetirementCompletionView(
        Instant verifiedAt,
        Long verifiedByUserId,
        String verifiedByUsername,
        String status,
        String message,
        List<String> unsatisfiedCheckIds) {
}
