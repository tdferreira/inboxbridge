package dev.inboxbridge.service.destination;

/**
 * Signals that Gmail permanently rejected one raw message because an attachment
 * violates Gmail's attachment policy.
 */
public class GmailInvalidAttachmentException extends IllegalStateException {

    private final int statusCode;

    public GmailInvalidAttachmentException(int statusCode, String responseBody) {
        super("Failed to import Gmail message: " + statusCode + " - " + responseBody);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
