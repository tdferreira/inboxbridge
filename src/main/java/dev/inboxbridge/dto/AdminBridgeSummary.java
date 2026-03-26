package dev.inboxbridge.dto;

import java.time.Instant;

public record AdminBridgeSummary(
        String id,
        boolean enabled,
        boolean effectivePollEnabled,
        String effectivePollInterval,
        int effectiveFetchWindow,
        String protocol,
        String authMethod,
        String oauthProvider,
        String host,
        int port,
        boolean tls,
        String folder,
        boolean unreadOnly,
        String customLabel,
        boolean passwordConfigured,
        String tokenStorageMode,
        long totalImportedMessages,
        Instant lastImportedAt,
        AdminPollEventSummary lastEvent,
        SourcePollingStateView pollingState) {
}
