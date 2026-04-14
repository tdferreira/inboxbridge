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

    @Inject
    SecretProviderResolver secretProviderResolver;

    @Inject
    TransitSecretProvider transitSecretProvider;

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

    public void setSecretProviderResolver(SecretProviderResolver secretProviderResolver) {
        this.secretProviderResolver = secretProviderResolver;
    }

    public void setTransitSecretProvider(TransitSecretProvider transitSecretProvider) {
        this.transitSecretProvider = transitSecretProvider;
    }

    public boolean isConfigured() {
        return providerResolver().isWritable();
    }

    public String keyVersion() {
        requireConfigured();
        return providerResolver().activeKeyVersion();
    }

    public EncryptedValue encrypt(String value, String context) {
        requireConfigured();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cannot encrypt an empty secret");
        }
        return switch (providerResolver().mode()) {
            case LOCAL -> encryptLocally(value, context);
            case OPENBAO_TRANSIT, VAULT_TRANSIT -> transitProvider().encrypt(
                    providerResolver().requireWritableTransitConfig(),
                    value,
                    context);
            case SPLIT_KEY -> encryptWithSplitKey(value, context);
        };
    }

    public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion, String context) {
        requireConfigured();
        StoredSecretKeyReference reference = StoredSecretKeyReference.parse(keyVersion);
        if (LocalSecretKeyProvider.PROVIDER_ID.equals(reference.providerId())) {
            SecretKeyMaterial keyMaterial = providerResolver().resolveKey(keyVersion)
                    .orElseThrow(() -> new IllegalStateException("Stored secret was encrypted with an unavailable or unsupported key version"));
            return decryptLocally(ciphertextBase64, nonceBase64, keyMaterial, context);
        }
        if (SecretProviderMode.SPLIT_KEY.name().equals(reference.providerId())) {
            return decryptWithSplitKey(ciphertextBase64, keyVersion, context);
        }
        TransitProviderConfig transitConfig = providerResolver().transitConfigForStoredKeyVersion(keyVersion)
                .orElseThrow(() -> new IllegalStateException("Stored secret was encrypted with an unavailable or unsupported key version"));
        return transitProvider().decrypt(transitConfig, ciphertextBase64, context);
    }

    private EncryptedValue encryptWithSplitKey(String value, String context) {
        EncryptedValue innerEncrypted = encryptLocally(value, context);
        SplitKeyEnvelope envelope = new SplitKeyEnvelope(innerEncrypted.ciphertextBase64(), innerEncrypted.nonceBase64());
        return transitProvider().encrypt(
                providerResolver().requireWritableTransitConfig(),
                envelope.serialize(),
                context);
    }

    private String decryptWithSplitKey(String ciphertextBase64, String keyVersion, String context) {
        TransitProviderConfig transitConfig = providerResolver().transitConfigForStoredKeyVersion(keyVersion)
                .orElseThrow(() -> new IllegalStateException("Stored secret was encrypted with an unavailable or unsupported key version"));
        String serializedEnvelope = transitProvider().decrypt(transitConfig, ciphertextBase64, context);
        SplitKeyEnvelope envelope = SplitKeyEnvelope.parse(serializedEnvelope);
        SplitKeyStoredKeyVersion splitReference = SplitKeyStoredKeyVersion.parse(keyVersion);
        SecretKeyMaterial keyMaterial = providerResolver().resolveKey(keyVersion)
                .orElseThrow(() -> new IllegalStateException("Stored secret was encrypted with an unavailable or unsupported key version"));
        if (!keyMaterial.storedKeyVersion().equals(splitReference.localStoredKeyVersion())) {
            throw new IllegalStateException("Stored secret was encrypted with an unavailable or unsupported key version");
        }
        return decryptLocally(envelope.ciphertextBase64(), envelope.nonceBase64(), keyMaterial, context);
    }

    private EncryptedValue encryptLocally(String value, String context) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] aad = aad(context);
        SecretKeyMaterial keyMaterial = providerResolver().requireWritableLocalKey();

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

    private String decryptLocally(String ciphertextBase64, String nonceBase64, SecretKeyMaterial keyMaterial, String context) {
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
            throw new IllegalStateException(providerResolver().health().statusMessage());
        }
    }

    private SecretProviderResolver providerResolver() {
        if (secretProviderResolver == null) {
            SecretProviderResolver resolver = new SecretProviderResolver();
            resolver.setLocalSecretKeyProvider(localProvider());
            resolver.setTransitSecretProvider(transitProvider());
            secretProviderResolver = resolver;
        }
        return secretProviderResolver;
    }

    private TransitSecretProvider transitProvider() {
        if (transitSecretProvider == null) {
            transitSecretProvider = new TransitSecretProvider();
        }
        return transitSecretProvider;
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
