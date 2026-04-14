package dev.inboxbridge.service.security;

/**
 * Carries the inner local ciphertext through the outer transit provider so the
 * existing persistence model can support split-key protection without a schema
 * change.
 */
public record SplitKeyEnvelope(String ciphertextBase64, String nonceBase64) {

    public String serialize() {
        if (ciphertextBase64 == null || ciphertextBase64.isBlank() || nonceBase64 == null || nonceBase64.isBlank()) {
            throw new IllegalArgumentException("Split-key envelope requires ciphertext and nonce");
        }
        return nonceBase64 + "\n" + ciphertextBase64;
    }

    public static SplitKeyEnvelope parse(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            throw new IllegalArgumentException("Split-key envelope is empty");
        }
        int separator = serialized.indexOf('\n');
        if (separator <= 0 || separator == serialized.length() - 1) {
            throw new IllegalArgumentException("Split-key envelope is malformed");
        }
        String nonceBase64 = serialized.substring(0, separator).trim();
        String ciphertextBase64 = serialized.substring(separator + 1).trim();
        if (nonceBase64.isBlank() || ciphertextBase64.isBlank()) {
            throw new IllegalArgumentException("Split-key envelope is malformed");
        }
        return new SplitKeyEnvelope(ciphertextBase64, nonceBase64);
    }
}
