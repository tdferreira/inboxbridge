package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AccountSessionsResponse;
import dev.inboxbridge.dto.AuthUiOptionsResponse;
import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.LoginRequest;
import dev.inboxbridge.dto.LoginResponse;
import dev.inboxbridge.dto.RegistrationChallengeResponse;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.StartPasskeyLoginRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.ApplicationModeService;
import dev.inboxbridge.service.AuthService;
import dev.inboxbridge.service.AuthClientAddressService;
import dev.inboxbridge.service.AuthLoginProtectionService;
import dev.inboxbridge.service.AuthSecuritySettingsService;
import dev.inboxbridge.service.GeoIpLocationService;
import dev.inboxbridge.service.MicrosoftOAuthService;
import dev.inboxbridge.service.OAuthProviderRegistryService;
import dev.inboxbridge.service.PasskeyService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.RemoteSessionService;
import dev.inboxbridge.service.RegistrationChallengeService;
import dev.inboxbridge.service.SessionClientInfoService;
import dev.inboxbridge.service.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.UserSessionService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

class AuthResourceTest {

    @Test
    void startPasskeyLoginReturnsCeremonyPayload() {
        AuthResource resource = new AuthResource();
        resource.passkeyService = new FakePasskeyService();
        resource.applicationModeService = new FakeApplicationModeService(true);

        StartPasskeyCeremonyResponse response = resource.startPasskeyLogin(new StartPasskeyLoginRequest(null));

        assertEquals("ceremony-1", response.ceremonyId());
        assertEquals("{\"challenge\":\"abc\"}", response.publicKeyJson());
    }

    @Test
    void startPasskeyLoginIgnoresUsernameHint() {
        AuthResource resource = new AuthResource();
        resource.passkeyService = new FakePasskeyService();
        resource.applicationModeService = new FakeApplicationModeService(true);

        StartPasskeyCeremonyResponse response = resource.startPasskeyLogin(new StartPasskeyLoginRequest("alice"));

        assertEquals("ceremony-1", response.ceremonyId());
        assertEquals("{\"challenge\":\"abc\"}", response.publicKeyJson());
    }

    @Test
    void finishPasskeyLoginReturnsSessionCookie() {
        AuthResource resource = new AuthResource();
        resource.authService = new FakeAuthService();
        resource.authClientAddressService = new AuthClientAddressService();
        resource.passkeyService = new FakePasskeyService();
        resource.appUserService = new AppUserService();
        resource.currentUserContext = new CurrentUserContext();
        resource.applicationModeService = new FakeApplicationModeService(true);
        TrackingPollingLiveService pollingLiveService = new TrackingPollingLiveService();
        resource.pollingLiveService = pollingLiveService;
        resource.httpServerRequest = staticHttpServerRequest("172.18.0.2");

        Response response = resource.finishPasskeyLogin(new FinishPasskeyCeremonyRequest("ceremony-1", "{\"id\":\"cred\"}"));

        assertEquals(200, response.getStatus());
        NewCookie cookie = response.getCookies().get("inboxbridge_session");
        NewCookie csrfCookie = response.getCookies().get("inboxbridge_csrf");
        assertNotNull(cookie);
        assertNotNull(csrfCookie);
        assertEquals("session-1", cookie.getValue());
        assertEquals("csrf-1", csrfCookie.getValue());
        assertEquals(true, cookie.isHttpOnly());
        assertEquals(true, cookie.isSecure());
        assertEquals(NewCookie.SameSite.STRICT, cookie.getSameSite());
        assertEquals(false, csrfCookie.isHttpOnly());
        assertEquals(true, csrfCookie.isSecure());
        assertEquals(NewCookie.SameSite.STRICT, csrfCookie.getSameSite());
        LoginResponse payload = (LoginResponse) response.getEntity();
        assertEquals("AUTHENTICATED", payload.status());
        assertEquals(7L, pollingLiveService.lastViewerId);
        assertEquals(PollingLiveService.SessionStreamKind.BROWSER, pollingLiveService.lastStreamKind);
        assertEquals(41L, pollingLiveService.lastSessionId);
    }

