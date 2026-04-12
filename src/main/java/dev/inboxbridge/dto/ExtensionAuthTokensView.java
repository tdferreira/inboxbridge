package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionAuthTokensView(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt) {
}
