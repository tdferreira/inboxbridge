package dev.inboxbridge.dto;

public record ExtensionBrowserAuthRedeemRequest(
        String requestId,
        String codeVerifier) {
}
