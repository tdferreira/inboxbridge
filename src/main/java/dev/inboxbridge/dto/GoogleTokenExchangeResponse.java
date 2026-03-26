package dev.inboxbridge.dto;

import java.time.Instant;

public record GoogleTokenExchangeResponse(
        boolean storedInDatabase,
        boolean usingEnvironmentFallback,
        String refreshToken,
        String credentialKey,
        String scope,
        String tokenType,
        Instant accessTokenExpiresAt,
        String nextStep) {
}
