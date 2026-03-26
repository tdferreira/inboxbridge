package dev.inboxbridge.dto;

/**
 * Browser-facing login response that either completes authentication
 * immediately or indicates that a follow-up passkey ceremony is required.
 */
public record LoginResponse(
        String status,
        SessionUserResponse user,
        StartPasskeyCeremonyResponse passkeyChallenge) {

    public static LoginResponse authenticated(SessionUserResponse user) {
        return new LoginResponse("AUTHENTICATED", user, null);
    }

    public static LoginResponse passkeyRequired(StartPasskeyCeremonyResponse challenge) {
        return new LoginResponse("PASSKEY_REQUIRED", null, challenge);
    }
}
