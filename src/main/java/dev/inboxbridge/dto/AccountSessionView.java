package dev.inboxbridge.dto;

import java.time.Instant;

public record AccountSessionView(
        Long id,
        String sessionType,
        String browserLabel,
        String deviceLabel,
        String ipAddress,
        String locationLabel,
        boolean unusualLocation,
        String deviceLocationLabel,
        Double deviceLatitude,
        Double deviceLongitude,
        Instant deviceLocationCapturedAt,
        String loginMethod,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current,
        boolean active) {
}
