package dev.inboxbridge.web;

import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.LoginRequest;
import dev.inboxbridge.dto.LoginResponse;
import dev.inboxbridge.dto.RemoteSessionUserResponse;
import dev.inboxbridge.dto.SessionDeviceLocationRequest;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.security.RequireRemoteControl;
import dev.inboxbridge.security.RemoteControlFilter;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.AuthClientAddressService;
import dev.inboxbridge.service.AuthLoginProtectionService;
import dev.inboxbridge.service.AuthService;
import dev.inboxbridge.service.GeoIpLocationService;
import dev.inboxbridge.service.PasskeyService;
import dev.inboxbridge.service.PollingLiveService;
import dev.inboxbridge.service.RemoteSessionService;
import dev.inboxbridge.service.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.UserUiPreferenceService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

@Path("/api/remote/auth")
@Produces(MediaType.APPLICATION_JSON)
public class RemoteAuthResource {
    private static final String DEFAULT_LANGUAGE = "en";

    @Inject
    AuthService authService;

    @Inject
    PasskeyService passkeyService;

    @Inject
    AppUserService appUserService;

    @Inject
    RemoteSessionService remoteSessionService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AuthClientAddressService authClientAddressService;

    @Inject
    AuthLoginProtectionService authLoginProtectionService;

    @Inject
    GeoIpLocationService geoIpLocationService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    PollingLiveService pollingLiveService;

    @Context
    HttpHeaders httpHeaders;

    @Context
    HttpServerRequest httpServerRequest;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest request) {
        requireRemoteEnabled();
        String clientKey = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
        String userAgent = httpHeaders == null ? null : httpHeaders.getHeaderString("User-Agent");
        try {
            authLoginProtectionService.requireLoginAllowed(clientKey);
            AuthService.AuthenticationResult result = authService.authenticate(request.username(), request.password());
            authLoginProtectionService.recordSuccessfulLogin(clientKey);
            if (result.status() == AuthService.AuthenticationStatus.PASSKEY_REQUIRED) {
                return Response.ok(LoginResponse.passkeyRequired(result.passkeyChallenge())).build();
            }
            return authenticated(result.user(), result.loginMethod(), clientKey, userAgent);
        } catch (AuthLoginProtectionService.LoginBlockedException e) {
            return loginBlockedResponse(e.blockedUntil());
        } catch (IllegalArgumentException e) {
            AuthLoginProtectionService.FailureResult failure = authLoginProtectionService.recordFailedLogin(clientKey);
            if (failure.blocked()) {
                return loginBlockedResponse(failure.blockedUntil());
            }
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/passkey/options")
    @Consumes(MediaType.APPLICATION_JSON)
    public dev.inboxbridge.dto.StartPasskeyCeremonyResponse startPasskeyLogin(dev.inboxbridge.dto.StartPasskeyLoginRequest request) {
        requireRemoteEnabled();
        try {
            return passkeyService.startAuthentication();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/passkey/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response finishPasskeyLogin(FinishPasskeyCeremonyRequest request) {
        requireRemoteEnabled();
        try {
            String clientKey = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
            String userAgent = httpHeaders == null ? null : httpHeaders.getHeaderString("User-Agent");
            PasskeyService.PasskeyAuthenticationResult authResult = passkeyService.finishAuthentication(request);
            AuthService.AuthenticationResult result = authService.authenticateWithPasskey(authResult);
            return authenticated(result.user(), result.loginMethod(), clientKey, userAgent);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/me")
    @RequireRemoteControl
    public RemoteSessionUserResponse me() {
        return toResponse(currentUserContext.user());
    }

    @POST
    @Path("/logout")
    @RequireRemoteControl
    public Response logout(@CookieParam(RemoteControlFilter.REMOTE_SESSION_COOKIE) String token) {
        remoteSessionService.invalidate(token);
        return Response.noContent()
                .cookie(expiredSessionCookie())
                .cookie(expiredCsrfCookie())
                .build();
    }

    @POST
    @Path("/session/device-location")
    @RequireRemoteControl
    @Consumes(MediaType.APPLICATION_JSON)
    public Response recordDeviceLocation(SessionDeviceLocationRequest request) {
        try {
            remoteSessionService.recordDeviceLocation(
                    currentUserContext.remoteSession() == null ? null : currentUserContext.remoteSession().id,
                    request == null ? null : request.latitude(),
                    request == null ? null : request.longitude(),
                    request == null ? null : request.accuracyMeters());
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    private Response authenticated(AppUser user, UserSession.LoginMethod loginMethod, String clientIp, String userAgent) {
        String location = geoIpLocationService.resolveLocation(clientIp).orElse(null);
        RemoteSessionService.CreatedRemoteSession session = remoteSessionService.createSession(
                user,
                clientIp,
                location,
                userAgent,
                loginMethod);
        pollingLiveService.publishNewSignInDetected(
                user.id,
                PollingLiveService.SessionStreamKind.REMOTE,
                session.session().id);
        return Response.ok(toResponse(user, session.session().id))
                .cookie(remoteSessionCookie(session.sessionToken()))
                .cookie(remoteCsrfCookie(session.csrfToken()))
                .build();
    }

    private RemoteSessionUserResponse toResponse(AppUser user) {
        return toResponse(user, currentUserContext.remoteSession() == null ? null : currentUserContext.remoteSession().id);
    }

    private RemoteSessionUserResponse toResponse(AppUser user, Long currentSessionId) {
        dev.inboxbridge.dto.UserUiPreferenceView uiPreference = userUiPreferenceService.viewForUser(user.id).orElse(userUiPreferenceService.defaultView());
        return new RemoteSessionUserResponse(
                user.id,
                currentSessionId,
                user.username,
                user.role.name(),
                true,
                user.role == AppUser.Role.ADMIN,
                systemOAuthAppSettingsService.effectiveMultiUserEnabled(),
                currentUserContext.remoteSession() != null && currentUserContext.remoteSession().deviceLocationCapturedAt != null,
                uiPreference.language(),
                uiPreference.dateFormat(),
                uiPreference.timezoneMode(),
                uiPreference.timezone());
    }

    private NewCookie remoteSessionCookie(String token) {
        return new NewCookie.Builder(RemoteControlFilter.REMOTE_SESSION_COOKIE)
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }

    private NewCookie remoteCsrfCookie(String token) {
        return new NewCookie.Builder(RemoteControlFilter.REMOTE_CSRF_COOKIE)
                .value(token)
                .path("/")
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }

    private NewCookie expiredSessionCookie() {
        return new NewCookie.Builder(RemoteControlFilter.REMOTE_SESSION_COOKIE)
                .value("")
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(0)
                .build();
    }

    private NewCookie expiredCsrfCookie() {
        return new NewCookie.Builder(RemoteControlFilter.REMOTE_CSRF_COOKIE)
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

    private void requireRemoteEnabled() {
        if (!inboxBridgeConfig.security().remote().enabled()) {
            throw new jakarta.ws.rs.ForbiddenException("Remote control is disabled");
        }
    }

    private String directRemoteAddress() {
        if (httpServerRequest == null || httpServerRequest.remoteAddress() == null) {
            return null;
        }
        return httpServerRequest.remoteAddress().hostAddress();
    }
}
