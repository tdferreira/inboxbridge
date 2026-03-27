package dev.inboxbridge.dto;

import java.time.Instant;

public record GoogleTokenExchangeResponse(
        boolean storedInDatabase,
        boolean usingEnvironmentFallback,
        boolean replacedExistingAccount,
        boolean sameLinkedAccount,
        boolean previousGrantRevoked,
        String refreshToken,
        String credentialKey,
        String scope,
        String tokenType,
        Instant accessTokenExpiresAt,
        String nextStep) {
}
