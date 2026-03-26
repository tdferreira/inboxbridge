package dev.inboxbridge.dto;

public record FinishPasskeyCeremonyRequest(
        String ceremonyId,
        String credentialJson) {
}
