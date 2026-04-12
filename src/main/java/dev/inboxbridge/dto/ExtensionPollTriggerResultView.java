package dev.inboxbridge.dto;

public record ExtensionPollTriggerResultView(
        boolean accepted,
        boolean started,
        String reason,
        String message) {
}
