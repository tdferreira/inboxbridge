package dev.inboxbridge.dto;

public record EmailAccountConnectionTestResult(
        boolean success,
        String message,
        String protocol,
        String host,
        int port,
        boolean tls,
        String authMethod,
        String oauthProvider,
        boolean authenticated,
        String folder,
        boolean folderAccessible,
        boolean unreadFilterRequested,
        Boolean unreadFilterSupported,
        Boolean unreadFilterValidated,
        Integer visibleMessageCount,
        Integer unreadMessageCount,
        Boolean sampleMessageAvailable,
        Boolean sampleMessageMaterialized) {
}
