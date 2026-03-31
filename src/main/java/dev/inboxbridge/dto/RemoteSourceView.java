package dev.inboxbridge.dto;

import java.time.Instant;

public record RemoteSourceView(
        String sourceId,
        String scope,
        Long ownerUserId,
        String ownerLabel,
        boolean enabled,
        boolean effectivePollEnabled,
        String effectivePollInterval,
        int effectiveFetchWindow,
        String protocol,
        String host,
        int port,
        String username,
        String folder,
        String customLabel,
        long totalImportedMessages,
        Instant lastImportedAt,
        AdminPollEventSummary lastEvent,
        SourcePollingStateView pollingState) {
}
