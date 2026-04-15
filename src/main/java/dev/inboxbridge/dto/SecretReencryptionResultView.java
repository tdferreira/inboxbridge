package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.List;

/**
 * Summarizes the outcome of re-encrypting stored application secrets under the
 * currently active key.
 */
public record SecretReencryptionResultView(
        String operationStatus,
        String message,
        Instant executeAfter,
        String activeKeyVersion,
        int totalRecordsUpdated,
        int totalSecretValuesReencrypted,
        List<SecretReencryptionAreaResultView> areas,
        SecretReencryptionFollowUpView followUp,
        SecretReencryptionVerificationView verification) {
}
