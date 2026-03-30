package dev.inboxbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserSummaryResponse(
        Long id,
        String username,
        String role,
        boolean active,
        boolean approved,
        boolean mustChangePassword,
        boolean passwordConfigured,
        boolean gmailConfigured,
        @JsonProperty("emailAccountCount") int emailAccountCount,
        int passkeyCount) {

    @JsonProperty("bridgeCount")
    public int legacyBridgeCount() {
        return emailAccountCount;
    }
}
