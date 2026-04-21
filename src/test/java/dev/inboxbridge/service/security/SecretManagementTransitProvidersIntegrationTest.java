package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.dto.SecretReencryptionRequest;
import dev.inboxbridge.dto.SecretReencryptionResultView;

@Testcontainers(disabledWithoutDocker = true)
class SecretManagementTransitProvidersIntegrationTest {

    private static final String OPENBAO_TOKEN = "openbao-root";
    private static final String VAULT_TOKEN = "vault-root";
    private static final String TRANSIT_MOUNT = "transit";
    private static final String OPENBAO_KEY = "inboxbridge-openbao";
    private static final String VAULT_KEY = "inboxbridge-vault";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static final GenericContainer<?> openbao = new GenericContainer<>(
            DockerImageName.parse(System.getProperty(
                    "inboxbridge.test.openbao.image",
                    "openbao/openbao:latest")))
            .withEnv("BAO_DEV_ROOT_TOKEN_ID", OPENBAO_TOKEN)
            .withExposedPorts(8200);

    @Container
    static final GenericContainer<?> vault = new GenericContainer<>(
            DockerImageName.parse(System.getProperty(
                    "inboxbridge.test.vault.image",
                    "hashicorp/vault:1.19")))
            .withCommand("server", "-dev", "-dev-root-token-id=" + VAULT_TOKEN, "-dev-listen-address=0.0.0.0:8200")
            .withExposedPorts(8200);

    private static SecretManagementTestFixtureSupport.TransitProviderSettings openbaoSettings;
    private static SecretManagementTestFixtureSupport.TransitProviderSettings vaultSettings;

    @BeforeAll
    static void configureTransitProviders() throws Exception {
        openbaoSettings = new SecretManagementTestFixtureSupport.TransitProviderSettings(
                SecretProviderMode.OPENBAO_TRANSIT,
                "http://" + openbao.getHost() + ":" + openbao.getMappedPort(8200),
                OPENBAO_TOKEN,
                TRANSIT_MOUNT,
                OPENBAO_KEY);
        vaultSettings = new SecretManagementTestFixtureSupport.TransitProviderSettings(
                SecretProviderMode.VAULT_TRANSIT,
                "http://" + vault.getHost() + ":" + vault.getMappedPort(8200),
                VAULT_TOKEN,
                TRANSIT_MOUNT,
                VAULT_KEY);
        ensureTransitKey(openbaoSettings);
        ensureTransitKey(vaultSettings);
    }

    @Test
    void reencryptsStoredSecretsFromLocalToOpenBaoTransit() {
        assertMigration(
                SecretProviderMode.LOCAL,
                SecretProviderMode.OPENBAO_TRANSIT,
                "OPENBAO_TRANSIT:" + OPENBAO_KEY);
    }

    @Test
    void reencryptsStoredSecretsFromOpenBaoTransitBackToLocal() {
        assertMigration(
                SecretProviderMode.OPENBAO_TRANSIT,
                SecretProviderMode.LOCAL,
                "LOCAL:v2");
    }

    @Test
    void reencryptsStoredSecretsFromLocalToVaultTransit() {
        assertMigration(
                SecretProviderMode.LOCAL,
                SecretProviderMode.VAULT_TRANSIT,
                "VAULT_TRANSIT:" + VAULT_KEY);
    }

    @Test
    void reencryptsStoredSecretsFromVaultTransitBackToLocal() {
        assertMigration(
                SecretProviderMode.VAULT_TRANSIT,
                SecretProviderMode.LOCAL,
                "LOCAL:v2");
    }

    @Test
    void reencryptsStoredSecretsFromOpenBaoTransitToVaultTransit() {
        assertMigration(
                SecretProviderMode.OPENBAO_TRANSIT,
                SecretProviderMode.VAULT_TRANSIT,
                "VAULT_TRANSIT:" + VAULT_KEY);
    }

    @Test
    void reencryptsStoredSecretsFromVaultTransitToOpenBaoTransit() {
        assertMigration(
                SecretProviderMode.VAULT_TRANSIT,
                SecretProviderMode.OPENBAO_TRANSIT,
                "OPENBAO_TRANSIT:" + OPENBAO_KEY);
    }

    private void assertMigration(
            SecretProviderMode sourceMode,
            SecretProviderMode targetMode,
            String expectedActiveKeyVersion) {
        TransitSecretProvider transitSecretProvider = realTransitSecretProvider();
        SecretManagementTestFixtureSupport.SecretManagementFixture fixture =
                SecretManagementTestFixtureSupport.createFixture(
                        sourceMode,
                        transitSecretProvider,
                        openbaoSettings,
                        vaultSettings);

        SecretManagementTestFixtureSupport.applyActiveMode(
                fixture.resolver(),
                targetMode,
                openbaoSettings,
                vaultSettings);

        SecretReencryptionResultView result = fixture.service().reencryptAllStoredSecrets(
                SecretManagementTestFixtureSupport.adminUser(),
                new SecretReencryptionRequest(false, false, false, false));
        SecretManagementStatusView status = fixture.service().status();

        assertEquals("COMPLETED", result.operationStatus());
        assertEquals(expectedActiveKeyVersion, result.activeKeyVersion());
        assertEquals(5, result.totalRecordsUpdated());
        assertEquals(5, result.totalSecretValuesReencrypted());
        assertEquals(5, result.totalFullReencryptionCount());
        assertEquals(0, result.totalMetadataRewrapCount());
        assertEquals(expectedActiveKeyVersion, status.activeKeyVersion());
        assertEquals(5, status.protectedRecordCount());
        assertEquals(5, status.activeKeyRecordCount());
        assertEquals(0, status.nonActiveKeyRecordCount());
        assertEquals(0, status.unavailableKeyRecordCount());
        assertTrue(status.safeToRetireLegacyKeys());
        assertTrue(status.legacyKeyRetirementReady());
    }

    private static TransitSecretProvider realTransitSecretProvider() {
        TransitSecretProvider provider = new TransitSecretProvider();
        provider.setObjectMapper(OBJECT_MAPPER);
        provider.setHttpClient(HTTP_CLIENT);
        return provider;
    }

    private static void ensureTransitKey(SecretManagementTestFixtureSupport.TransitProviderSettings settings) throws Exception {
        ensureTransitMount(settings);
        postJson(
                settings.baseUrl() + "/v1/" + settings.mount() + "/keys/" + settings.keyName(),
                settings.token(),
                """
                {"derived":true}
                """);
    }

    private static void ensureTransitMount(SecretManagementTestFixtureSupport.TransitProviderSettings settings) throws Exception {
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
        throw new IllegalStateException("Unable to mount transit engine for " + settings.mode() + ": HTTP "
                + response.statusCode() + " " + response.body());
    }

    private static HttpResponse<String> postJson(String url, String token, String jsonBody) throws IOException, InterruptedException {
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
            // Transit mount creation is idempotent from the test's perspective.
            if (response.statusCode() == 400 && body.contains("path is already in use")) {
                return response;
            }
            throw new IllegalStateException("Transit provider request failed: HTTP " + response.statusCode() + " " + body);
        }
        return response;
    }
}