    @Test
    void optionsReturnsSingleUserSetting() {
        AuthResource resource = new AuthResource();
        resource.applicationModeService = new FakeApplicationModeService(false);
        resource.appUserService = new FakeAppUserService(false);
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService(false);
        resource.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService(false);
        resource.oAuthProviderRegistryService = new FakeOAuthProviderRegistryService();
        resource.registrationChallengeService = new FakeRegistrationChallengeService(false);
        resource.authSecuritySettingsService = new FakeAuthSecuritySettingsService(false);

        AuthUiOptionsResponse response = resource.options();

        assertEquals(false, response.multiUserEnabled());
        assertEquals(false, response.bootstrapLoginPrefillEnabled());
        assertEquals(false, response.microsoftOAuthAvailable());
        assertEquals(false, response.googleOAuthAvailable());
        assertEquals(false, response.registrationChallengeEnabled());
        assertEquals("ALTCHA", response.registrationChallengeProvider());
    }

    @Test
    void optionsCanExposeBootstrapPrefillWhenStillSafe() {
        AuthResource resource = new AuthResource();
        resource.applicationModeService = new FakeApplicationModeService(true);
        resource.appUserService = new FakeAppUserService(true);
        resource.microsoftOAuthService = new FakeMicrosoftOAuthService(false);
        resource.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService(false);
        resource.oAuthProviderRegistryService = new FakeOAuthProviderRegistryService();
        resource.registrationChallengeService = new FakeRegistrationChallengeService(false);
        resource.authSecuritySettingsService = new FakeAuthSecuritySettingsService(false);

        AuthUiOptionsResponse response = resource.options();

        assertEquals(true, response.bootstrapLoginPrefillEnabled());
    }

    @Test
    void registerIsBlockedWhenMultiUserModeIsDisabled() {
        AuthResource resource = new AuthResource();
        resource.applicationModeService = new FakeApplicationModeService(false);
        resource.appUserService = new AppUserService();
        resource.registrationChallengeService = new FakeRegistrationChallengeService(true);
        resource.authSecuritySettingsService = new FakeAuthSecuritySettingsService(true);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.register(new dev.inboxbridge.dto.RegisterUserRequest("alice", "Secret#123", "Secret#123", "captcha-token")));

