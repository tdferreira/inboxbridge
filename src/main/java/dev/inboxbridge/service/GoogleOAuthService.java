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

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.dto.GoogleTokenResponse;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleOAuthService {
        public static final String GMAIL_ACCESS_REVOKED_MESSAGE =
            "The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.";
    public static final String SYSTEM_GMAIL_ACCESS_REVOKED_MESSAGE =
            "The configured Gmail account no longer grants InboxBridge access. Reconnect Gmail OAuth or update the configured refresh token.";
    public static final String SOURCE_GOOGLE_ACCESS_REVOKED_MESSAGE =
            "The linked Google account no longer grants InboxBridge access. Reconnect it from this mail account.";

    public static final String GMAIL_TARGET_SCOPE = "https://www.googleapis.com/auth/gmail.insert https://www.googleapis.com/auth/gmail.labels";
    public static final String GMAIL_SOURCE_SCOPE = "https://mail.google.com/";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    @Inject
    InboxBridgeConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    MailboxConflictService mailboxConflictService;

    @Inject
    UserEmailAccountService userEmailAccountService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ConcurrentMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    public String buildAuthorizationUrl() {
        return buildAuthorizationUrl(systemProfile());
    }

    public boolean clientConfigured() {
        return systemOAuthAppSettingsService.googleClientConfigured();
    }

    public boolean secureStorageConfigured() {
        return oAuthCredentialService.secureStorageConfigured();
    }

    public GoogleOAuthProfile systemProfileForCallbacks() {
        return systemProfile();
    }

    public String buildAuthorizationUrl(GoogleOAuthProfile profile) {
        requireConfiguredClient(profile);
        String redirectUri = urlEncode(profile.redirectUri());
        String clientId = urlEncode(profile.clientId());
        String scope = urlEncode(profile.scope());
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&access_type=offline"
                + "&prompt=consent"
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=" + scope;
    }

    public String buildAuthorizationUrlWithState(GoogleOAuthProfile profile, String targetLabel, String language) {
        purgeExpiredStates();
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(profile, targetLabel, normalizeLanguage(language), Instant.now().plus(STATE_TTL)));
        return buildAuthorizationUrl(profile) + "&state=" + urlEncode(state);
    }

    public String buildAuthorizationUrlWithState(GoogleOAuthProfile profile, String targetLabel) {
        return buildAuthorizationUrlWithState(profile, targetLabel, null);
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
        requireConfiguredClient(profile);
        requireSecureTokenStorage();
        String body = formBody(Map.of(
                "code", code,
                "client_id", profile.clientId(),
                "client_secret", profile.clientSecret(),
                "redirect_uri", profile.redirectUri(),
                "grant_type", "authorization_code"));
        GoogleTokenResponse token = executeTokenRequest(profile, body, false);
        requireRefreshToken(token.refreshToken(), "Google");
        validateGrantedScopes(token.scope(), profile.scope(), "Google", retryHintFor(profile));
        return storeExchangeResult(profile, token);
    }

    public String getAccessToken() {
        return getAccessToken(systemProfile());
    }

    public String getAccessToken(InboxBridgeConfig.Source source) {
        return getAccessToken(sourceProfile(source));
    }

    public String getAccessToken(RuntimeEmailAccount bridge) {
        return getAccessToken(sourceProfile(bridge));
    }

    public String getAccessToken(GoogleOAuthProfile profile) {
        requireConfiguredClient(profile);
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

    public GoogleOAuthProfile sourceProfile(String sourceId, String refreshToken, String redirectUri) {
        return new GoogleOAuthProfile(
                "source-google:" + sourceId,
                systemOAuthAppSettingsService.googleClientId(),
                systemOAuthAppSettingsService.googleClientSecret(),
                refreshToken == null ? "" : refreshToken,
                redirectUri == null || redirectUri.isBlank() ? systemOAuthAppSettingsService.googleRedirectUri() : redirectUri,
                GMAIL_SOURCE_SCOPE);
    }

    public GoogleOAuthProfile sourceProfile(InboxBridgeConfig.Source source) {
        return sourceProfile(source.id(), source.oauthRefreshToken().orElse(""), systemOAuthAppSettingsService.googleRedirectUri());
    }

    public GoogleOAuthProfile sourceProfile(RuntimeEmailAccount bridge) {
        return sourceProfile(bridge.id(), bridge.oauthRefreshToken(), systemOAuthAppSettingsService.googleRedirectUri());
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
        return executeTokenRequest(profile, body, true);
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
        String previousRefreshToken = resolveRefreshToken(profile);
        String previousAccountAddress = previousRefreshToken == null || previousRefreshToken.isBlank()
                ? null
                : resolveAccountAddress(profile);
        String newAccountAddress = resolveAccountAddress(token.accessToken());
        boolean sameLinkedAccount = previousAccountAddress != null
                && newAccountAddress != null
                && previousAccountAddress.equalsIgnoreCase(newAccountAddress);
        boolean replacedExistingAccount = previousRefreshToken != null
                && !previousRefreshToken.isBlank()
                && !sameLinkedAccount
                && !previousRefreshToken.equals(token.refreshToken());
        boolean previousGrantRevoked = !replacedExistingAccount || revokeToken(previousRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        cachedTokens.put(profile.subjectKey(), new CachedToken(token.accessToken(), expiresAt));

        oAuthCredentialService.storeGoogleCredential(
                profile.subjectKey(),
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());

        Long userId = parseUserId(profile.subjectKey());
        if (userId != null) {
            userGmailConfigRepository.findByUserId(userId).ifPresent(config -> {
                config.linkedMailboxAddress = newAccountAddress;
                config.updatedAt = Instant.now();
                userGmailConfigRepository.persist(config);
            });
            mailboxConflictService.disableSourcesMatchingCurrentDestination(userId);
        }
        if (userEmailAccountService != null) {
            sourceIdFromSubjectKey(profile.subjectKey()).ifPresent(userEmailAccountService::enableAfterSuccessfulOauthConnection);
        }

        return new GoogleTokenExchangeResponse(
                true,
                false,
                replacedExistingAccount,
                sameLinkedAccount,
                previousGrantRevoked,
                null,
                "db:GOOGLE:" + profile.subjectKey(),
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in encrypted storage. Future Google access token refreshes will be handled automatically.");
    }

    private void requireSecureTokenStorage() {
        if (!oAuthCredentialService.secureStorageConfigured()) {
            throw new IllegalStateException(
                    "Secure token storage is required before completing Google OAuth. Set SECURITY_TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key, restart InboxBridge, and then retry the OAuth flow.");
        }
    }

    protected String resolveAccountAddress(GoogleOAuthProfile profile) {
        try {
            return resolveAccountAddress(getAccessToken(profile));
        } catch (Exception ignored) {
            return null;
        }
    }

    private java.util.Optional<String> sourceIdFromSubjectKey(String subjectKey) {
        if (subjectKey == null || !subjectKey.startsWith("source-google:")) {
            return java.util.Optional.empty();
        }
        String sourceId = subjectKey.substring("source-google:".length());
        return sourceId.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(sourceId);
    }

    protected String resolveAccountAddress(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(response.body(), Map.class);
            Object emailAddress = payload.get("emailAddress");
            return emailAddress == null ? null : emailAddress.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            return null;
        }
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

    protected GoogleTokenResponse executeTokenRequest(GoogleOAuthProfile profile, String body, boolean refreshFlow) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                if (refreshFlow && indicatesRevokedGoogleConsent(response.body())) {
                    handleRevokedGoogleConsent(profile);
                    throw new IllegalStateException(revokedAccessMessage(profile));
                }
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

    private boolean indicatesRevokedGoogleConsent(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase();
        return normalized.contains("\"invalid_grant\"")
                || normalized.contains("token has been expired or revoked")
                || normalized.contains("token has been revoked")
                || normalized.contains("invalid refresh token")
                || normalized.contains("malformed auth code")
                || normalized.contains("bad request");
    }

    private void handleRevokedGoogleConsent(GoogleOAuthProfile profile) {
        if (profile == null || profile.subjectKey() == null || profile.subjectKey().isBlank()) {
            return;
        }
        clearCachedToken(profile.subjectKey());
        oAuthCredentialService.deleteGoogleCredential(profile.subjectKey());
        Long userId = parseUserId(profile.subjectKey());
        if (userId != null) {
            userGmailConfigRepository.findByUserId(userId).ifPresent(config -> {
                config.refreshTokenCiphertext = null;
                config.refreshTokenNonce = null;
                config.updatedAt = Instant.now();
                userGmailConfigRepository.persist(config);
            });
        }
    }

    private String revokedAccessMessage(GoogleOAuthProfile profile) {
        if (parseUserId(profile.subjectKey()) != null) {
            return GMAIL_ACCESS_REVOKED_MESSAGE;
        }
        if (isSourceSubject(profile.subjectKey())) {
            return SOURCE_GOOGLE_ACCESS_REVOKED_MESSAGE;
        }
        return SYSTEM_GMAIL_ACCESS_REVOKED_MESSAGE;
    }

    private Long parseUserId(String subjectKey) {
        if (subjectKey == null || !subjectKey.startsWith("user-gmail:")) {
            return null;
        }
        try {
            return Long.parseLong(subjectKey.substring("user-gmail:".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isSourceSubject(String subjectKey) {
        return subjectKey != null && subjectKey.startsWith("source-google:");
    }

    private String retryHintFor(GoogleOAuthProfile profile) {
        return isSourceSubject(profile.subjectKey())
                ? "Retry the Google OAuth flow and approve InboxBridge to access this Gmail mailbox."
                : "Retry the Google OAuth flow and grant every requested Gmail permission.";
    }

    private void requireConfiguredClient(GoogleOAuthProfile profile) {
        if (profile.clientId() == null || profile.clientId().isBlank() || "replace-me".equals(profile.clientId().trim())) {
            throw new IllegalStateException("Google OAuth client id is not configured");
        }
        if (profile.clientSecret() == null || profile.clientSecret().isBlank() || "replace-me".equals(profile.clientSecret().trim())) {
            throw new IllegalStateException("Google OAuth client secret is not configured");
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
            String redirectUri,
            String scope) {
    }

    private GoogleOAuthProfile systemProfile() {
        return new GoogleOAuthProfile(
                "gmail-destination",
                systemOAuthAppSettingsService.googleClientId(),
                systemOAuthAppSettingsService.googleClientSecret(),
                systemOAuthAppSettingsService.googleRefreshToken(),
                systemOAuthAppSettingsService.googleRedirectUri(),
                GMAIL_TARGET_SCOPE);
    }

    public CallbackValidation validateCallback(String state) {
        PendingState pendingState = requirePendingState(state, true);
        return new CallbackValidation(
                pendingState.profile().subjectKey(),
                pendingState.targetLabel(),
                pendingState.profile().redirectUri(),
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

    public record CallbackValidation(String subjectKey, String targetLabel, String redirectUri, String language) {
    }

    private record PendingState(GoogleOAuthProfile profile, String targetLabel, String language, Instant expiresAt) {
    }
}
