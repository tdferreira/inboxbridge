package dev.inboxbridge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateUserEmailAccountRequest(
        @JsonProperty("originalEmailAccountId") @JsonAlias("originalEmailAccountId") String originalEmailAccountId,
        @JsonProperty("emailAccountId") @JsonAlias("bridgeId") String emailAccountId,
        Boolean enabled,
        String protocol,
        String host,
        Integer port,
        Boolean tls,
        String authMethod,
        String oauthProvider,
        String username,
        String password,
        String oauthRefreshToken,
        String folder,
        Boolean unreadOnly,
        String customLabel) {
}
