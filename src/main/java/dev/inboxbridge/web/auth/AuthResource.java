package dev.inboxbridge.web.auth;

import dev.inboxbridge.dto.AuthUiOptionsResponse;
import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.LoginRequest;
import dev.inboxbridge.dto.LoginResponse;
import dev.inboxbridge.dto.RegistrationChallengeResponse;
import dev.inboxbridge.dto.RegisterUserRequest;
import dev.inboxbridge.dto.SessionUserResponse;
import dev.inboxbridge.dto.SessionDeviceLocationRequest;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.StartPasskeyLoginRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.AuthenticatedFilter;
import dev.inboxbridge.security.BrowserSessionSecurity;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.admin.ApplicationModeService;
import dev.inboxbridge.service.auth.AuthService;
import dev.inboxbridge.service.auth.AuthClientAddressService;
import dev.inboxbridge.service.auth.AuthLoginProtectionService;
import dev.inboxbridge.service.auth.AuthSecuritySettingsService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.oauth.OAuthProviderRegistryService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.auth.RegistrationChallengeService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.auth.UserSessionService;
import dev.inboxbridge.web.WebResourceSupport;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Context;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
/**
 * Exposes browser-facing authentication endpoints for login, logout, current
 * session inspection, and self-registration. Registration creates pending
 * accounts that must be approved by an admin before the user can sign in.
 */
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AppUserService appUserService;

    @Inject
    PasskeyService passkeyService;

    @Inject
    ApplicationModeService applicationModeService;

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    OAuthProviderRegistryService oAuthProviderRegistryService;

    @Inject
    AuthClientAddressService authClientAddressService;

    @Inject
    AuthLoginProtectionService authLoginProtectionService;

    @Inject
    RegistrationChallengeService registrationChallengeService;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    PollingLiveService pollingLiveService;

    @Context
    HttpHeaders httpHeaders;

    @Context
    HttpServerRequest httpServerRequest;

    @GET
    @Path("/options")
    public AuthUiOptionsResponse options() {
        return new AuthUiOptionsResponse(
                applicationModeService.multiUserEnabled(),
                appUserService.bootstrapLoginPrefillEnabled(),
                microsoftOAuthService.clientConfigured(),
                systemOAuthAppSettingsService.googleClientConfigured(),
                authSecuritySettingsService.effectiveSettings().registrationChallengeEnabled(),
                authSecuritySettingsService.effectiveSettings().registrationChallengeProvider(),
                oAuthProviderRegistryService.configuredSourceProviders().stream()
                        .map(Enum::name)
                        .toList());
    }

    @GET
    @Path("/register/challenge")
    public RegistrationChallengeResponse registrationChallenge() {
        applicationModeService.requireMultiUserMode();
        return registrationChallengeService.currentChallenge();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest request) {
        String clientKey = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
        String userAgent = httpHeaders == null ? null : httpHeaders.getHeaderString("User-Agent");
        try {
            authLoginProtectionService.requireLoginAllowed(clientKey);
            AuthService.LoginResult result = authService.login(request.username(), request.password(), clientKey, userAgent);
            authLoginProtectionService.recordSuccessfulLogin(clientKey);
            if (result.status() == AuthService.LoginStatus.PASSKEY_REQUIRED) {
                return Response.ok(LoginResponse.passkeyRequired(result.passkeyChallenge())).build();
            }
            AuthService.AuthenticatedSession session = result.session();
            pollingLiveService.publishNewSignInDetected(
                    session.user().id,
                    PollingLiveService.SessionStreamKind.BROWSER,
                    session.sessionId());
            return Response.ok(LoginResponse.authenticated(toResponse(session.user(), session.sessionId())))
                    .cookie(sessionCookie(session.token()))
                    .cookie(csrfCookie(session.csrfToken()))
                    .build();
        } catch (AuthLoginProtectionService.LoginBlockedException e) {
            return loginBlockedResponse(e.blockedUntil());
        } catch (IllegalArgumentException e) {
            AuthLoginProtectionService.FailureResult failure = authLoginProtectionService.recordFailedLogin(clientKey);
            if (failure.blocked()) {
                return loginBlockedResponse(failure.blockedUntil());
            }
            throw WebResourceSupport.badRequest(e);
        }
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(RegisterUserRequest request) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            registrationChallengeService.validateAndConsume(
                    request.captchaToken(),
                    authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress()));
            AppUser user = appUserService.registerUser(request);
            return Response.status(Response.Status.ACCEPTED)
                    .entity(java.util.Map.of(
                            "username", user.username,
                            "message", "Registration received. An admin must approve this account before it can sign in."))
                    .build();
        });
    }

    @POST
    @Path("/passkey/options")
    @Consumes(MediaType.APPLICATION_JSON)
    public StartPasskeyCeremonyResponse startPasskeyLogin(StartPasskeyLoginRequest request) {
        return WebResourceSupport.badRequest(passkeyService::startAuthentication);
    }

    @POST
    @Path("/passkey/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response finishPasskeyLogin(FinishPasskeyCeremonyRequest request) {
        return WebResourceSupport.badRequest(() -> {
            String clientKey = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
            String userAgent = httpHeaders == null ? null : httpHeaders.getHeaderString("User-Agent");
            PasskeyService.PasskeyAuthenticationResult authResult = passkeyService.finishAuthentication(request);
            AuthService.AuthenticatedSession session = authService.loginWithPasskey(authResult, clientKey, userAgent);
            pollingLiveService.publishNewSignInDetected(
                    session.user().id,
                    PollingLiveService.SessionStreamKind.BROWSER,
                    session.sessionId());
            return Response.ok(LoginResponse.authenticated(toResponse(session.user(), session.sessionId())))
                    .cookie(sessionCookie(session.token()))
                    .cookie(csrfCookie(session.csrfToken()))
                    .build();
        });
    }

    @POST
    @Path("/logout")
    @RequireAuth
    public Response logout(@jakarta.ws.rs.CookieParam(AuthenticatedFilter.SESSION_COOKIE) String token) {
        authService.logout(token);
        return Response.noContent().cookie(expiredCookie()).cookie(expiredCsrfCookie()).build();
    }

    @GET
    @Path("/me")
    @RequireAuth
    public SessionUserResponse me() {
        return toResponse(currentUserContext.user());
    }

    @POST
    @Path("/session/device-location")
    @RequireAuth
    @Consumes(MediaType.APPLICATION_JSON)
    public Response recordDeviceLocation(SessionDeviceLocationRequest request) {
        return WebResourceSupport.badRequest(() -> {
            userSessionService.recordDeviceLocation(
                    currentUserContext.session() == null ? null : currentUserContext.session().id,
                    request == null ? null : request.latitude(),
                    request == null ? null : request.longitude(),
                    request == null ? null : request.accuracyMeters());
            return Response.noContent().build();
        });
    }

    private SessionUserResponse toResponse(AppUser user) {
        return toResponse(user, currentUserContext.session() == null ? null : currentUserContext.session().id);
    }

    private SessionUserResponse toResponse(AppUser user, Long currentSessionId) {
        return new SessionUserResponse(
                user.id,
                currentSessionId,
                user.username,
                user.role.name(),
                user.approved,
                user.mustChangePassword,
                (int) passkeyService.countForUser(user.id),
                appUserService.hasPassword(user),
                currentUserContext.session() != null && currentUserContext.session().deviceLocationCapturedAt != null);
    }

    private NewCookie sessionCookie(String token) {
        return new NewCookie.Builder(AuthenticatedFilter.SESSION_COOKIE)
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge((int) java.time.Duration.ofDays(7).getSeconds())
                .build();
    }

    private NewCookie expiredCookie() {
        return new NewCookie.Builder(AuthenticatedFilter.SESSION_COOKIE)
                .value("")
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(0)
                .build();
    }

    private NewCookie csrfCookie(String token) {
        return new NewCookie.Builder(BrowserSessionSecurity.CSRF_COOKIE)
                .value(token)
                .path("/")
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge((int) java.time.Duration.ofDays(7).getSeconds())
                .build();
    }

    private NewCookie expiredCsrfCookie() {
        return new NewCookie.Builder(BrowserSessionSecurity.CSRF_COOKIE)
                .value("")
                .path("/")
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(0)
                .build();
    }

    private Response loginBlockedResponse(java.time.Instant blockedUntil) {
        return Response.status(429)
                .entity(new ApiError(
                        "auth_login_blocked",
                        "Too many failed sign-in attempts from this address.",
                        "Too many failed sign-in attempts from this address.",
                        java.util.Map.of("blockedUntil", blockedUntil.toString())))
                .build();
    }

    private String directRemoteAddress() {
        if (httpServerRequest == null || httpServerRequest.remoteAddress() == null) {
            return null;
        }
        return httpServerRequest.remoteAddress().hostAddress();
    }
}
