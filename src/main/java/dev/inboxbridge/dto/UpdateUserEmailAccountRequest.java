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
        String customLabel,
        Boolean markReadAfterPoll,
        String postPollAction,
        String postPollTargetFolder) {

    public UpdateUserEmailAccountRequest(
            String originalEmailAccountId,
            String emailAccountId,
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
        this(
                originalEmailAccountId,
                emailAccountId,
                enabled,
                protocol,
                host,
                port,
                tls,
                authMethod,
                oauthProvider,
                username,
                password,
                oauthRefreshToken,
                folder,
                unreadOnly,
                customLabel,
                false,
                "NONE",
                "");
    }
}
