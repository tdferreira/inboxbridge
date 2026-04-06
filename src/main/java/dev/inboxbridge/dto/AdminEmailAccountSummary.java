package dev.inboxbridge.dto;

import java.time.Instant;

public record AdminEmailAccountSummary(
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
        String fetchMode,
        String customLabel,
        boolean markReadAfterPoll,
        String postPollAction,
        String postPollTargetFolder,
        boolean passwordConfigured,
        String tokenStorageMode,
        long totalImportedMessages,
        Instant lastImportedAt,
        AdminPollEventSummary lastEvent,
        SourcePollingStateView pollingState,
        SourceDiagnosticsView diagnostics) {
}
