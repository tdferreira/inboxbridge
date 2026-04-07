package dev.inboxbridge.web.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.LoginRequest;
import dev.inboxbridge.dto.LoginResponse;
import dev.inboxbridge.dto.RemoteSessionUserResponse;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.auth.AuthClientAddressService;
import dev.inboxbridge.service.auth.AuthLoginProtectionService;
import dev.inboxbridge.service.auth.AuthService;
import dev.inboxbridge.service.GeoIpLocationService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.remote.RemoteSessionService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.user.UserUiPreferenceService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.enterprise.inject.Vetoed;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Proxy;

class RemoteAuthResourceTest {

    @Test
    void loginReturnsRemoteSessionCookies() {
        RemoteAuthResource resource = new RemoteAuthResource();
        resource.authService = new FakeAuthService();
        resource.passkeyService = new FakePasskeyService();
        resource.appUserService = new FakeAppUserService();
        resource.remoteSessionService = new FakeRemoteSessionService();
        resource.currentUserContext = new CurrentUserContext();
        resource.authClientAddressService = new AuthClientAddressService();
        resource.authLoginProtectionService = new FakeAuthLoginProtectionService();
        resource.geoIpLocationService = new FakeGeoIpLocationService();
        resource.userUiPreferenceService = new FakeUserUiPreferenceService();
        resource.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        resource.inboxBridgeConfig = new FakeInboxBridgeConfig();
        TrackingPollingLiveService pollingLiveService = new TrackingPollingLiveService();
        resource.pollingLiveService = pollingLiveService;
        resource.httpHeaders = new StaticHttpHeaders("203.0.113.7");
        resource.httpServerRequest = staticHttpServerRequest("172.18.0.2");

        Response response = resource.login(new LoginRequest("admin", "secret"));

        assertEquals(200, response.getStatus());
        RemoteSessionUserResponse payload = (RemoteSessionUserResponse) response.getEntity();
        assertEquals("admin", payload.username());
        assertEquals("pt-PT", payload.language());
        NewCookie sessionCookie = response.getCookies().get("inboxbridge_remote_session");
        NewCookie csrfCookie = response.getCookies().get("inboxbridge_remote_csrf");
        assertNotNull(sessionCookie);
        assertNotNull(csrfCookie);
        assertEquals("remote-session-1", sessionCookie.getValue());
        assertEquals("remote-csrf-1", csrfCookie.getValue());
        assertEquals(true, sessionCookie.isHttpOnly());
        assertEquals(true, sessionCookie.isSecure());
        assertEquals(NewCookie.SameSite.STRICT, sessionCookie.getSameSite());
        assertEquals(false, csrfCookie.isHttpOnly());
        assertEquals(true, csrfCookie.isSecure());
        assertEquals(NewCookie.SameSite.STRICT, csrfCookie.getSameSite());
        assertEquals(1L, payload.currentSessionId());
        assertEquals(7L, pollingLiveService.lastViewerId);
        assertEquals(PollingLiveService.SessionStreamKind.REMOTE, pollingLiveService.lastStreamKind);
        assertEquals(1L, pollingLiveService.lastSessionId);
    }

    @Test
    void loginReturnsPasskeyChallengeWhenRequired() {
        RemoteAuthResource resource = new RemoteAuthResource();
        resource.authService = new FakeAuthService(true);
        resource.passkeyService = new FakePasskeyService();
        resource.appUserService = new FakeAppUserService();
        resource.remoteSessionService = new FakeRemoteSessionService();
        resource.currentUserContext = new CurrentUserContext();
        resource.authClientAddressService = new AuthClientAddressService();
        resource.authLoginProtectionService = new FakeAuthLoginProtectionService();
        resource.geoIpLocationService = new FakeGeoIpLocationService();
        resource.userUiPreferenceService = new FakeUserUiPreferenceService();
        resource.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        resource.inboxBridgeConfig = new FakeInboxBridgeConfig();
        TrackingPollingLiveService pollingLiveService = new TrackingPollingLiveService();
        resource.pollingLiveService = pollingLiveService;
        resource.httpHeaders = new StaticHttpHeaders("203.0.113.7");
        resource.httpServerRequest = staticHttpServerRequest("172.18.0.2");

        Response response = resource.login(new LoginRequest("admin", "secret"));

        assertEquals(200, response.getStatus());
        LoginResponse payload = (LoginResponse) response.getEntity();
        assertEquals("PASSKEY_REQUIRED", payload.status());
    }

