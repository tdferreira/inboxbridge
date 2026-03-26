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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.connexa.inboxbridge.config.BridgeConfig;
import dev.connexa.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.connexa.inboxbridge.dto.MicrosoftTokenExchangeResponse;
import dev.connexa.inboxbridge.dto.MicrosoftTokenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implements Microsoft OAuth for source mailbox authentication.
 *
 * <p>The service supports a browser-first auth-code flow, encrypted refresh-token
 * storage, in-memory access-token caching, and automatic source-token renewal
 * for IMAP / POP connections that authenticate with XOAUTH2.</p>
 */
@ApplicationScoped
public class MicrosoftOAuthService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_SKEW = Duration.ofSeconds(30);

    @Inject
    BridgeConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private final ConcurrentMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    public List<MicrosoftOAuthSourceOption> listMicrosoftOAuthSources() {
        return config.sources().stream()
                .filter(source -> source.oauthProvider() == BridgeConfig.OAuthProvider.MICROSOFT)
                .map(source -> new MicrosoftOAuthSourceOption(
                        source.id(),
                        source.protocol().name(),
                        source.enabled()))
                .toList();
    }

    public String buildAuthorizationUrl(String sourceId) {
        requireConfiguredClient();
        SourceRef sourceRef = requireMicrosoftOAuthSource(sourceId);
        purgeExpiredStates();

        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(sourceRef.source().id(), sourceRef.index(), Instant.now().plus(STATE_TTL)));

        return authorizationEndpoint()
                + "?response_type=code"
                + "&response_mode=query"
                + "&prompt=consent"
                + "&client_id=" + urlEncode(config.microsoft().clientId())
                + "&redirect_uri=" + urlEncode(config.microsoft().redirectUri())
                + "&scope=" + urlEncode(authorizationScopes(sourceRef.source()))
                + "&state=" + urlEncode(state);
    }

    public CallbackValidation validateCallback(String state) {
        purgeExpiredStates();
        PendingState pendingState = pendingStates.remove(state);
        if (pendingState == null || Instant.now().isAfter(pendingState.expiresAt())) {
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }
        return new CallbackValidation(
                pendingState.sourceId(),
                "BRIDGE_SOURCES_" + pendingState.index() + "__OAUTH_REFRESH_TOKEN");
    }

    public MicrosoftTokenExchangeResponse exchangeAuthorizationCode(String sourceId, String code) {
        requireConfiguredClient();
        SourceRef sourceRef = requireMicrosoftOAuthSource(sourceId);
        String body = formBody(Map.of(
                "client_id", config.microsoft().clientId(),
                "client_secret", config.microsoft().clientSecret(),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", config.microsoft().redirectUri(),
                "scope", authorizationScopes(sourceRef.source())));
        MicrosoftTokenResponse token = executeTokenRequest(body);
        requireRefreshToken(token.refreshToken(), "Microsoft");
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        cachedTokens.put(sourceRef.source().id(), new CachedToken(token.accessToken(), expiresAt));

        if (!oAuthCredentialService.secureStorageConfigured()) {
            return new MicrosoftTokenExchangeResponse(
                    sourceRef.source().id(),
                    false,
                    true,
                    token.refreshToken(),
                    "env:BRIDGE_SOURCES_" + sourceRef.index() + "__OAUTH_REFRESH_TOKEN",
                    token.scope(),
                    token.tokenType(),
                    expiresAt,
                    "Set bridge.security.token-encryption-key to enable automatic encrypted token storage. Until then, keep using BRIDGE_SOURCES_" + sourceRef.index() + "__OAUTH_REFRESH_TOKEN in .env.");
        }

        oAuthCredentialService.storeMicrosoftCredential(
                sourceRef.source().id(),
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());

        return new MicrosoftTokenExchangeResponse(
                sourceRef.source().id(),
                true,
                false,
                null,
                "db:MICROSOFT:" + sourceRef.source().id(),
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in the database. Future Microsoft token refreshes will be handled automatically.");
    }

    public String getAccessToken(BridgeConfig.Source source) {
        requireConfiguredClient();
        SourceRef sourceRef = requireMicrosoftOAuthSource(source.id());
        CachedToken current = cachedTokens.get(sourceRef.source().id());
        if (current != null && Instant.now().isBefore(current.expiresAt().minus(TOKEN_SKEW))) {
            return current.accessToken();
        }

        if (oAuthCredentialService.secureStorageConfigured()) {
            // Reuse a still-valid encrypted access token before asking Microsoft
            // for a refresh, which reduces external token churn and failure surface.
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findMicrosoftCredential(sourceRef.source().id()).orElse(null);
            if (stored != null
                    && stored.accessToken() != null
                    && stored.accessExpiresAt() != null
                    && Instant.now().isBefore(stored.accessExpiresAt().minus(TOKEN_SKEW))) {
                cachedTokens.put(sourceRef.source().id(), new CachedToken(stored.accessToken(), stored.accessExpiresAt()));
                return stored.accessToken();
            }
        }

        synchronized (this) {
            CachedToken latest = cachedTokens.get(sourceRef.source().id());
            if (latest != null && Instant.now().isBefore(latest.expiresAt().minus(TOKEN_SKEW))) {
                return latest.accessToken();
            }

            MicrosoftTokenResponse refreshed = refreshAccessToken(sourceRef.source());
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn() == null ? 300 : refreshed.expiresIn());
            cachedTokens.put(sourceRef.source().id(), new CachedToken(refreshed.accessToken(), expiresAt));
            persistRefreshedToken(sourceRef.source().id(), refreshed, expiresAt);
            return refreshed.accessToken();
        }
    }

    private MicrosoftTokenResponse refreshAccessToken(BridgeConfig.Source source) {
        String refreshToken = resolveRefreshToken(source);
        if (refreshToken.isBlank()) {
            throw new IllegalStateException("Source " + source.id() + " is configured for OAuth2 but has no refresh token");
        }

        String body = formBody(Map.of(
                "client_id", config.microsoft().clientId(),
                "client_secret", config.microsoft().clientSecret(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "scope", protocolScope(source)));
        return executeTokenRequest(body);
    }

    private String resolveRefreshToken(BridgeConfig.Source source) {
        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findMicrosoftCredential(source.id()).orElse(null);
            if (stored != null && stored.refreshToken() != null && !stored.refreshToken().isBlank()) {
                return stored.refreshToken();
            }
        }
        return source.oauthRefreshToken().orElse("");
    }

    private void persistRefreshedToken(String sourceId, MicrosoftTokenResponse token, Instant expiresAt) {
        if (!oAuthCredentialService.secureStorageConfigured()) {
            return;
        }
        oAuthCredentialService.storeMicrosoftCredential(
                sourceId,
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());
    }

    private void requireRefreshToken(String refreshToken, String provider) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException(
                    provider + " did not return a refresh token. Ensure offline_access is granted and repeat consent.");
        }
    }

    private MicrosoftTokenResponse executeTokenRequest(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Microsoft token request failed with status " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), MicrosoftTokenResponse.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Microsoft token request failed", e);
        } catch (IOException e) {
            throw new IllegalStateException("Microsoft token request failed", e);
        }
    }

    private void requireConfiguredClient() {
        if (config.microsoft().clientId().isBlank() || "replace-me".equals(config.microsoft().clientId())) {
            throw new IllegalStateException("Microsoft OAuth client id is not configured");
        }
        if (config.microsoft().clientSecret().isBlank() || "replace-me".equals(config.microsoft().clientSecret())) {
            throw new IllegalStateException("Microsoft OAuth client secret is not configured");
        }
    }

    private SourceRef requireMicrosoftOAuthSource(String sourceId) {
        for (int i = 0; i < config.sources().size(); i++) {
            BridgeConfig.Source source = config.sources().get(i);
            if (!source.id().equals(sourceId)) {
                continue;
            }
            if (source.authMethod() != BridgeConfig.AuthMethod.OAUTH2) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for OAuth2");
            }
            if (source.oauthProvider() != BridgeConfig.OAuthProvider.MICROSOFT) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for Microsoft OAuth");
            }
            return new SourceRef(source, i);
        }
        throw new IllegalArgumentException("Unknown source id: " + sourceId);
    }

    private String authorizationScopes(BridgeConfig.Source source) {
        return "offline_access " + protocolScope(source);
    }

    private String protocolScope(BridgeConfig.Source source) {
        return switch (source.protocol()) {
            case IMAP -> "https://outlook.office.com/IMAP.AccessAsUser.All";
            case POP3 -> "https://outlook.office.com/POP.AccessAsUser.All";
        };
    }

    private String authorizationEndpoint() {
        return "https://login.microsoftonline.com/" + config.microsoft().tenant() + "/oauth2/v2.0/authorize";
    }

    private String tokenEndpoint() {
        return "https://login.microsoftonline.com/" + config.microsoft().tenant() + "/oauth2/v2.0/token";
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

    private void purgeExpiredStates() {
        Instant now = Instant.now();
        pendingStates.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    private record PendingState(String sourceId, int index, Instant expiresAt) {
    }

    private record SourceRef(BridgeConfig.Source source, int index) {
    }

    public record CallbackValidation(String sourceId, String configKey) {
    }
}
