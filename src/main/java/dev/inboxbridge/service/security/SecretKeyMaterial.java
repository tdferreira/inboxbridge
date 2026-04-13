package dev.inboxbridge.service.security;

import java.util.Arrays;

/**
 * Represents one concrete encryption key that can protect stored application
 * secrets.
 */
public record SecretKeyMaterial(String providerId, String keyId, byte[] encodedKey) {

    public SecretKeyMaterial {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Secret key provider id is required");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Secret key id is required");
        }
        if (encodedKey == null || encodedKey.length == 0) {
            throw new IllegalArgumentException("Secret key material is required");
        }
        encodedKey = Arrays.copyOf(encodedKey, encodedKey.length);
    }

    public byte[] encodedKey() {
        return Arrays.copyOf(encodedKey, encodedKey.length);
    }

    public String storedKeyVersion() {
        return providerId + ":" + keyId;
    }
}
