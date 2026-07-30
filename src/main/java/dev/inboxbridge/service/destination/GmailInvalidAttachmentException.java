package dev.inboxbridge.service.destination;

import java.util.Locale;

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

    /**
     * Recognizes both the typed rejection and the production-shaped generic
     * exception that can cross a destination boundary. Generic matching stays
     * deliberately strict so unrelated Gmail 400 responses remain fatal.
     */
    public static boolean isPolicyRejection(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof GmailInvalidAttachmentException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("failed to import gmail message: 400")
                        && normalized.contains("invalid attachment")
                        && normalized.contains("support.google.com/mail/answer/6590")) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
