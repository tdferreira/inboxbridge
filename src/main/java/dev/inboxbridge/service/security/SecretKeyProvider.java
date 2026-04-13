package dev.inboxbridge.service.security;

import java.util.Optional;

/**
 * Resolves the active secret-encryption key and any still-trusted legacy keys
 * needed to decrypt previously stored secrets during rotation windows.
 */
public interface SecretKeyProvider {

    String providerId();

    boolean isConfigured();

    SecretKeyMaterial activeKey();

    Optional<SecretKeyMaterial> resolveKey(String storedKeyVersion);
}
