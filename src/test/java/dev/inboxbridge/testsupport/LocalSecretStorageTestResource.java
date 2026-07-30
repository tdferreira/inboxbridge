package dev.inboxbridge.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Supplies an explicit local encryption key to Quarkus tests that persist or
 * inspect encrypted secrets without requiring a live transit provider.
 */
public final class LocalSecretStorageTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String ACTIVE_KEY = "fedcba9876543210fedcba9876543210";

    @Override
    public Map<String, String> start() {
        return Map.of(
                "security.provider-mode", "LOCAL",
                "security.token-encryption-key", Base64.getEncoder().encodeToString(
                        ACTIVE_KEY.getBytes(StandardCharsets.UTF_8)),
                "security.token-encryption-key-id", "test-v1");
    }

    @Override
    public void stop() {
        // No external resource is created.
    }
}
