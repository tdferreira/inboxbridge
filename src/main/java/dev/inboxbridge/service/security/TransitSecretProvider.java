package dev.inboxbridge.service.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Speaks the Vault/OpenBao transit HTTP API for provider health and
 * encrypt/decrypt operations.
 */
@ApplicationScoped
public class TransitSecretProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern TRANSIT_CIPHERTEXT_VERSION_PATTERN = Pattern.compile("^[^:]+:v(\\d+):.+$");

    @Inject
    ObjectMapper objectMapper;

    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public SecretProviderHealth health(TransitProviderConfig config) {
        try {
            keyMetadata(config);
            return new SecretProviderHealth(
                    config.mode(),
                    config.providerId(),
                    true,
                    true,
                    config.mode().name() + " transit provider is ready.");
        } catch (Exception error) {
            return new SecretProviderHealth(
                    config.mode(),
                    config.providerId(),
                    false,
                    false,
                    "Unable to reach " + config.mode().name() + " transit provider: " + error.getMessage());
        }
    }

    public OptionalInt latestKeyVersion(TransitProviderConfig config) {
        try {
            JsonNode latestVersion = keyMetadata(config).path("latest_version");
            if (!latestVersion.canConvertToInt()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(latestVersion.asInt());
        } catch (Exception error) {
            return OptionalInt.empty();
        }
    }

    public SecretEncryptionService.EncryptedValue encrypt(TransitProviderConfig config, String value, String context) {
        try {
            String requestBody = objectMapper.writeValueAsString(new TransitEncryptRequest(
                    Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)),
                    Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8))));
            JsonNode response = sendJson(
                    "POST",
                    encryptUrl(config),
                    config.token(),
                    requestBody);
            String ciphertext = response.path("data").path("ciphertext").asText();
            if (ciphertext == null || ciphertext.isBlank()) {
                throw new IllegalStateException("Transit provider did not return ciphertext");
            }
            return new SecretEncryptionService.EncryptedValue(ciphertext, "");
        } catch (IOException e) {
            throw new IllegalStateException("Transit encryption request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transit encryption request was interrupted", e);
        }
    }

    public String decrypt(TransitProviderConfig config, String ciphertext, String context) {
        try {
            String requestBody = objectMapper.writeValueAsString(new TransitDecryptRequest(
                    ciphertext,
                    Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8))));
            JsonNode response = sendJson(
                    "POST",
                    decryptUrl(config),
                    config.token(),
                    requestBody);
            String plaintextBase64 = response.path("data").path("plaintext").asText();
            if (plaintextBase64 == null || plaintextBase64.isBlank()) {
                throw new IllegalStateException("Transit provider did not return plaintext");
            }
            return new String(Base64.getDecoder().decode(plaintextBase64), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Transit decryption request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transit decryption request was interrupted", e);
        }
    }

    public String rewrap(TransitProviderConfig config, String ciphertext, String context) {
        try {
            String requestBody = objectMapper.writeValueAsString(new TransitDecryptRequest(
                    ciphertext,
                    Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8))));
            JsonNode response = sendJson(
                    "POST",
                    rewrapUrl(config),
                    config.token(),
                    requestBody);
            String rewrappedCiphertext = response.path("data").path("ciphertext").asText();
            if (rewrappedCiphertext == null || rewrappedCiphertext.isBlank()) {
                throw new IllegalStateException("Transit provider did not return ciphertext");
            }
            return rewrappedCiphertext;
        } catch (IOException e) {
            throw new IllegalStateException("Transit rewrap request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transit rewrap request was interrupted", e);
        }
    }

    public OptionalInt ciphertextVersion(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher matcher = TRANSIT_CIPHERTEXT_VERSION_PATTERN.matcher(ciphertext.trim());
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(matcher.group(1)));
    }

    private JsonNode sendJson(String method, URI uri, String token, String body) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("X-Vault-Token", token);
        if (body != null) {
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private URI keyUrl(TransitProviderConfig config) {
        return URI.create(trimTrailingSlash(config.baseUrl()) + "/v1/" + normalizeSegment(config.mount()) + "/keys/" + normalizeSegment(config.keyName()));
    }

    private URI encryptUrl(TransitProviderConfig config) {
        return URI.create(trimTrailingSlash(config.baseUrl()) + "/v1/" + normalizeSegment(config.mount()) + "/encrypt/" + normalizeSegment(config.keyName()));
    }

    private URI decryptUrl(TransitProviderConfig config) {
        return URI.create(trimTrailingSlash(config.baseUrl()) + "/v1/" + normalizeSegment(config.mount()) + "/decrypt/" + normalizeSegment(config.keyName()));
    }

    private URI rewrapUrl(TransitProviderConfig config) {
        return URI.create(trimTrailingSlash(config.baseUrl()) + "/v1/" + normalizeSegment(config.mount()) + "/rewrap/" + normalizeSegment(config.keyName()));
    }

    private JsonNode keyMetadata(TransitProviderConfig config) throws IOException, InterruptedException {
        JsonNode response = sendJson(
                "GET",
                keyUrl(config),
                config.token(),
                null);
        JsonNode data = response.path("data");
        if (data.isMissingNode()) {
            throw new IllegalStateException("Transit provider did not return key metadata");
        }
        return data;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizeSegment(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record TransitEncryptRequest(String plaintext, String context) {
    }

    private record TransitDecryptRequest(String ciphertext, String context) {
    }
}
