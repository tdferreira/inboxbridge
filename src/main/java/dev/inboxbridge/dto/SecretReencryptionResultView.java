package dev.inboxbridge.dto;

import java.util.List;

/**
 * Summarizes the outcome of re-encrypting stored application secrets under the
 * currently active key.
 */
public record SecretReencryptionResultView(
        String activeKeyVersion,
        int totalRecordsUpdated,
        int totalSecretValuesReencrypted,
        List<SecretReencryptionAreaResultView> areas,
        SecretReencryptionFollowUpView followUp) {
}
