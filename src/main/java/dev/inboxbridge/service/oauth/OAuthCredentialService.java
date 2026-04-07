package dev.inboxbridge.service.oauth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.service.SecretEncryptionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Stores provider OAuth credentials in PostgreSQL after encrypting token
 * material and preserving the metadata needed for automatic token renewal.
 */
@ApplicationScoped
public class OAuthCredentialService {

    public static final String GOOGLE_PROVIDER = "GOOGLE";
    public static final String MICROSOFT_PROVIDER = "MICROSOFT";

    @Inject
    OAuthCredentialRepository repository;

    @Inject
    SecretEncryptionService secretEncryptionService;

    public void setRepository(OAuthCredentialRepository repository) {
        this.repository = repository;
    }

    public void setSecretEncryptionService(SecretEncryptionService secretEncryptionService) {
        this.secretEncryptionService = secretEncryptionService;
    }

    public boolean secureStorageConfigured() {
        return secretEncryptionService.isConfigured();
    }

    public Optional<StoredOAuthCredential> findGoogleCredential() {
        return findGoogleCredential("gmail-destination");
    }

    @Transactional
    public Optional<StoredOAuthCredential> findGoogleCredential(String subjectKey) {
        return findCredential(GOOGLE_PROVIDER, subjectKey);
    }

    @Transactional
    public Optional<StoredOAuthCredential> findMicrosoftCredential(String sourceId) {
        return findCredential(MICROSOFT_PROVIDER, sourceId);
    }

    @Transactional
    public StoredOAuthCredential storeGoogleCredential(
            String refreshToken,
            String accessToken,
            Instant accessExpiresAt,
            String scope,
            String tokenType) {
        return storeGoogleCredential("gmail-destination", refreshToken, accessToken, accessExpiresAt, scope, tokenType);
    }

    @Transactional
    public StoredOAuthCredential storeGoogleCredential(
            String subjectKey,
            String refreshToken,
            String accessToken,
            Instant accessExpiresAt,
            String scope,
            String tokenType) {
        return storeCredential(GOOGLE_PROVIDER, subjectKey, refreshToken, accessToken, accessExpiresAt, scope, tokenType);
    }

    @Transactional
    public StoredOAuthCredential storeMicrosoftCredential(
            String sourceId,
            String refreshToken,
            String accessToken,
            Instant accessExpiresAt,
            String scope,
            String tokenType) {
        return storeCredential(MICROSOFT_PROVIDER, sourceId, refreshToken, accessToken, accessExpiresAt, scope, tokenType);
    }

    @Transactional
    public boolean deleteGoogleCredential(String subjectKey) {
        return deleteCredential(GOOGLE_PROVIDER, subjectKey);
    }

    @Transactional
    public long deleteGoogleCredentials(List<String> subjectKeys) {
        return repository.deleteByProviderAndSubjects(GOOGLE_PROVIDER, subjectKeys);
    }

    @Transactional
    public boolean deleteMicrosoftCredential(String sourceId) {
        return deleteCredential(MICROSOFT_PROVIDER, sourceId);
    }

    @Transactional
    public long deleteMicrosoftCredentials(List<String> sourceIds) {
        return repository.deleteByProviderAndSubjects(MICROSOFT_PROVIDER, sourceIds);
    }

    @Transactional
    public void clearMicrosoftAccessToken(String sourceId) {
        clearAccessToken(MICROSOFT_PROVIDER, sourceId);
    }

    private Optional<StoredOAuthCredential> findCredential(String provider, String subjectKey) {
        if (!secretEncryptionService.isConfigured()) {
            return Optional.empty();
        }

        return repository.findByProviderAndSubject(provider, subjectKey)
                .map(credential -> new StoredOAuthCredential(
                        provider,
                        subjectKey,
                        decryptNullable(credential.refreshTokenCiphertext, credential.refreshTokenNonce, credential.keyVersion, credentialContext(provider, subjectKey, "refresh")),
                        decryptNullable(credential.accessTokenCiphertext, credential.accessTokenNonce, credential.keyVersion, credentialContext(provider, subjectKey, "access")),
                        credential.accessExpiresAt,
                        credential.tokenScope,
                        credential.tokenType,
                        credential.updatedAt));
    }

