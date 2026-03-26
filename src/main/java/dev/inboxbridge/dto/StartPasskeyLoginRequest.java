package dev.inboxbridge.dto;

/**
 * Optional username hint for browser-initiated passkey authentication.
 */
public record StartPasskeyLoginRequest(String username) {
}
