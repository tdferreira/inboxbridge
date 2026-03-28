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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.dto.MicrosoftTokenExchangeResponse;
import dev.inboxbridge.dto.MicrosoftTokenResponse;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.persistence.UserBridgeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implements Microsoft OAuth for Outlook / Hotmail / Live source mailboxes.
 *
 * <p>The service supports both environment-managed bridges and user-managed
 * bridges, uses a browser-friendly auth-code flow with CSRF state tracking,
 * stores tokens encrypted in PostgreSQL when secure storage is enabled, and
 * refreshes short-lived access tokens for IMAP / POP XOAUTH2 logins.</p>
 */
@ApplicationScoped
public class MicrosoftOAuthService {
    public static final String MICROSOFT_ACCESS_REVOKED_MESSAGE =
            "The linked Microsoft account no longer grants InboxBridge access. Reconnect it from this mail account.";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_SKEW = Duration.ofSeconds(30);

    @Inject
    BridgeConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    UserBridgeRepository userBridgeRepository;

    @Inject
    EnvSourceService envSourceService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private final ConcurrentMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    public boolean clientConfigured() {
        return isConfiguredValue(config.microsoft().clientId()) && isConfiguredValue(config.microsoft().clientSecret());
    }

    public List<MicrosoftOAuthSourceOption> listMicrosoftOAuthSources() {
        List<MicrosoftOAuthSourceOption> envSources = envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(source -> source.oauthProvider() == BridgeConfig.OAuthProvider.MICROSOFT)
                .map(source -> new MicrosoftOAuthSourceOption(source.id(), source.protocol().name(), source.enabled()))
                .toList();
        List<MicrosoftOAuthSourceOption> userSources = userBridgeRepository.listAll().stream()
                .filter(source -> source.oauthProvider == BridgeConfig.OAuthProvider.MICROSOFT)
                .map(source -> new MicrosoftOAuthSourceOption(source.bridgeId, source.protocol.name(), source.enabled))
                .toList();
        return java.util.stream.Stream.concat(envSources.stream(), userSources.stream()).toList();
    }

    public String buildAuthorizationUrl(String sourceId, String language) {
        requireConfiguredClient();
        SourceRef sourceRef = resolveSourceRef(sourceId);
        purgeExpiredStates();

        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(sourceRef, normalizeLanguage(language), Instant.now().plus(STATE_TTL)));

