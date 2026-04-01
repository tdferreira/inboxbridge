package dev.inboxbridge.dto;

/**
 * Legacy payload for browser-initiated passkey authentication.
 *
 * The unauthenticated sign-in endpoints intentionally ignore any username hint
 * and always start a discoverable-credential ceremony so they do not reveal
 * account-specific password/passkey state.
 */
public record StartPasskeyLoginRequest(String username) {
}
