package dev.inboxbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MicrosoftTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType) {
}
