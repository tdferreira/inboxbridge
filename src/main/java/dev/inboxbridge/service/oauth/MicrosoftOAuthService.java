package dev.inboxbridge.service.oauth;

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

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.dto.MicrosoftTokenExchangeResponse;
import dev.inboxbridge.dto.MicrosoftTokenResponse;
import dev.inboxbridge.service.EnvSourceService;
import dev.inboxbridge.service.user.UserEmailAccountService;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import dev.inboxbridge.service.destination.ImapAppendMailDestinationService;
import dev.inboxbridge.service.destination.MailboxConflictService;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Implements Microsoft OAuth for Outlook / Hotmail / Live source mailboxes.
 *
 * <p>The service supports both environment-managed email accounts and
 * user-managed email accounts, uses a browser-friendly auth-code flow with
 * CSRF state tracking,
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
    InboxBridgeConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    MailboxConflictService mailboxConflictService;

    @Inject
    UserEmailAccountService userEmailAccountService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private final ConcurrentMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingDestinationState> pendingDestinationStates = new ConcurrentHashMap<>();

    public boolean clientConfigured() {
        return systemOAuthAppSettingsService.microsoftClientConfigured();
    }

    public boolean secureStorageConfigured() {
        return oAuthCredentialService.secureStorageConfigured();
    }

    public List<MicrosoftOAuthSourceOption> listMicrosoftOAuthSources() {
        List<MicrosoftOAuthSourceOption> envSources = envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(source -> source.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT)
                .map(source -> new MicrosoftOAuthSourceOption(source.id(), source.protocol().name(), source.enabled()))
                .toList();
        List<MicrosoftOAuthSourceOption> userSources = userEmailAccountRepository.listAll().stream()
                .filter(source -> source.oauthProvider == InboxBridgeConfig.OAuthProvider.MICROSOFT)
                .map(source -> new MicrosoftOAuthSourceOption(source.emailAccountId, source.protocol.name(), source.enabled))
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
                + "&client_id=" + urlEncode(systemOAuthAppSettingsService.microsoftClientId())
                + "&redirect_uri=" + urlEncode(config.microsoft().redirectUri())
                + "&scope=" + urlEncode(authorizationScopes(sourceRef))
                + "&state=" + urlEncode(state);
    }

    public String buildAuthorizationUrl(String sourceId) {
        return buildAuthorizationUrl(sourceId, null);
    }

    public String buildDestinationAuthorizationUrl(Long userId, String language) {
        requireConfiguredClient();
        purgeExpiredStates();

        String state = UUID.randomUUID().toString();
        pendingDestinationStates.put(state, new PendingDestinationState(userId, normalizeLanguage(language), Instant.now().plus(STATE_TTL)));

        return authorizationEndpoint()
                + "?response_type=code"
                + "&response_mode=query"
                + "&prompt=consent"
                + "&client_id=" + urlEncode(systemOAuthAppSettingsService.microsoftClientId())
                + "&redirect_uri=" + urlEncode(config.microsoft().redirectUri())
                + "&scope=" + urlEncode("offline_access " + protocolScope(InboxBridgeConfig.Protocol.IMAP))
                + "&state=" + urlEncode(state);
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

    public BrowserCallbackValidation validateBrowserCallback(String state) {
        PendingState pendingState = pendingStates.get(state);
        if (pendingState != null && Instant.now().isBefore(pendingState.expiresAt())) {
            return new BrowserCallbackValidation(
                    pendingState.sourceRef().sourceId(),
                    pendingState.sourceRef().environmentIndex() == null
                            ? ""
                            : "MAIL_ACCOUNT_" + pendingState.sourceRef().environmentIndex() + "__OAUTH_REFRESH_TOKEN",
                    pendingState.language(),
                    "Mail account " + pendingState.sourceRef().sourceId());
        }
        PendingDestinationState destinationState = pendingDestinationStates.get(state);
        if (destinationState != null && Instant.now().isBefore(destinationState.expiresAt())) {
            return new BrowserCallbackValidation(
                    destinationSubjectKey(destinationState.userId()),
                    "",
                    destinationState.language(),
                    "Destination mailbox");
        }
        throw new IllegalArgumentException("Invalid or expired OAuth state");
    }

    @Transactional
    public boolean destinationLinked(Long userId) {
        return oAuthCredentialService.findMicrosoftCredential(destinationSubjectKey(userId))
                .map(credential -> credential.refreshToken() != null && !credential.refreshToken().isBlank())
                .orElse(false);
    }

    public String getDestinationAccessToken(Long userId) {
        requireConfiguredClient();
        String subjectKey = destinationSubjectKey(userId);
        CachedToken current = cachedTokens.get(subjectKey);
        if (current != null && Instant.now().isBefore(current.expiresAt().minus(TOKEN_SKEW))) {
            return current.accessToken();
        }

        OAuthCredentialService.StoredOAuthCredential stored = oAuthCredentialService.findMicrosoftCredential(subjectKey)
                .orElseThrow(() -> new IllegalStateException(ImapAppendMailDestinationService.IMAP_DESTINATION_NOT_LINKED_MESSAGE));
        if (stored.accessToken() != null
                && stored.accessExpiresAt() != null
                && Instant.now().isBefore(stored.accessExpiresAt().minus(TOKEN_SKEW))) {
            cachedTokens.put(subjectKey, new CachedToken(stored.accessToken(), stored.accessExpiresAt()));
            return stored.accessToken();
        }

        synchronized (this) {
            CachedToken latest = cachedTokens.get(subjectKey);
            if (latest != null && Instant.now().isBefore(latest.expiresAt().minus(TOKEN_SKEW))) {
                return latest.accessToken();
            }

            MicrosoftTokenResponse refreshed = refreshDestinationAccessToken(userId, stored.refreshToken());
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn() == null ? 300 : refreshed.expiresIn());
            cachedTokens.put(subjectKey, new CachedToken(refreshed.accessToken(), expiresAt));
            oAuthCredentialService.storeMicrosoftCredential(subjectKey, refreshed.refreshToken(), refreshed.accessToken(), expiresAt, refreshed.scope(), refreshed.tokenType());
            return refreshed.accessToken();
        }
    }

    public void invalidateDestinationCachedToken(Long userId) {
        String subjectKey = destinationSubjectKey(userId);
        cachedTokens.remove(subjectKey);
        if (oAuthCredentialService.secureStorageConfigured()) {
            oAuthCredentialService.clearMicrosoftAccessToken(subjectKey);
        }
    }

    public void unlinkDestination(Long userId) {
        String subjectKey = destinationSubjectKey(userId);
        cachedTokens.remove(subjectKey);
        if (oAuthCredentialService.secureStorageConfigured()) {
            oAuthCredentialService.deleteMicrosoftCredential(subjectKey);
        }
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

    @Transactional
    public MicrosoftTokenExchangeResponse exchangeAuthorizationCodeByState(String state, String code) {
        PendingState pendingState = pendingStates.remove(state);
        if (pendingState != null && Instant.now().isBefore(pendingState.expiresAt())) {
            return exchangeAuthorizationCode(pendingState.sourceRef(), code);
        }
        PendingDestinationState destinationState = pendingDestinationStates.remove(state);
        if (destinationState != null && Instant.now().isBefore(destinationState.expiresAt())) {
            return exchangeDestinationAuthorizationCode(destinationState.userId(), code);
        }
        throw new IllegalArgumentException("Invalid or expired OAuth state");
    }

    public MicrosoftTokenExchangeResponse exchangeAuthorizationCode(String sourceId, String code) {
        return exchangeAuthorizationCode(resolveSourceRef(sourceId), code);
    }

    public String getAccessToken(InboxBridgeConfig.Source source) {
        return getAccessToken(new RuntimeEmailAccount(
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
                source.fetchMode(),
                source.customLabel(),
                null));
    }

    public String getAccessToken(RuntimeEmailAccount bridge) {
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
        requireSecureTokenStorage("Microsoft OAuth");
        String body = formBody(Map.of(
                "client_id", systemOAuthAppSettingsService.microsoftClientId(),
                "client_secret", systemOAuthAppSettingsService.microsoftClientSecret(),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", config.microsoft().redirectUri(),
                "scope", authorizationScopes(sourceRef)));
        MicrosoftTokenResponse token = executeTokenRequest(sourceRef, body, false);
        requireRefreshToken(token.refreshToken(), "Microsoft");
        validateGrantedScopes(token.scope(), sourceRef.protocol());
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        cachedTokens.put(sourceRef.sourceId(), new CachedToken(token.accessToken(), expiresAt));

        oAuthCredentialService.storeMicrosoftCredential(
                sourceRef.sourceId(),
                token.refreshToken(),
                token.accessToken(),
                expiresAt,
                token.scope(),
                token.tokenType());
        userEmailAccountService.enableAfterSuccessfulOauthConnection(sourceRef.sourceId());

        return new MicrosoftTokenExchangeResponse(
                sourceRef.sourceId(),
                true,
                false,
                null,
                "db:MICROSOFT:" + sourceRef.sourceId(),
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in encrypted storage. Future Microsoft token refreshes will be handled automatically.");
    }

    private MicrosoftTokenResponse refreshAccessToken(SourceRef sourceRef, String configuredRefreshToken) {
        String refreshToken = resolveRefreshToken(sourceRef, configuredRefreshToken);
        if (refreshToken.isBlank()) {
            throw new IllegalStateException("Source " + sourceRef.sourceId() + " is configured for OAuth2 but has no refresh token");
        }

        String body = formBody(Map.of(
                "client_id", systemOAuthAppSettingsService.microsoftClientId(),
                "client_secret", systemOAuthAppSettingsService.microsoftClientSecret(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "scope", protocolScope(sourceRef.protocol())));
        return executeTokenRequest(sourceRef, body, true);
    }

    private MicrosoftTokenResponse refreshDestinationAccessToken(Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException(ImapAppendMailDestinationService.IMAP_DESTINATION_NOT_LINKED_MESSAGE);
        }

        String body = formBody(Map.of(
                "client_id", systemOAuthAppSettingsService.microsoftClientId(),
                "client_secret", systemOAuthAppSettingsService.microsoftClientSecret(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "scope", protocolScope(InboxBridgeConfig.Protocol.IMAP)));
        return executeDestinationTokenRequest(userId, body, true);
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

    private MicrosoftTokenExchangeResponse exchangeDestinationAuthorizationCode(Long userId, String code) {
        requireSecureTokenStorage("Microsoft destination OAuth");
        String body = formBody(Map.of(
                "client_id", systemOAuthAppSettingsService.microsoftClientId(),
                "client_secret", systemOAuthAppSettingsService.microsoftClientSecret(),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", config.microsoft().redirectUri(),
                "scope", "offline_access " + protocolScope(InboxBridgeConfig.Protocol.IMAP)));
        MicrosoftTokenResponse token = executeDestinationTokenRequest(userId, body, false);
        requireRefreshToken(token.refreshToken(), "Microsoft");
        validateGrantedScopes(token.scope(), InboxBridgeConfig.Protocol.IMAP);
        Instant expiresAt = Instant.now().plusSeconds(token.expiresIn() == null ? 300 : token.expiresIn());
        String subjectKey = destinationSubjectKey(userId);
        cachedTokens.put(subjectKey, new CachedToken(token.accessToken(), expiresAt));
        oAuthCredentialService.storeMicrosoftCredential(subjectKey, token.refreshToken(), token.accessToken(), expiresAt, token.scope(), token.tokenType());
        syncDestinationMicrosoftConfig(userId, preferredMailboxUsername(token.accessToken()));
        mailboxConflictService.disableSourcesMatchingCurrentDestination(userId);
        return new MicrosoftTokenExchangeResponse(
                subjectKey,
                true,
                false,
                null,
                "db:MICROSOFT:" + subjectKey,
                token.scope(),
                token.tokenType(),
                expiresAt,
                "Stored securely in encrypted storage for the Outlook destination mailbox.");
    }

    private void requireSecureTokenStorage(String flowLabel) {
        if (!oAuthCredentialService.secureStorageConfigured()) {
            throw new IllegalStateException(
                    "Secure token storage is required before completing " + flowLabel + ". Set SECURITY_TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key, restart InboxBridge, and then retry the OAuth flow.");
        }
    }

    void validateGrantedScopes(String grantedScopes, InboxBridgeConfig.Protocol protocol) {
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

    private MicrosoftTokenResponse executeDestinationTokenRequest(Long userId, String body, boolean refreshFlow) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                if (refreshFlow && indicatesRevokedMicrosoftConsent(response.body())) {
                    unlinkDestination(userId);
                    throw new IllegalStateException(ImapAppendMailDestinationService.MICROSOFT_DESTINATION_ACCESS_REVOKED_MESSAGE);
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
        if (!isConfiguredValue(systemOAuthAppSettingsService.microsoftClientId())) {
            throw new IllegalStateException("Microsoft OAuth client id is not configured");
        }
        if (!isConfiguredValue(systemOAuthAppSettingsService.microsoftClientSecret())) {
            throw new IllegalStateException("Microsoft OAuth client secret is not configured");
        }
    }

    private boolean isConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"replace-me".equals(value.trim());
    }

    private SourceRef resolveSourceRef(String sourceId) {
        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            InboxBridgeConfig.Source source = indexedSource.source();
            if (!source.id().equals(sourceId)) {
                continue;
            }
            if (source.authMethod() != InboxBridgeConfig.AuthMethod.OAUTH2) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for OAuth2");
            }
            if (source.oauthProvider() != InboxBridgeConfig.OAuthProvider.MICROSOFT) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for Microsoft OAuth");
            }
            return new SourceRef(
                    source.id(),
                    source.protocol(),
                    source.oauthRefreshToken().orElse(""),
                    indexedSource.index(),
                    false);
        }

        UserEmailAccount bridge = userEmailAccountRepository.findByEmailAccountId(sourceId).orElse(null);
        if (bridge != null) {
            if (bridge.authMethod != InboxBridgeConfig.AuthMethod.OAUTH2) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for OAuth2");
            }
            if (bridge.oauthProvider != InboxBridgeConfig.OAuthProvider.MICROSOFT) {
                throw new IllegalArgumentException("Source " + sourceId + " is not configured for Microsoft OAuth");
            }
            return new SourceRef(
                    bridge.emailAccountId,
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

    private String protocolScope(InboxBridgeConfig.Protocol protocol) {
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
        pendingDestinationStates.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private void syncDestinationMicrosoftConfig(Long userId, String username) {
        UserMailDestinationConfig config = userMailDestinationConfigRepository.findByUserId(userId)
                .orElseGet(UserMailDestinationConfig::new);
        config.userId = userId;
        config.provider = UserMailDestinationConfigService.PROVIDER_OUTLOOK;
        config.host = config.host == null || config.host.isBlank() ? "outlook.office365.com" : config.host;
        config.port = config.port == null ? 993 : config.port;
        config.tls = true;
        config.authMethod = InboxBridgeConfig.AuthMethod.OAUTH2.name();
        config.oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        if (username != null && !username.isBlank()) {
            config.username = username;
        }
        config.passwordCiphertext = null;
        config.passwordNonce = null;
        config.folderName = config.folderName == null || config.folderName.isBlank() ? "INBOX" : config.folderName;
        config.updatedAt = Instant.now();
        userMailDestinationConfigRepository.persist(config);
    }

    private String preferredMailboxUsername(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(padBase64(parts[1]));
            com.fasterxml.jackson.databind.JsonNode claims = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
            for (String field : List.of("preferred_username", "upn", "email", "unique_name")) {
                String value = claims.path(field).asText("").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        } catch (IllegalArgumentException | IOException ignored) {
            return null;
        }
        return null;
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private String destinationSubjectKey(Long userId) {
        return "destination-microsoft:" + userId;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    private record PendingState(SourceRef sourceRef, String language, Instant expiresAt) {
    }

    private record PendingDestinationState(Long userId, String language, Instant expiresAt) {
    }

    private record SourceRef(
            String sourceId,
            InboxBridgeConfig.Protocol protocol,
            String refreshToken,
            Integer environmentIndex,
            boolean userManaged) {
    }

    public record CallbackValidation(String sourceId, String configKey, String language) {
    }

    public record BrowserCallbackValidation(String subjectId, String configKey, String language, String targetLabel) {
    }
}
