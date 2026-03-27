package dev.inboxbridge.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.dto.GoogleTokenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleOAuthService {

    private static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/gmail.insert https://www.googleapis.com/auth/gmail.labels";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    @Inject
    BridgeConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ConcurrentMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    public String buildAuthorizationUrl() {
        return buildAuthorizationUrl(systemProfile());
    }

    public GoogleOAuthProfile systemProfileForCallbacks() {
        return systemProfile();
    }

    public String buildAuthorizationUrl(GoogleOAuthProfile profile) {
        String redirectUri = urlEncode(profile.redirectUri());
        String clientId = urlEncode(profile.clientId());
        String scope = urlEncode(OAUTH_SCOPE);
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&access_type=offline"
                + "&prompt=consent"
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=" + scope;
    }

    public String buildAuthorizationUrlWithState(GoogleOAuthProfile profile, String targetLabel) {
        purgeExpiredStates();
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(profile, targetLabel, Instant.now().plus(STATE_TTL)));
        return buildAuthorizationUrl(profile) + "&state=" + urlEncode(state);
    }

    public GoogleTokenExchangeResponse exchangeAuthorizationCode(String code) {
        return exchangeAuthorizationCode(systemProfile(), code);
    }

    public GoogleTokenExchangeResponse exchangeAuthorizationCode(String state, String code) {
        PendingState pendingState = requirePendingState(state, false);
        try {
            return exchangeAuthorizationCode(pendingState.profile(), code);
        } finally {
            pendingStates.remove(state);
        }
    }

    public GoogleTokenExchangeResponse exchangeAuthorizationCode(GoogleOAuthProfile profile, String code) {
        String body = formBody(Map.of(
                "code", code,
                "client_id", profile.clientId(),
                "client_secret", profile.clientSecret(),
                "redirect_uri", profile.redirectUri(),
                "grant_type", "authorization_code"));
        GoogleTokenResponse token = executeTokenRequest(body);
        requireRefreshToken(token.refreshToken(), "Google");
        validateGrantedScopes(token.scope());
        return storeExchangeResult(profile, token);
    }

    public String getAccessToken() {
        return getAccessToken(systemProfile());
    }

    public String getAccessToken(GoogleOAuthProfile profile) {
        CachedToken current = cachedTokens.get(profile.subjectKey());
        if (current != null && Instant.now().isBefore(current.expiresAt.minusSeconds(30))) {
            return current.accessToken;
        }

        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findGoogleCredential(profile.subjectKey()).orElse(null);
            if (stored != null
                    && stored.accessToken() != null
                    && stored.accessExpiresAt() != null
                    && Instant.now().isBefore(stored.accessExpiresAt().minusSeconds(30))) {
                cachedTokens.put(profile.subjectKey(), new CachedToken(stored.accessToken(), stored.accessExpiresAt()));
                return stored.accessToken();
            }
        }

        synchronized (this) {
            CachedToken latest = cachedTokens.get(profile.subjectKey());
            if (latest != null && Instant.now().isBefore(latest.expiresAt.minusSeconds(30))) {
                return latest.accessToken;
            }

            GoogleTokenResponse refreshed = refreshAccessToken(profile);
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn() == null ? 300 : refreshed.expiresIn());
            cachedTokens.put(profile.subjectKey(), new CachedToken(refreshed.accessToken(), expiresAt));
            persistRefreshedToken(profile, refreshed, expiresAt);
            return refreshed.accessToken();
        }
    }

    public void clearCachedToken(String subjectKey) {
        cachedTokens.remove(subjectKey);
    }

    public boolean revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/revoke"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(Map.of("token", token))))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() / 100 == 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private GoogleTokenResponse refreshAccessToken(GoogleOAuthProfile profile) {
        String body = formBody(Map.of(
                "client_id", profile.clientId(),
                "client_secret", profile.clientSecret(),
                "refresh_token", resolveRefreshToken(profile),
                "grant_type", "refresh_token"));
        return executeTokenRequest(body);
    }

    private String resolveRefreshToken(GoogleOAuthProfile profile) {
        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findGoogleCredential(profile.subjectKey()).orElse(null);
            if (stored != null && stored.refreshToken() != null && !stored.refreshToken().isBlank()) {
                return stored.refreshToken();
            }
        }
        return profile.refreshToken();
    }

    private GoogleTokenExchangeResponse storeExchangeResult(GoogleOAuthProfile profile, GoogleTokenResponse token) {
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        cachedTokens.put(profile.subjectKey(), new CachedToken(token.accessToken(), expiresAt));

        if (!oAuthCredentialService.secureStorageConfigured()) {
            return new GoogleTokenExchangeResponse(
                    false,
                    true,
                    token.refreshToken(),
                    "env:" + profile.subjectKey(),
                    token.scope(),
                    token.tokenType(),
                    expiresAt,
                    "Set bridge.security.token-encryption-key to enable automatic encrypted token storage. Until then, keep using the refresh token in your environment or user settings.");
        }

        oAuthCredentialService.storeGoogleCredential(
                profile.subjectKey(),
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());

        return new GoogleTokenExchangeResponse(
                true,
                false,
                null,
                "db:GOOGLE:" + profile.subjectKey(),
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in the database. Future Google access token refreshes will be handled automatically.");
    }

    private void persistRefreshedToken(GoogleOAuthProfile profile, GoogleTokenResponse token, Instant expiresAt) {
        if (!oAuthCredentialService.secureStorageConfigured()) {
            return;
        }
        oAuthCredentialService.storeGoogleCredential(
                profile.subjectKey(),
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

    void validateGrantedScopes(String grantedScopes) {
        validateGrantedScopes(grantedScopes, OAUTH_SCOPE, "Google", "Retry the Google OAuth flow and grant every requested Gmail permission.");
    }

    static void validateGrantedScopes(String grantedScopes, String requiredScopes, String provider, String retryHint) {
        Set<String> granted = tokenizeScopes(grantedScopes);
        Set<String> required = tokenizeScopes(requiredScopes);
        if (!granted.containsAll(required)) {
            Set<String> missing = required.stream()
                    .filter(scope -> !granted.contains(scope))
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            throw new IllegalStateException(
                    provider + " did not grant all required permissions. Missing scopes: "
                            + String.join(", ", missing) + ". " + retryHint);
        }
    }

    private static Set<String> tokenizeScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopes.trim().split("\\s+"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
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

    public record GoogleOAuthProfile(
            String subjectKey,
            String clientId,
            String clientSecret,
            String refreshToken,
            String redirectUri) {
    }

    private GoogleOAuthProfile systemProfile() {
        return new GoogleOAuthProfile(
                "gmail-destination",
                config.gmail().clientId(),
                config.gmail().clientSecret(),
                config.gmail().refreshToken(),
                config.gmail().redirectUri());
    }

    public CallbackValidation validateCallback(String state) {
        PendingState pendingState = requirePendingState(state, true);
        return new CallbackValidation(
                pendingState.profile().subjectKey(),
                pendingState.targetLabel(),
                pendingState.profile().redirectUri());
    }

    private PendingState requirePendingState(String state, boolean keep) {
        purgeExpiredStates();
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth state");
        }
        PendingState pendingState = keep ? pendingStates.get(state) : pendingStates.remove(state);
        if (pendingState == null || Instant.now().isAfter(pendingState.expiresAt())) {
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }
        return pendingState;
    }

    private void purgeExpiredStates() {
        Instant now = Instant.now();
        pendingStates.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    public record CallbackValidation(String subjectKey, String targetLabel, String redirectUri) {
    }

    private record PendingState(GoogleOAuthProfile profile, String targetLabel, Instant expiresAt) {
    }
}
