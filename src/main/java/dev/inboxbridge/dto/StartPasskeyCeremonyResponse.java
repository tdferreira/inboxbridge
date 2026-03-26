package dev.inboxbridge.dto;

/**
 * Returns the opaque ceremony id plus the browser-facing WebAuthn request
 * payload that should be passed to navigator.credentials.*.
 */
public record StartPasskeyCeremonyResponse(
        String ceremonyId,
        String publicKeyJson) {
}
