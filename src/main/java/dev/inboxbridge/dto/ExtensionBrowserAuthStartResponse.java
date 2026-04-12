package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionBrowserAuthStartResponse(
        String requestId,
        String browserUrl,
        Instant expiresAt) {
}
