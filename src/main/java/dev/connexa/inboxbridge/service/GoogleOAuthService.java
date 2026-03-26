package dev.connexa.inboxbridge.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.connexa.inboxbridge.config.BridgeConfig;
import dev.connexa.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.connexa.inboxbridge.dto.GoogleTokenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleOAuthService {

    private static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/gmail.insert https://www.googleapis.com/auth/gmail.labels";

    @Inject
    BridgeConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public String buildAuthorizationUrl() {
        String redirectUri = urlEncode(config.gmail().redirectUri());
        String clientId = urlEncode(config.gmail().clientId());
        String scope = urlEncode(OAUTH_SCOPE);
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&access_type=offline"
                + "&prompt=consent"
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=" + scope;
    }

    public GoogleTokenExchangeResponse exchangeAuthorizationCode(String code) {
        String body = formBody(Map.of(
                "code", code,
                "client_id", config.gmail().clientId(),
                "client_secret", config.gmail().clientSecret(),
                "redirect_uri", config.gmail().redirectUri(),
                "grant_type", "authorization_code"));
        GoogleTokenResponse token = executeTokenRequest(body);
        requireRefreshToken(token.refreshToken(), "Google");
        return storeExchangeResult(token);
    }

    public String getAccessToken() {
        CachedToken current = cachedToken.get();
        if (current != null && Instant.now().isBefore(current.expiresAt.minusSeconds(30))) {
            return current.accessToken;
        }

        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findGoogleCredential().orElse(null);
            if (stored != null
                    && stored.accessToken() != null
                    && stored.accessExpiresAt() != null
                    && Instant.now().isBefore(stored.accessExpiresAt().minusSeconds(30))) {
                cachedToken.set(new CachedToken(stored.accessToken(), stored.accessExpiresAt()));
                return stored.accessToken();
            }
        }

        synchronized (this) {
            CachedToken latest = cachedToken.get();
            if (latest != null && Instant.now().isBefore(latest.expiresAt.minusSeconds(30))) {
                return latest.accessToken;
            }

            GoogleTokenResponse refreshed = refreshAccessToken();
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn() == null ? 300 : refreshed.expiresIn());
            cachedToken.set(new CachedToken(refreshed.accessToken(), expiresAt));
            persistRefreshedToken(refreshed, expiresAt);
            return refreshed.accessToken();
        }
    }

    private GoogleTokenResponse refreshAccessToken() {
        String body = formBody(Map.of(
                "client_id", config.gmail().clientId(),
                "client_secret", config.gmail().clientSecret(),
                "refresh_token", resolveRefreshToken(),
                "grant_type", "refresh_token"));
        return executeTokenRequest(body);
    }

    private String resolveRefreshToken() {
        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findGoogleCredential().orElse(null);
            if (stored != null && stored.refreshToken() != null && !stored.refreshToken().isBlank()) {
                return stored.refreshToken();
            }
        }
        return config.gmail().refreshToken();
    }

    private GoogleTokenExchangeResponse storeExchangeResult(GoogleTokenResponse token) {
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        cachedToken.set(new CachedToken(token.accessToken(), expiresAt));

        if (!oAuthCredentialService.secureStorageConfigured()) {
            return new GoogleTokenExchangeResponse(
                    false,
                    true,
                    token.refreshToken(),
                    "env:GOOGLE_REFRESH_TOKEN",
                    token.scope(),
                    token.tokenType(),
                    expiresAt,
                    "Set bridge.security.token-encryption-key to enable automatic encrypted token storage. Until then, keep using GOOGLE_REFRESH_TOKEN in .env.");
        }

        oAuthCredentialService.storeGoogleCredential(
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());

        return new GoogleTokenExchangeResponse(
                true,
                false,
                null,
                "db:GOOGLE:gmail-destination",
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in the database. Future Google access token refreshes will be handled automatically.");
    }

    private void persistRefreshedToken(GoogleTokenResponse token, Instant expiresAt) {
        if (!oAuthCredentialService.secureStorageConfigured()) {
            return;
        }
        oAuthCredentialService.storeGoogleCredential(
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());
    }

    private void requireRefreshToken(String refreshToken, String provider) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException(
                    provider + " did not return a refresh token. Ensure offline access is granted and repeat consent.");
        }
    }

    private GoogleTokenResponse executeTokenRequest(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Google token request failed with status " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), GoogleTokenResponse.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google token request failed", e);
        } catch (IOException e) {
            throw new IllegalStateException("Google token request failed", e);
        }
    }

    private String formBody(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
