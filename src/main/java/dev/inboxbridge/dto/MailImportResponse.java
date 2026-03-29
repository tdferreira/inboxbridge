package dev.inboxbridge.dto;

public record MailImportResponse(
        String destinationMessageId,
        String destinationThreadId) {
}