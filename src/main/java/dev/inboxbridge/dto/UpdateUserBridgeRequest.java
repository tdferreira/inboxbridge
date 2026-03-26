package dev.inboxbridge.dto;

public record UpdateUserBridgeRequest(
        String bridgeId,
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
