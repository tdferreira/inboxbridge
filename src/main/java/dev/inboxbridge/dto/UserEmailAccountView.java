package dev.inboxbridge.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserEmailAccountView(
        @JsonProperty("emailAccountId") @JsonAlias("bridgeId") String emailAccountId,
        boolean enabled,
        boolean effectivePollEnabled,
        String effectivePollInterval,
        int effectiveFetchWindow,
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
        String fetchMode,
        String customLabel,
        boolean markReadAfterPoll,
        String postPollAction,
        String postPollTargetFolder,
        String tokenStorageMode,
        long totalImportedMessages,
        Instant lastImportedAt,
        AdminPollEventSummary lastEvent,
        SourcePollingStateView pollingState,
        SourceDiagnosticsView diagnostics) {

        @JsonProperty("bridgeId")
        public String legacyBridgeId() {
                return emailAccountId;
        }
}