    @Test
    void finishPasskeyLoginReturnsRemoteSession() {
        RemoteAuthResource resource = new RemoteAuthResource();
        resource.authService = new FakeAuthService();
        resource.passkeyService = new FakePasskeyService();
        resource.appUserService = new FakeAppUserService();
        resource.remoteSessionService = new FakeRemoteSessionService();
        resource.currentUserContext = new CurrentUserContext();
        resource.authClientAddressService = new AuthClientAddressService();
        resource.authLoginProtectionService = new FakeAuthLoginProtectionService();
        resource.geoIpLocationService = new FakeGeoIpLocationService();
        resource.userUiPreferenceService = new FakeUserUiPreferenceService();
        resource.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        resource.inboxBridgeConfig = new FakeInboxBridgeConfig();
        TrackingPollingLiveService pollingLiveService = new TrackingPollingLiveService();
        resource.pollingLiveService = pollingLiveService;
        resource.httpHeaders = new StaticHttpHeaders("203.0.113.7");
        resource.httpServerRequest = staticHttpServerRequest("172.18.0.2");

        Response response = resource.finishPasskeyLogin(new FinishPasskeyCeremonyRequest("ceremony-1", "{\"id\":\"cred\"}"));

        assertEquals(200, response.getStatus());
        RemoteSessionUserResponse payload = (RemoteSessionUserResponse) response.getEntity();
        assertEquals("ADMIN", payload.role());
        assertEquals("pt-PT", payload.language());
        assertEquals(1L, payload.currentSessionId());
        assertEquals(7L, pollingLiveService.lastViewerId);
        assertEquals(PollingLiveService.SessionStreamKind.REMOTE, pollingLiveService.lastStreamKind);
        assertEquals(1L, pollingLiveService.lastSessionId);
    }

    @Test
    void startPasskeyLoginIgnoresUsernameHint() {
        RemoteAuthResource resource = new RemoteAuthResource();
        resource.passkeyService = new FakePasskeyService();
        resource.inboxBridgeConfig = new FakeInboxBridgeConfig();

        StartPasskeyCeremonyResponse response = resource.startPasskeyLogin(new dev.inboxbridge.dto.StartPasskeyLoginRequest("alice"));

        assertEquals("ceremony-1", response.ceremonyId());
        assertEquals("{\"challenge\":\"abc\"}", response.publicKeyJson());
    }

    @Test
    void recordDeviceLocationUsesCurrentRemoteSession() {
        RemoteAuthResource resource = new RemoteAuthResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        context.setUser(user);
        RemoteSession remoteSession = new RemoteSession();
        remoteSession.id = 12L;
        context.setRemoteSession(remoteSession);
        FakeRemoteSessionService remoteSessionService = new FakeRemoteSessionService();
        resource.currentUserContext = context;
        resource.remoteSessionService = remoteSessionService;

        Response response = resource.recordDeviceLocation(new dev.inboxbridge.dto.SessionDeviceLocationRequest(38.7223, -9.1393, 25d));

        assertEquals(204, response.getStatus());
        assertEquals(12L, remoteSessionService.lastLocationSessionId);
        assertEquals(-9.1393, remoteSessionService.lastLongitude);
    }

    private static final class FakeAuthService extends AuthService {
        private final boolean passkeyRequired;

        private FakeAuthService() {
            this(false);
        }

        private FakeAuthService(boolean passkeyRequired) {
            this.passkeyRequired = passkeyRequired;
        }

        @Override
        public AuthenticationResult authenticate(String username, String password) {
            if (passkeyRequired) {
                return AuthenticationResult.passkeyRequired(new dev.inboxbridge.dto.StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}"));
            }
            AppUser user = new AppUser();
            user.id = 7L;
            user.username = "admin";
            user.role = AppUser.Role.ADMIN;
            user.active = true;
            user.approved = true;
            return AuthenticationResult.authenticated(user, UserSession.LoginMethod.PASSWORD);
        }

