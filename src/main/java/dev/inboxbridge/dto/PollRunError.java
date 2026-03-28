package dev.inboxbridge.dto;

public record PollRunError(
        String code,
        String sourceId,
        String message,
        String value) {
}
