package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionSessionCreateView(
        Long id,
        String label,
        String browserFamily,
        String extensionVersion,
        String token,
        String tokenPrefix,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt) {
}