        return authorizationEndpoint()
                + "?response_type=code"
                + "&response_mode=query"
                + "&prompt=consent"
                + "&client_id=" + urlEncode(config.microsoft().clientId())
                + "&redirect_uri=" + urlEncode(config.microsoft().redirectUri())
                + "&scope=" + urlEncode(authorizationScopes(sourceRef))
                + "&state=" + urlEncode(state);
    }

    public String buildAuthorizationUrl(String sourceId) {
        return buildAuthorizationUrl(sourceId, null);
    }

    public CallbackValidation validateCallback(String state) {
        PendingState pendingState = requirePendingState(state, true);
        return new CallbackValidation(
                pendingState.sourceRef().sourceId(),
                pendingState.sourceRef().environmentIndex() == null
                        ? ""
                        : "MAIL_ACCOUNT_" + pendingState.sourceRef().environmentIndex() + "__OAUTH_REFRESH_TOKEN",
                pendingState.language());
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String normalized = language.trim().toLowerCase();
        if ("fr".equals(normalized) || normalized.startsWith("fr-")) {
            return "fr";
        }
        if ("de".equals(normalized) || normalized.startsWith("de-")) {
            return "de";
        }
        if ("es".equals(normalized) || normalized.startsWith("es-")) {
            return "es";
        }
        if ("pt-pt".equals(normalized)) {
            return "pt-PT";
        }
        if ("pt-br".equals(normalized)) {
            return "pt-BR";
        }
        if ("pt".equals(normalized) || normalized.startsWith("pt-")) {
            return "pt-PT";
        }
        return "en";
    }

    public MicrosoftTokenExchangeResponse exchangeAuthorizationCodeByState(String state, String code) {
        return exchangeAuthorizationCode(requirePendingState(state, false).sourceRef(), code);
    }

    public MicrosoftTokenExchangeResponse exchangeAuthorizationCode(String sourceId, String code) {
        return exchangeAuthorizationCode(resolveSourceRef(sourceId), code);
    }

    public String getAccessToken(BridgeConfig.Source source) {
        return getAccessToken(new RuntimeBridge(
                source.id(),
                "SYSTEM",
                null,
                "system",
                source.enabled(),
                source.protocol(),
                source.host(),
                source.port(),
                source.tls(),
                source.authMethod(),
                source.oauthProvider(),
                source.username(),
                source.password(),
                source.oauthRefreshToken().orElse(""),
                source.folder(),
                source.unreadOnly(),
                source.customLabel(),
                null));
    }

    public String getAccessToken(RuntimeBridge bridge) {
        requireConfiguredClient();
        SourceRef sourceRef = resolveSourceRef(bridge.id());
        CachedToken current = cachedTokens.get(sourceRef.sourceId());
        if (current != null && Instant.now().isBefore(current.expiresAt().minus(TOKEN_SKEW))) {
            return current.accessToken();
        }

        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findMicrosoftCredential(sourceRef.sourceId()).orElse(null);
            if (stored != null
                    && stored.accessToken() != null
                    && stored.accessExpiresAt() != null
                    && Instant.now().isBefore(stored.accessExpiresAt().minus(TOKEN_SKEW))) {
                cachedTokens.put(sourceRef.sourceId(), new CachedToken(stored.accessToken(), stored.accessExpiresAt()));
                return stored.accessToken();
            }
        }

        synchronized (this) {
            CachedToken latest = cachedTokens.get(sourceRef.sourceId());
            if (latest != null && Instant.now().isBefore(latest.expiresAt().minus(TOKEN_SKEW))) {
                return latest.accessToken();
            }

            MicrosoftTokenResponse refreshed = refreshAccessToken(sourceRef, bridge.oauthRefreshToken());
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn() == null ? 300 : refreshed.expiresIn());
            cachedTokens.put(sourceRef.sourceId(), new CachedToken(refreshed.accessToken(), expiresAt));
            persistRefreshedToken(sourceRef.sourceId(), refreshed, expiresAt);
            return refreshed.accessToken();
        }
    }

    public void invalidateCachedToken(String sourceId) {
        cachedTokens.remove(sourceId);
        if (oAuthCredentialService.secureStorageConfigured()) {
            oAuthCredentialService.clearMicrosoftAccessToken(sourceId);
        }
    }

    private MicrosoftTokenExchangeResponse exchangeAuthorizationCode(SourceRef sourceRef, String code) {
        requireConfiguredClient();
        String body = formBody(Map.of(
                "client_id", config.microsoft().clientId(),
                "client_secret", config.microsoft().clientSecret(),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", config.microsoft().redirectUri(),
                "scope", authorizationScopes(sourceRef)));
        MicrosoftTokenResponse token = executeTokenRequest(sourceRef, body, false);
        requireRefreshToken(token.refreshToken(), "Microsoft");
        validateGrantedScopes(token.scope(), sourceRef.protocol());
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        cachedTokens.put(sourceRef.sourceId(), new CachedToken(token.accessToken(), expiresAt));

        if (!oAuthCredentialService.secureStorageConfigured()) {
            if (sourceRef.userManaged()) {
                throw new IllegalStateException("Secure encrypted storage is required for UI-managed Microsoft OAuth bridges.");
            }
            return new MicrosoftTokenExchangeResponse(
                    sourceRef.sourceId(),
                    false,
                    true,
                    token.refreshToken(),
                    "env:MAIL_ACCOUNT_" + sourceRef.environmentIndex() + "__OAUTH_REFRESH_TOKEN",
                    token.scope(),
                    token.tokenType(),
                    expiresAt,
                    "Set SECURITY_TOKEN_ENCRYPTION_KEY to enable automatic encrypted token storage. Until then, keep using MAIL_ACCOUNT_" + sourceRef.environmentIndex() + "__OAUTH_REFRESH_TOKEN in .env.");
        }

        oAuthCredentialService.storeMicrosoftCredential(
                sourceRef.sourceId(),
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());

        return new MicrosoftTokenExchangeResponse(
                sourceRef.sourceId(),
                true,
                false,
                null,
                "db:MICROSOFT:" + sourceRef.sourceId(),
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in the database. Future Microsoft token refreshes will be handled automatically.");
    }

    private MicrosoftTokenResponse refreshAccessToken(SourceRef sourceRef, String configuredRefreshToken) {
        String refreshToken = resolveRefreshToken(sourceRef, configuredRefreshToken);
        if (refreshToken.isBlank()) {
            throw new IllegalStateException("Source " + sourceRef.sourceId() + " is configured for OAuth2 but has no refresh token");
        }

        String body = formBody(Map.of(
                "client_id", config.microsoft().clientId(),
                "client_secret", config.microsoft().clientSecret(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "scope", protocolScope(sourceRef.protocol())));
        return executeTokenRequest(sourceRef, body, true);
    }

    private String resolveRefreshToken(SourceRef sourceRef, String configuredRefreshToken) {
        if (oAuthCredentialService.secureStorageConfigured()) {
            OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findMicrosoftCredential(sourceRef.sourceId()).orElse(null);
            if (stored != null && stored.refreshToken() != null && !stored.refreshToken().isBlank()) {
                return stored.refreshToken();
            }
        }
        if (configuredRefreshToken != null && !configuredRefreshToken.isBlank()) {
            return configuredRefreshToken;
        }
        return sourceRef.refreshToken();
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
            throw new IllegalStateException(provider + " did not return a refresh token. Ensure offline_access is granted and repeat consent.");
        }
    }

    void validateGrantedScopes(String grantedScopes, BridgeConfig.Protocol protocol) {
        GoogleOAuthService.validateGrantedScopes(
                grantedScopes,
                protocolScope(protocol),
                "Microsoft",
                "Retry the Microsoft OAuth flow and approve every requested mailbox permission.");
    }

    private MicrosoftTokenResponse executeTokenRequest(SourceRef sourceRef, String body, boolean refreshFlow) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                if (refreshFlow && indicatesRevokedMicrosoftConsent(response.body())) {
                    handleRevokedMicrosoftConsent(sourceRef);
                    throw new IllegalStateException(MICROSOFT_ACCESS_REVOKED_MESSAGE);
                }
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

    private boolean indicatesRevokedMicrosoftConsent(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase();
        return normalized.contains("\"invalid_grant\"")
                || normalized.contains("\"consent_required\"")
                || normalized.contains("\"interaction_required\"")
                || normalized.contains("grant has expired")
                || normalized.contains("user or administrator has not consented")
                || normalized.contains("refresh token has expired")
                || normalized.contains("refresh token is invalid")
                || normalized.contains("token has been revoked")
                || normalized.contains("no longer valid");
    }

    private void handleRevokedMicrosoftConsent(SourceRef sourceRef) {
        cachedTokens.remove(sourceRef.sourceId());
        if (oAuthCredentialService.secureStorageConfigured()) {
            oAuthCredentialService.deleteMicrosoftCredential(sourceRef.sourceId());
        }
    }

    private void requireConfiguredClient() {
        if (!isConfiguredValue(config.microsoft().clientId())) {
            throw new IllegalStateException("Microsoft OAuth client id is not configured");
        }
        if (!isConfiguredValue(config.microsoft().clientSecret())) {
            throw new IllegalStateException("Microsoft OAuth client secret is not configured");
        }
    }

    private boolean isConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"replace-me".equals(value.trim());
    }

    private SourceRef resolveSourceRef(String sourceId) {
        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            BridgeConfig.Source source = indexedSource.source();
            if (!source.id().equals(sourceId)) {
                continue;
            }
            if (source.authMethod() != BridgeConfig.AuthMethod.OAUTH2) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for OAuth2");
            }
            if (source.oauthProvider() != BridgeConfig.OAuthProvider.MICROSOFT) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for Microsoft OAuth");
            }
            return new SourceRef(
                    source.id(),
                    source.protocol(),
                    source.oauthRefreshToken().orElse(""),
                    indexedSource.index(),
                    false);
        }

        UserBridge bridge = userBridgeRepository.findByBridgeId(sourceId).orElse(null);
        if (bridge != null) {
            if (bridge.authMethod != BridgeConfig.AuthMethod.OAUTH2) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for OAuth2");
            }
            if (bridge.oauthProvider != BridgeConfig.OAuthProvider.MICROSOFT) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for Microsoft OAuth");
            }
            return new SourceRef(
                    bridge.bridgeId,
                    bridge.protocol,
                    "",
                    null,
                    true);
        }

        throw new IllegalArgumentException("Unknown source id: " + sourceId);
    }

    private String authorizationScopes(SourceRef sourceRef) {
        return "offline_access " + protocolScope(sourceRef.protocol());
    }

    private String protocolScope(BridgeConfig.Protocol protocol) {
        return switch (protocol) {
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

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    private record PendingState(SourceRef sourceRef, String language, Instant expiresAt) {
    }

    private record SourceRef(
            String sourceId,
            BridgeConfig.Protocol protocol,
            String refreshToken,
            Integer environmentIndex,
            boolean userManaged) {
    }

    public record CallbackValidation(String sourceId, String configKey, String language) {
    }
}
