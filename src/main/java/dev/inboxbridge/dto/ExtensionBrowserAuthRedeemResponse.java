package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionBrowserAuthRedeemResponse(
        String status,
        ExtensionAuthSessionView session,
        Instant expiresAt) {

    public static ExtensionBrowserAuthRedeemResponse pending(Instant expiresAt) {
        return new ExtensionBrowserAuthRedeemResponse("PENDING", null, expiresAt);
    }

    public static ExtensionBrowserAuthRedeemResponse authenticated(ExtensionAuthSessionView session, Instant expiresAt) {
        return new ExtensionBrowserAuthRedeemResponse("AUTHENTICATED", session, expiresAt);
    }

    public static ExtensionBrowserAuthRedeemResponse expired() {
        return new ExtensionBrowserAuthRedeemResponse("EXPIRED", null, null);
    }
}
