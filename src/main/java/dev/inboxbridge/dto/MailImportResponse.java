package dev.inboxbridge.dto;

import java.util.List;

public record MailImportResponse(
        String destinationMessageId,
        String destinationThreadId,
        List<String> removedAttachmentNames) {

    public MailImportResponse(String destinationMessageId, String destinationThreadId) {
        this(destinationMessageId, destinationThreadId, List.of());
    }

    public MailImportResponse {
        removedAttachmentNames = List.copyOf(removedAttachmentNames);
    }

    public boolean sanitized() {
        return !removedAttachmentNames.isEmpty();
    }
}
