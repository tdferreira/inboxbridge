package dev.inboxbridge.dto;

public record ExtensionAuthResponse(
        String status,
        ExtensionAuthSessionView session,
        StartPasskeyCeremonyResponse passkeyChallenge) {

    public static ExtensionAuthResponse authenticated(ExtensionAuthSessionView session) {
        return new ExtensionAuthResponse("AUTHENTICATED", session, null);
    }

    public static ExtensionAuthResponse passkeyRequired(StartPasskeyCeremonyResponse challenge) {
        return new ExtensionAuthResponse("PASSKEY_REQUIRED", null, challenge);
    }
}