        assertEquals("Multi-user mode is disabled for this deployment.", error.getMessage());
    }

    @Test
    void registrationChallengeReturnsCurrentChallenge() {
        AuthResource resource = new AuthResource();
        resource.applicationModeService = new FakeApplicationModeService(true);
        resource.registrationChallengeService = new FakeRegistrationChallengeService(true);
        resource.authSecuritySettingsService = new FakeAuthSecuritySettingsService(true);

        RegistrationChallengeResponse response = resource.registrationChallenge();

        assertEquals(true, response.enabled());
        assertEquals("ALTCHA", response.provider());
        assertEquals("challenge-1", response.altcha().challengeId());
    }

    @Test
    void loginReturnsTooManyRequestsWhenAddressIsBlocked() {
        AuthResource resource = new AuthResource();
        resource.authService = new FakeAuthService();
        resource.authClientAddressService = new AuthClientAddressService();
        resource.authLoginProtectionService = new FakeAuthLoginProtectionService(Instant.parse("2026-03-30T14:20:00Z"));
        resource.authSecuritySettingsService = new FakeAuthSecuritySettingsService(true);
        resource.httpHeaders = new StaticHttpHeaders("203.0.113.4");
        resource.httpServerRequest = staticHttpServerRequest("172.18.0.2");

        Response response = resource.login(new LoginRequest("admin", "wrong"));

        assertEquals(429, response.getStatus());
        ApiError error = (ApiError) response.getEntity();
        assertEquals("auth_login_blocked", error.code());
        assertEquals("2026-03-30T14:20:00Z", error.meta().get("blockedUntil"));
    }

    @Test
    void accountSessionsReturnsRecentAndActiveLists() {
        AccountResource resource = new AccountResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        context.setUser(user);
        UserSession current = new UserSession();
        current.id = 91L;
        context.setSession(current);
        resource.currentUserContext = context;
        resource.userSessionService = new FakeUserSessionService();
        resource.remoteSessionService = new FakeRemoteSessionService();
        resource.geoIpLocationService = new FakeGeoIpLocationService(true);
        resource.sessionClientInfoService = new SessionClientInfoService();

        AccountSessionsResponse response = resource.sessions();

        assertEquals(1, response.recentLogins().size());
        assertEquals(1, response.activeSessions().size());
        assertEquals(true, response.activeSessions().get(0).current());
        assertEquals(true, response.geoIpConfigured());
    }

    @Test
    void revokeOtherSessionsUsesCurrentSessionId() {
        AccountResource resource = new AccountResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        context.setUser(user);
        UserSession current = new UserSession();
        current.id = 91L;
        context.setSession(current);
        FakeUserSessionService sessionService = new FakeUserSessionService();
        resource.pollingLiveService = new TrackingPollingLiveService();
        resource.currentUserContext = context;
        resource.userSessionService = sessionService;
        resource.remoteSessionService = new FakeRemoteSessionService();

        resource.revokeOtherSessions();

        assertEquals(7L, sessionService.lastUserId);
        assertEquals(91L, sessionService.lastCurrentSessionId);
    }

    @Test
    void revokeSessionUsesCurrentUserScope() {
        AccountResource resource = new AccountResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        context.setUser(user);
        FakeUserSessionService sessionService = new FakeUserSessionService();
        resource.currentUserContext = context;
        resource.userSessionService = sessionService;
        resource.remoteSessionService = new FakeRemoteSessionService();
        resource.pollingLiveService = new TrackingPollingLiveService();

        resource.revokeSession(42L, null);

        assertEquals(7L, sessionService.lastUserId);
        assertEquals(42L, sessionService.lastRevokedSessionId);
    }

    @Test
    void recordDeviceLocationUsesCurrentBrowserSession() {
        AuthResource resource = new AuthResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        context.setUser(user);
        UserSession current = new UserSession();
        current.id = 91L;
        context.setSession(current);
        FakeUserSessionService sessionService = new FakeUserSessionService();
        resource.currentUserContext = context;
        resource.userSessionService = sessionService;

        Response response = resource.recordDeviceLocation(new dev.inboxbridge.dto.SessionDeviceLocationRequest(38.7223, -9.1393, 25d));

        assertEquals(204, response.getStatus());
        assertEquals(91L, sessionService.lastLocationSessionId);
        assertEquals(38.7223, sessionService.lastLatitude);
    }

    private static final class FakePasskeyService extends PasskeyService {
        @Override
        public StartPasskeyCeremonyResponse startAuthentication() {
            return new StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}");
        }

        @Override
        public StartPasskeyCeremonyResponse startAuthenticationForUser(AppUser user, boolean passwordVerified) {
            return new StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}");
        }

        @Override
        public PasskeyAuthenticationResult finishAuthentication(FinishPasskeyCeremonyRequest request) {
            AppUser user = new AppUser();
            user.id = 7L;
            user.username = "admin";
            user.role = AppUser.Role.ADMIN;
            user.approved = true;
            user.active = true;
            return new PasskeyAuthenticationResult(user, false);
        }

        @Override
        public long countForUser(Long userId) {
            return 2;
        }
    }

    private static final class FakeAuthService extends AuthService {
        @Override
        public LoginResult login(String username, String password, String clientIp, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthenticatedSession loginWithPasskey(PasskeyService.PasskeyAuthenticationResult result, String clientIp, String userAgent) {
            return new AuthenticatedSession(result.user(), 41L, "session-1", "csrf-1");
        }
    }

    private static final class FakeUserSessionService extends UserSessionService {
        private Long lastUserId;
        private Long lastCurrentSessionId;
        private Long lastRevokedSessionId;
        private Long lastLocationSessionId;
        private Double lastLatitude;

        @Override
        public java.util.List<UserSession> listRecentSessions(Long userId, int limit) {
            return java.util.List.of(buildSession(91L, userId, true));
        }

        @Override
        public java.util.List<UserSession> listActiveSessions(Long userId) {
            return java.util.List.of(buildSession(91L, userId, true));
        }

        @Override
        public void invalidateOtherSessions(Long userId, Long currentSessionId) {
            this.lastUserId = userId;
            this.lastCurrentSessionId = currentSessionId;
        }

        @Override
        public void invalidateSessionForUser(Long userId, Long sessionId) {
            this.lastUserId = userId;
            this.lastRevokedSessionId = sessionId;
        }

        @Override
        public void recordDeviceLocation(Long sessionId, Double latitude, Double longitude, Double accuracyMeters) {
            this.lastLocationSessionId = sessionId;
            this.lastLatitude = latitude;
        }

        private UserSession buildSession(Long sessionId, Long userId, boolean active) {
            UserSession session = new UserSession();
            session.id = sessionId;
            session.userId = userId;
            session.clientIp = "203.0.113.9";
            session.loginMethod = UserSession.LoginMethod.PASSWORD;
            session.createdAt = Instant.parse("2026-03-30T12:00:00Z");
            session.lastSeenAt = Instant.parse("2026-03-30T12:05:00Z");
            session.expiresAt = active ? Instant.now().plusSeconds(3600) : Instant.now().minusSeconds(3600);
            return session;
        }
    }

    private static final class FakeRemoteSessionService extends RemoteSessionService {
        @Override
        public java.util.List<dev.inboxbridge.persistence.RemoteSession> listRecentSessions(Long userId, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<dev.inboxbridge.persistence.RemoteSession> listActiveSessions(Long userId) {
            return java.util.List.of();
        }

        @Override
        public void invalidateOtherSessions(Long userId) {
        }
    }

    private static final class FakeSystemOAuthAppSettingsService extends SystemOAuthAppSettingsService {
        private final boolean googleConfigured;

        private FakeSystemOAuthAppSettingsService(boolean googleConfigured) {
            this.googleConfigured = googleConfigured;
        }

        @Override
        public boolean googleClientConfigured() {
            return googleConfigured;
        }
    }

    private static final class FakeOAuthProviderRegistryService extends OAuthProviderRegistryService {
        @Override
        public java.util.List<dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider> configuredSourceProviders() {
            return java.util.List.of();
        }
    }

    private static final class FakeRegistrationChallengeService extends RegistrationChallengeService {
        private final boolean enabled;

        private FakeRegistrationChallengeService(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public RegistrationChallengeResponse currentChallenge() {
            return enabled
                    ? new RegistrationChallengeResponse(
                            true,
                            "ALTCHA",
                            null,
                            new RegistrationChallengeResponse.AltchaChallengeResponse(
                                    "challenge-1",
                                    "SHA-256",
                                    "challenge",
                                    "salt",
                                    "signature",
                                    1000))
                    : RegistrationChallengeResponse.disabled();
        }

        @Override
        public void validateAndConsume(String captchaToken, String remoteIpAddress) {
        }
    }

    private static final class FakeAuthSecuritySettingsService extends AuthSecuritySettingsService {
        private final boolean registrationChallengeEnabled;

        private FakeAuthSecuritySettingsService(boolean registrationChallengeEnabled) {
            this.registrationChallengeEnabled = registrationChallengeEnabled;
        }

        @Override
        public EffectiveAuthSecuritySettings effectiveSettings() {
            return new EffectiveAuthSecuritySettings(
                    5,
                    java.time.Duration.ofMinutes(5),
                    java.time.Duration.ofHours(1),
                    registrationChallengeEnabled,
                    java.time.Duration.ofMinutes(10),
                    "ALTCHA",
                    "",
                    "",
                    "",
                    "",
                    false,
                    "IPWHOIS",
                    "IPAPI_CO,IP_API,IPINFO_LITE",
                    java.time.Duration.ofDays(30),
                    java.time.Duration.ofMinutes(5),
                    java.time.Duration.ofSeconds(3),
                    "");
        }
    }

    private static final class FakeApplicationModeService extends ApplicationModeService {
        private final boolean enabled;

        private FakeApplicationModeService(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean multiUserEnabled() {
            return enabled;
        }
    }

    private static final class TrackingPollingLiveService extends PollingLiveService {
        private Long lastViewerId;
        private SessionStreamKind lastStreamKind;
        private Long lastSessionId;

        @Override
        public void publishNewSignInDetected(Long viewerId, SessionStreamKind streamKind, Long sessionId) {
            this.lastViewerId = viewerId;
            this.lastStreamKind = streamKind;
            this.lastSessionId = sessionId;
        }
    }

    private static final class FakeAppUserService extends AppUserService {
        private final boolean bootstrapPrefillEnabled;

        private FakeAppUserService(boolean bootstrapPrefillEnabled) {
            this.bootstrapPrefillEnabled = bootstrapPrefillEnabled;
        }

        @Override
        public boolean bootstrapLoginPrefillEnabled() {
            return bootstrapPrefillEnabled;
        }
    }

    private static final class FakeGeoIpLocationService extends GeoIpLocationService {
        private final boolean configured;

        private FakeGeoIpLocationService(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }

    private static final class FakeMicrosoftOAuthService extends MicrosoftOAuthService {
        private final boolean configured;

        private FakeMicrosoftOAuthService(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean clientConfigured() {
            return configured;
        }
    }

    private static final class FakeAuthLoginProtectionService extends AuthLoginProtectionService {
        private final Instant blockedUntil;

        private FakeAuthLoginProtectionService(Instant blockedUntil) {
            this.blockedUntil = blockedUntil;
        }

        @Override
        public void requireLoginAllowed(String clientKey) {
            throw new LoginBlockedException(blockedUntil);
        }
    }

    private static final class StaticHttpHeaders implements HttpHeaders {
        private final MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

        private StaticHttpHeaders(String forwardedFor) {
            headers.put("X-Forwarded-For", List.of(forwardedFor));
        }

        @Override
        public List<String> getRequestHeader(String name) {
            return headers.get(name);
        }

        @Override
        public String getHeaderString(String name) {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return String.join(",", values);
        }

        @Override
        public MultivaluedHashMap<String, String> getRequestHeaders() {
            return headers;
        }

        @Override
        public List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() {
            return List.of();
        }

        @Override
        public List<java.util.Locale> getAcceptableLanguages() {
            return List.of();
        }

        @Override
        public jakarta.ws.rs.core.MediaType getMediaType() {
            return null;
        }

        @Override
        public java.util.Locale getLanguage() {
            return null;
        }

        @Override
        public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() {
            return java.util.Map.of();
        }

        @Override
        public java.util.Date getDate() {
            return null;
        }

        @Override
        public int getLength() {
            return 0;
        }
    }

    static HttpServerRequest staticHttpServerRequest(String remoteAddress) {
        return (HttpServerRequest) Proxy.newProxyInstance(
                HttpServerRequest.class.getClassLoader(),
                new Class<?>[] { HttpServerRequest.class },
                (proxy, method, args) -> {
                    if ("remoteAddress".equals(method.getName())) {
                        return SocketAddress.inetSocketAddress(443, remoteAddress);
                    }
                    return null;
                });
    }
}
