package dev.inboxbridge.dto;

public record ConfigBackupExportRequest(
        String publicKeyPem,
        Boolean riskAcknowledged) {
}
