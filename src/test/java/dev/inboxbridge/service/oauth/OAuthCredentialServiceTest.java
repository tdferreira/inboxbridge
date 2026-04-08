package dev.inboxbridge.service.oauth;

import dev.inboxbridge.service.security.SecretEncryptionService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;

class OAuthCredentialServiceTest {

    @Test
    void storesAndLoadsEncryptedMicrosoftCredentials() {
        InMemoryOAuthCredentialRepository repository = new InMemoryOAuthCredentialRepository();
        OAuthCredentialService service = configuredService(repository);

        Instant expiresAt = Instant.parse("2026-03-26T13:00:00Z");
        OAuthCredentialService.StoredOAuthCredential stored = service.storeMicrosoftCredential(
                "outlook-main-imap",
                "refresh-token-1",
                "access-token-1",
                expiresAt,
                "offline_access imap",
                "Bearer");

        OAuthCredential persisted = repository.get("MICROSOFT", "outlook-main-imap");
        OAuthCredentialService.StoredOAuthCredential loaded = service.findMicrosoftCredential("outlook-main-imap").orElseThrow();

        assertEquals("refresh-token-1", stored.refreshToken());
        assertNotEquals("refresh-token-1", persisted.refreshTokenCiphertext);
        assertNotEquals("access-token-1", persisted.accessTokenCiphertext);
        assertEquals("refresh-token-1", loaded.refreshToken());
        assertEquals("access-token-1", loaded.accessToken());
        assertEquals(expiresAt, loaded.accessExpiresAt());
    }

    @Test
    void preservesExistingRefreshTokenWhenProviderOnlyReturnsNewAccessToken() {
        InMemoryOAuthCredentialRepository repository = new InMemoryOAuthCredentialRepository();
        OAuthCredentialService service = configuredService(repository);

        service.storeGoogleCredential(
                "google-refresh-1",
                "google-access-1",
                Instant.parse("2026-03-26T13:00:00Z"),
                "gmail.insert",
                "Bearer");

        service.storeGoogleCredential(
                null,
                "google-access-2",
                Instant.parse("2026-03-26T14:00:00Z"),
                "gmail.insert",
                "Bearer");

        OAuthCredentialService.StoredOAuthCredential loaded = service.findGoogleCredential().orElseThrow();

        assertEquals("google-refresh-1", loaded.refreshToken());
        assertEquals("google-access-2", loaded.accessToken());
        assertEquals(Instant.parse("2026-03-26T14:00:00Z"), loaded.accessExpiresAt());
    }

    @Test
    void reportsSecureStorageConfiguredWhenEncryptionKeyExists() {
        OAuthCredentialService service = configuredService(new InMemoryOAuthCredentialRepository());

        assertTrue(service.secureStorageConfigured());
    }

    private OAuthCredentialService configuredService(InMemoryOAuthCredentialRepository repository) {
        OAuthCredentialService service = new OAuthCredentialService();
        service.repository = repository;
        service.secretEncryptionService = configuredEncryptionService();
        return service;
    }

    private SecretEncryptionService configuredEncryptionService() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.setTokenEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        service.setTokenEncryptionKeyId("v1");
        return service;
    }

    private static class InMemoryOAuthCredentialRepository extends OAuthCredentialRepository {
        private final Map<String, OAuthCredential> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public Optional<OAuthCredential> findByProviderAndSubject(String provider, String subjectKey) {
            return Optional.ofNullable(store.get(key(provider, subjectKey)));
        }

        @Override
        public void persist(OAuthCredential credential) {
            if (credential.id == null) {
                credential.id = sequence++;
            }
            store.put(key(credential.provider, credential.subjectKey), copyOf(credential));
        }

        OAuthCredential get(String provider, String subjectKey) {
            return store.get(key(provider, subjectKey));
        }

        private String key(String provider, String subjectKey) {
            return provider + "::" + subjectKey;
        }

        private OAuthCredential copyOf(OAuthCredential credential) {
            OAuthCredential copy = new OAuthCredential();
            copy.id = credential.id;
            copy.provider = credential.provider;
            copy.subjectKey = credential.subjectKey;
            copy.keyVersion = credential.keyVersion;
            copy.refreshTokenCiphertext = credential.refreshTokenCiphertext;
            copy.refreshTokenNonce = credential.refreshTokenNonce;
            copy.accessTokenCiphertext = credential.accessTokenCiphertext;
            copy.accessTokenNonce = credential.accessTokenNonce;
            copy.accessExpiresAt = credential.accessExpiresAt;
            copy.tokenScope = credential.tokenScope;
            copy.tokenType = credential.tokenType;
            copy.createdAt = credential.createdAt;
            copy.updatedAt = credential.updatedAt;
            copy.lastRefreshedAt = credential.lastRefreshedAt;
            return copy;
        }
    }
}
