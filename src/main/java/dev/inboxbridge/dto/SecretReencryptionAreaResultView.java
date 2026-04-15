package dev.inboxbridge.dto;

/**
 * Reports how many records and secret fields were rewritten for one secret
 * area during a bulk re-encryption run.
 */
public record SecretReencryptionAreaResultView(
        String area,
        int recordsUpdated,
        int secretValuesReencrypted,
        int fullReencryptionCount,
        int metadataRewrapCount) {
}
