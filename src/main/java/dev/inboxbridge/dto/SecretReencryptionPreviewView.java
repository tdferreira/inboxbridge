package dev.inboxbridge.dto;

import java.util.List;

/**
 * Summarizes, without mutating data, how many stored secrets InboxBridge would
 * rewrite if the operator starts the current bulk re-encryption plan.
 */
public record SecretReencryptionPreviewView(
        String activeKeyVersion,
        int totalRecordsPendingUpdate,
        int totalSecretValuesPendingRewrite,
        int totalFullReencryptionCount,
        int totalMetadataRewrapCount,
        List<SecretReencryptionAreaResultView> areas) {
}