        @Override
        public AuthenticationResult authenticateWithPasskey(PasskeyService.PasskeyAuthenticationResult result) {
            AppUser user = new AppUser();
            user.id = 7L;
            user.username = "admin";
            user.role = AppUser.Role.ADMIN;
            user.active = true;
            user.approved = true;
            return AuthenticationResult.authenticated(user, UserSession.LoginMethod.PASSKEY);
        }
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
            user.active = true;
            user.approved = true;
            return new PasskeyAuthenticationResult(user, false);
        }
    }

    @Vetoed
    private static final class FakeAppUserService extends AppUserService {
        @Override
        public java.util.Optional<AppUser> findByUsername(String username) {
            AppUser user = new AppUser();
            user.id = 7L;
            user.username = username;
            user.role = AppUser.Role.ADMIN;
            user.active = true;
            user.approved = true;
            return java.util.Optional.of(user);
        }

        @Override
        public boolean hasPassword(AppUser user) {
            return false;
        }

        @Override
        public boolean requiresPasskey(AppUser user) {
            return true;
        }
    }

    private static final class FakeRemoteSessionService extends RemoteSessionService {
        private Long lastLocationSessionId;
        private Double lastLongitude;

        @Override
        public CreatedRemoteSession createSession(AppUser user, String clientIp, String locationLabel, String userAgent, UserSession.LoginMethod loginMethod) {
            RemoteSession session = new RemoteSession();
            session.id = 1L;
            session.userId = user.id;
            return new CreatedRemoteSession("remote-session-1", "remote-csrf-1", session);
        }

        @Override
        public void recordDeviceLocation(Long sessionId, Double latitude, Double longitude, Double accuracyMeters) {
            this.lastLocationSessionId = sessionId;
            this.lastLongitude = longitude;
        }
    }

    private static final class FakeUserUiPreferenceService extends UserUiPreferenceService {
        @Override
        public java.util.Optional<dev.inboxbridge.dto.UserUiPreferenceView> viewForUser(Long userId) {
            return java.util.Optional.of(new dev.inboxbridge.dto.UserUiPreferenceView(
                    false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false,
                    java.util.List.of("quickSetup", "destination", "sourceEmailAccounts", "userPolling", "remoteControl", "userStats"),
                    java.util.List.of("adminQuickSetup", "systemDashboard", "oauthApps", "userManagement", "authSecurity", "globalStats"),
                    "pt-PT",
                    "DMY_24",
                    "MANUAL",
                    "Europe/Lisbon",
                    java.util.List.of()));
        }
    }

    private static final class FakeAuthLoginProtectionService extends AuthLoginProtectionService {
        @Override
        public void requireLoginAllowed(String clientKey) {
        }

        @Override
        public FailureResult recordFailedLogin(String clientKey) {
            return new FailureResult(false, null);
        }

        @Override
        public void recordSuccessfulLogin(String clientKey) {
        }
    }

    private static final class FakeGeoIpLocationService extends GeoIpLocationService {
        @Override
        public java.util.Optional<String> resolveLocation(String clientIp) {
            return java.util.Optional.of("Lisbon, Portugal");
        }
    }

    private static final class FakeSystemOAuthAppSettingsService extends SystemOAuthAppSettingsService {
        @Override
        public boolean effectiveMultiUserEnabled() {
            return true;
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

    private static final class FakeInboxBridgeConfig implements InboxBridgeConfig {
        @Override
        public boolean pollEnabled() { return true; }
        @Override
        public String pollInterval() { return "5m"; }
        @Override
        public int fetchWindow() { return 50; }
        @Override
        public java.time.Duration sourceHostMinSpacing() { return java.time.Duration.ofSeconds(1); }
        @Override
        public int sourceHostMaxConcurrency() { return 2; }
        @Override
        public java.time.Duration destinationProviderMinSpacing() { return java.time.Duration.ofMillis(250); }
        @Override
        public int destinationProviderMaxConcurrency() { return 1; }
        @Override
        public java.time.Duration throttleLeaseTtl() { return java.time.Duration.ofMinutes(2); }
        @Override
        public int adaptiveThrottleMaxMultiplier() { return 6; }
        @Override
        public double successJitterRatio() { return 0.2; }
        @Override
        public java.time.Duration maxSuccessJitter() { return java.time.Duration.ofSeconds(30); }
        @Override
        public boolean multiUserEnabled() { return true; }
        @Override
        public Security security() {
            return new Security() {
                @Override
                public Auth auth() {
                    return new Auth() {
                        @Override
                        public int loginFailureThreshold() { return 5; }
                        @Override
                        public java.time.Duration loginInitialBlock() { return java.time.Duration.ofMinutes(5); }
                        @Override
                        public java.time.Duration loginMaxBlock() { return java.time.Duration.ofHours(1); }
                        @Override
                        public boolean registrationChallengeEnabled() { return true; }
                        @Override
                        public java.time.Duration registrationChallengeTtl() { return java.time.Duration.ofMinutes(10); }
                        @Override
                        public String registrationChallengeProvider() { return "ALTCHA"; }
                        @Override
                        public RegistrationCaptcha registrationCaptcha() {
                            return new RegistrationCaptcha() {
                                @Override
                                public Altcha altcha() {
                                    return new Altcha() {
                                        @Override
                                        public long maxNumber() { return 100000L; }
                                        @Override
                                        public java.util.Optional<String> hmacKey() { return java.util.Optional.empty(); }
                                    };
                                }

                                @Override
                                public Turnstile turnstile() {
                                    return new Turnstile() {
                                        @Override
                                        public java.util.Optional<String> siteKey() { return java.util.Optional.empty(); }
                                        @Override
                                        public java.util.Optional<String> secret() { return java.util.Optional.empty(); }
                                    };
                                }

                                @Override
                                public Hcaptcha hcaptcha() {
                                    return new Hcaptcha() {
                                        @Override
                                        public java.util.Optional<String> siteKey() { return java.util.Optional.empty(); }
                                        @Override
                                        public java.util.Optional<String> secret() { return java.util.Optional.empty(); }
                                    };
                                }
                            };
                        }
                        @Override
                        public GeoIp geoIp() { return null; }
                    };
                }
                @Override
                public Passkeys passkeys() { return null; }
                @Override
                public Remote remote() {
                    return new Remote() {
                        @Override
                        public boolean enabled() { return true; }
                        @Override
                        public java.time.Duration sessionTtl() { return java.time.Duration.ofHours(12); }
                        @Override
                        public int pollRateLimitCount() { return 60; }
                        @Override
                        public java.time.Duration pollRateLimitWindow() { return java.time.Duration.ofMinutes(1); }
                        @Override
                        public java.util.Optional<String> serviceToken() { return java.util.Optional.empty(); }
                        @Override
                        public java.util.Optional<String> serviceUsername() { return java.util.Optional.empty(); }
                    };
                }
            };
        }
        @Override
        public Gmail gmail() { return null; }
        @Override
        public Microsoft microsoft() { return null; }
        @Override
        public java.util.List<Source> sources() { return java.util.List.of(); }
    }

    static final class StaticHttpHeaders implements HttpHeaders {
        private final jakarta.ws.rs.core.MultivaluedMap<String, String> headers = new jakarta.ws.rs.core.MultivaluedHashMap<>();

        StaticHttpHeaders(String forwardedFor) {
            headers.add("X-Forwarded-For", forwardedFor);
            headers.add("User-Agent", "JUnit");
        }

        @Override
        public java.util.List<String> getRequestHeader(String name) {
            return headers.get(name);
        }

        @Override
        public String getHeaderString(String name) {
            return headers.getFirst(name);
        }

        @Override
        public jakarta.ws.rs.core.MultivaluedMap<String, String> getRequestHeaders() {
            return headers;
        }

        @Override
        public java.util.List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<java.util.Locale> getAcceptableLanguages() {
            return java.util.List.of();
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
            return -1;
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
