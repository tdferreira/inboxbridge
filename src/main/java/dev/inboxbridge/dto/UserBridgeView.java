package dev.inboxbridge.dto;

import java.time.Instant;

public record UserBridgeView(
        String bridgeId,
        boolean enabled,
        String protocol,
        String host,
        int port,
        boolean tls,
        String authMethod,
        String oauthProvider,
        String username,
        boolean passwordConfigured,
        boolean oauthRefreshTokenConfigured,
        String folder,
        boolean unreadOnly,
        String customLabel,
        String tokenStorageMode,
        long totalImportedMessages,
        Instant lastImportedAt,
        AdminPollEventSummary lastEvent) {
}
