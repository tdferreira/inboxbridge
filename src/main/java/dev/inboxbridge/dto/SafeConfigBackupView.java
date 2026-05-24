package dev.inboxbridge.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record SafeConfigBackupView(
        Instant generatedAt,
        boolean includesSecrets,
        String warning,
        JsonNode data) {
}
