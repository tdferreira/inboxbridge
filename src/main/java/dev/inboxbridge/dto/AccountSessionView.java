package dev.inboxbridge.dto;

import java.time.Instant;

public record AccountSessionView(
        Long id,
        String sessionType,
        String ipAddress,
        String locationLabel,
        String loginMethod,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current,
        boolean active) {
}
