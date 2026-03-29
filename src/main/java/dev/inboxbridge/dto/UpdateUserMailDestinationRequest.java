package dev.inboxbridge.dto;

public record UpdateUserMailDestinationRequest(
        String provider,
        String host,
        Integer port,
        Boolean tls,
        String authMethod,
        String oauthProvider,
        String username,
        String password,
        String folder) {
}