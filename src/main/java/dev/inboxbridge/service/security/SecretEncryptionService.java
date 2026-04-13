package dev.inboxbridge.service.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Encrypts and decrypts OAuth secrets for durable storage.
 *
 * <p>The implementation uses AES-GCM and binds each ciphertext to a provider /
 * subject / token-kind context via Additional Authenticated Data so a token
 * cannot be replayed across credential records without failing decryption.</p>
 */
@ApplicationScoped
public class SecretEncryptionService {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    @Inject
    LocalSecretKeyProvider localSecretKeyProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    public void setTokenEncryptionKey(String tokenEncryptionKey) {
        localProvider().setTokenEncryptionKey(tokenEncryptionKey);
    }

    public void setTokenEncryptionKeyId(String tokenEncryptionKeyId) {
        localProvider().setTokenEncryptionKeyId(tokenEncryptionKeyId);
    }

    public void setTokenEncryptionLegacyKeys(String tokenEncryptionLegacyKeys) {
        localProvider().setTokenEncryptionLegacyKeys(tokenEncryptionLegacyKeys);
    }

    public void setLocalSecretKeyProvider(LocalSecretKeyProvider localSecretKeyProvider) {
        this.localSecretKeyProvider = localSecretKeyProvider;
    }

    public boolean isConfigured() {
        return configuredProvider().isConfigured();
    }

    public String keyVersion() {
        requireConfigured();
        return configuredProvider().activeKey().storedKeyVersion();
    }

    public EncryptedValue encrypt(String value, String context) {
        requireConfigured();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cannot encrypt an empty secret");
        }

        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] aad = aad(context);
        SecretKeyMaterial keyMaterial = configuredProvider().activeKey();

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(keyMaterial), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return new EncryptedValue(base64(ciphertext), base64(nonce));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret encryption failed", e);
        }
    }

    public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion, String context) {
        requireConfigured();
        SecretKeyMaterial keyMaterial = configuredProvider().resolveKey(keyVersion)
                .orElseThrow(() -> new IllegalStateException("Stored secret was encrypted with an unavailable or unsupported key version"));

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey(keyMaterial),
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(nonceBase64)));
            cipher.updateAAD(aad(context));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64));
            return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plaintext)).toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret decryption failed", e);
        }
    }

    private SecretKeySpec secretKey(SecretKeyMaterial keyMaterial) {
        return new SecretKeySpec(keyMaterial.encodedKey(), "AES");
    }

    private byte[] aad(String context) {
        return context.getBytes(StandardCharsets.UTF_8);
    }

    private String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
        }
    }

    private SecretKeyProvider configuredProvider() {
        return localProvider();
    }

    private LocalSecretKeyProvider localProvider() {
        if (localSecretKeyProvider == null) {
            localSecretKeyProvider = new LocalSecretKeyProvider();
        }
        return localSecretKeyProvider;
    }

    public record EncryptedValue(String ciphertextBase64, String nonceBase64) {
    }
}
