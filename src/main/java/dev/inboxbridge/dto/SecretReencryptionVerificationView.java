package dev.inboxbridge.dto;

import java.util.List;

/**
 * Confirms whether the re-encryption run ended in a state that is safe to keep
 * operating from and highlights what operators should save before retiring any
 * old key material.
 */
public record SecretReencryptionVerificationView(
        boolean passed,
        List<String> messages,
        List<String> operatorSaveItems) {
}
