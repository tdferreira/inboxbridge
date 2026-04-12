package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionSessionView(
        Long id,
        String label,
        String browserFamily,
        String extensionVersion,
        String tokenPrefix,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt) {
}