    private StoredOAuthCredential storeCredential(
            String provider,
            String subjectKey,
            String refreshToken,
            String accessToken,
            Instant accessExpiresAt,
            String scope,
            String tokenType) {
        if (!secretEncryptionService.isConfigured()) {
            throw new IllegalStateException("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
        }

        OAuthCredential credential = repository.findByProviderAndSubject(provider, subjectKey)
                .orElseGet(OAuthCredential::new);

        Instant now = Instant.now();
        if (credential.id == null) {
            credential.provider = provider;
            credential.subjectKey = subjectKey;
            credential.createdAt = now;
        }
        String existingKeyVersion = credential.keyVersion;
        credential.keyVersion = secretEncryptionService.keyVersion();
        credential.updatedAt = now;
        credential.lastRefreshedAt = now;
        credential.accessExpiresAt = accessExpiresAt;
        credential.tokenScope = scope;
        credential.tokenType = tokenType;

        // Some providers only return a refresh token on the initial consent step,
        // so later refresh responses must retain the previously stored one.
        String effectiveRefreshToken = valueOrExisting(
                refreshToken,
                credential.refreshTokenCiphertext,
                credential.refreshTokenNonce,
                existingKeyVersion,
                credentialContext(provider, subjectKey, "refresh"));
        if (effectiveRefreshToken != null) {
            SecretEncryptionService.EncryptedValue encryptedRefresh = secretEncryptionService.encrypt(
                    effectiveRefreshToken,
                    credentialContext(provider, subjectKey, "refresh"));
            credential.refreshTokenCiphertext = encryptedRefresh.ciphertextBase64();
            credential.refreshTokenNonce = encryptedRefresh.nonceBase64();
        }

        if (accessToken != null && !accessToken.isBlank()) {
            SecretEncryptionService.EncryptedValue encryptedAccess = secretEncryptionService.encrypt(
                    accessToken,
                    credentialContext(provider, subjectKey, "access"));
            credential.accessTokenCiphertext = encryptedAccess.ciphertextBase64();
            credential.accessTokenNonce = encryptedAccess.nonceBase64();
        }

        repository.persist(credential);
        return new StoredOAuthCredential(
                provider,
                subjectKey,
                effectiveRefreshToken,
                accessToken,
                accessExpiresAt,
                scope,
                tokenType,
                credential.updatedAt);
    }

    private String valueOrExisting(
            String candidate,
            String ciphertext,
            String nonce,
            String keyVersion,
            String context) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return decryptNullable(ciphertext, nonce, keyVersion, context);
    }

    private String decryptNullable(String ciphertext, String nonce, String keyVersion, String context) {
        if (ciphertext == null || nonce == null) {
            return null;
        }
        return secretEncryptionService.decrypt(ciphertext, nonce, keyVersion, context);
    }

    private boolean deleteCredential(String provider, String subjectKey) {
        Optional<OAuthCredential> credential = repository.findByProviderAndSubject(provider, subjectKey);
        credential.ifPresent(repository::delete);
        return credential.isPresent();
    }

    private void clearAccessToken(String provider, String subjectKey) {
        Optional<OAuthCredential> credential = repository.findByProviderAndSubject(provider, subjectKey);
        if (credential.isEmpty()) {
            return;
        }
        OAuthCredential existing = credential.get();
        existing.accessTokenCiphertext = null;
        existing.accessTokenNonce = null;
        existing.accessExpiresAt = null;
        existing.updatedAt = Instant.now();
        repository.persist(existing);
    }

    private String credentialContext(String provider, String subjectKey, String tokenKind) {
        return provider + ":" + subjectKey + ":" + tokenKind;
    }

    public record StoredOAuthCredential(
            String provider,
            String subjectKey,
            String refreshToken,
            String accessToken,
            Instant accessExpiresAt,
            String scope,
            String tokenType,
            Instant updatedAt) {
    }
}
