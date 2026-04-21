package dev.inboxbridge.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Boots live OpenBao and Vault dev containers and wires Quarkus to a transit
 * provider mode so admin/API integration tests can exercise the real
 * secret-management HTTP flow with production-style provider IO.
 */
public final class SecretTransitProvidersTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String LOCAL_ACTIVE_KEY = "fedcba9876543210fedcba9876543210";
    public static final String LOCAL_LEGACY_KEY = "0123456789abcdef0123456789abcdef";
    public static final String LOCAL_ACTIVE_KEY_BASE64 = java.util.Base64.getEncoder().encodeToString(LOCAL_ACTIVE_KEY.getBytes());
    public static final String LOCAL_LEGACY_KEY_BASE64 = java.util.Base64.getEncoder().encodeToString(LOCAL_LEGACY_KEY.getBytes());
    public static final String LOCAL_ACTIVE_KEY_ID = "v2";
    public static final String LOCAL_LEGACY_KEY_ID = "v1";
    public static final String OPENBAO_TOKEN = "openbao-root";
    public static final String VAULT_TOKEN = "vault-root";
    public static final String TRANSIT_MOUNT = "transit";
    public static final String OPENBAO_KEY = "inboxbridge-openbao";
    public static final String VAULT_KEY = "inboxbridge-vault";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private GenericContainer<?> openbao;
    private GenericContainer<?> vault;

    @Override
    public Map<String, String> start() {
        openbao = new GenericContainer<>(DockerImageName.parse(
                System.getProperty("inboxbridge.test.openbao.image", "openbao/openbao:latest")))
                .withEnv("BAO_DEV_ROOT_TOKEN_ID", OPENBAO_TOKEN)
                .withExposedPorts(8200);
        vault = new GenericContainer<>(DockerImageName.parse(
                System.getProperty("inboxbridge.test.vault.image", "hashicorp/vault:1.19")))
                .withCommand("server", "-dev", "-dev-root-token-id=" + VAULT_TOKEN, "-dev-listen-address=0.0.0.0:8200")
                .withExposedPorts(8200);

        openbao.start();
        vault.start();

        TransitProviderSettings openbaoSettings = new TransitProviderSettings(
                "http://" + openbao.getHost() + ":" + openbao.getMappedPort(8200),
                OPENBAO_TOKEN,
                TRANSIT_MOUNT,
                OPENBAO_KEY);
        TransitProviderSettings vaultSettings = new TransitProviderSettings(
                "http://" + vault.getHost() + ":" + vault.getMappedPort(8200),
                VAULT_TOKEN,
                TRANSIT_MOUNT,
                VAULT_KEY);

        ensureTransitKey(openbaoSettings);
        ensureTransitKey(vaultSettings);

        return Map.ofEntries(
                Map.entry("security.provider-mode", "VAULT_TRANSIT"),
                Map.entry("security.token-encryption-key", LOCAL_ACTIVE_KEY_BASE64),
                Map.entry("security.token-encryption-key-id", LOCAL_ACTIVE_KEY_ID),
                Map.entry("security.token-encryption-legacy-keys", LOCAL_LEGACY_KEY_ID + ":" + LOCAL_LEGACY_KEY_BASE64),
                Map.entry("security.openbao-url", openbaoSettings.baseUrl()),
                Map.entry("security.openbao-token", openbaoSettings.token()),
                Map.entry("security.openbao-mount", openbaoSettings.mount()),
                Map.entry("security.openbao-key", openbaoSettings.keyName()),
                Map.entry("security.vault-url", vaultSettings.baseUrl()),
                Map.entry("security.vault-token", vaultSettings.token()),
                Map.entry("security.vault-mount", vaultSettings.mount()),
                Map.entry("security.vault-key", vaultSettings.keyName()),
                Map.entry("inboxbridge.poll-enabled", "false"),
                Map.entry("inboxbridge.security.secret-management.reencryption-cooldown", "PT0S"),
                Map.entry("inboxbridge.security.secret-management.allow-immediate-reencrypt-override", "true"),
                Map.entry("inboxbridge.security.secret-management.reauthentication-ttl", "PT10M"));
    }

    @Override
    public void stop() {
        if (openbao != null) {
            openbao.stop();
        }
        if (vault != null) {
            vault.stop();
        }
    }

    private static void ensureTransitKey(TransitProviderSettings settings) {
        ensureTransitMount(settings);
        postJson(
                settings.baseUrl() + "/v1/" + settings.mount() + "/keys/" + settings.keyName(),
                settings.token(),
                """
                {"derived":true}
                """);
    }

    private static void ensureTransitMount(TransitProviderSettings settings) {
        HttpResponse<String> response = postJson(
                settings.baseUrl() + "/v1/sys/mounts/" + settings.mount(),
                settings.token(),
                """
                {"type":"transit"}
                """);
        if (response.statusCode() / 100 == 2) {
            return;
        }
        if (response.statusCode() == 400 && response.body() != null && response.body().contains("path is already in use")) {
            return;
        }
        throw new IllegalStateException("Unable to mount transit engine for " + settings.baseUrl() + ": HTTP "
                + response.statusCode() + " " + response.body());
    }

    private static HttpResponse<String> postJson(String url, String token, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String body = response.body() == null ? "" : response.body();
                if (response.statusCode() == 400 && body.contains("path is already in use")) {
                    return response;
                }
                throw new IllegalStateException("Transit provider request failed: HTTP " + response.statusCode() + " " + body);
            }
            return response;
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException("Unable to provision transit provider test fixtures", error);
        }
    }

    private record TransitProviderSettings(
            String baseUrl,
            String token,
            String mount,
            String keyName) {
    }
}
