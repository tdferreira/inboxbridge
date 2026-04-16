package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Exports a point-in-time operator report for secret-management readiness and
 * the latest queued or completed re-encryption request snapshot.
 */
public record SecretManagementReportView(
        Instant exportedAt,
        SecretManagementStatusView status,
        java.util.List<String> saveChecklist,
        SecretManagementRecoveryGuideView recoveryGuide) {
}
