package dev.inboxbridge.web;

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
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.ApplicationModeService;
import dev.inboxbridge.service.AuthService;
import dev.inboxbridge.service.AuthClientAddressService;
import dev.inboxbridge.service.AuthLoginProtectionService;
import dev.inboxbridge.service.AuthSecuritySettingsService;
import dev.inboxbridge.service.MicrosoftOAuthService;
import dev.inboxbridge.service.OAuthProviderRegistryService;
import dev.inboxbridge.service.PasskeyService;
import dev.inboxbridge.service.RegistrationChallengeService;
import dev.inboxbridge.service.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.UserSessionService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
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

    @Context
    HttpHeaders httpHeaders;

    @Context
    HttpServerRequest httpServerRequest;

    @GET
    @Path("/options")
    public AuthUiOptionsResponse options() {
        return new AuthUiOptionsResponse(
                applicationModeService.multiUserEnabled(),
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
            return Response.ok(LoginResponse.authenticated(toResponse(session.user())))
                    .cookie(sessionCookie(session.token()))
                    .build();
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
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(RegisterUserRequest request) {
        try {
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
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/passkey/options")
    @Consumes(MediaType.APPLICATION_JSON)
    public StartPasskeyCeremonyResponse startPasskeyLogin(StartPasskeyLoginRequest request) {
        try {
            if (request != null && request.username() != null && !request.username().isBlank()) {
                AppUser user = appUserService.findByUsername(request.username().trim())
                        .filter(found -> found.active && found.approved)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown account for passkey sign-in"));
                if (appUserService.hasPassword(user) && appUserService.requiresPasskey(user)) {
                    throw new IllegalArgumentException("This account requires password verification before passkey sign-in.");
                }
                if (!appUserService.requiresPasskey(user)) {
                    throw new IllegalArgumentException("This account does not have a passkey configured.");
                }
                return passkeyService.startAuthenticationForUser(user, false);
            }
            return passkeyService.startAuthentication();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/passkey/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response finishPasskeyLogin(FinishPasskeyCeremonyRequest request) {
        try {
            String clientKey = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
            String userAgent = httpHeaders == null ? null : httpHeaders.getHeaderString("User-Agent");
            PasskeyService.PasskeyAuthenticationResult authResult = passkeyService.finishAuthentication(request);
            AuthService.AuthenticatedSession session = authService.loginWithPasskey(authResult, clientKey, userAgent);
            return Response.ok(LoginResponse.authenticated(toResponse(session.user())))
                    .cookie(sessionCookie(session.token()))
                    .build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/logout")
    @RequireAuth
    public Response logout(@jakarta.ws.rs.CookieParam(AuthenticatedFilter.SESSION_COOKIE) String token) {
        authService.logout(token);
        return Response.noContent().cookie(expiredCookie()).build();
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
        try {
            userSessionService.recordDeviceLocation(
                    currentUserContext.session() == null ? null : currentUserContext.session().id,
                    request == null ? null : request.latitude(),
                    request == null ? null : request.longitude(),
                    request == null ? null : request.accuracyMeters());
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    private SessionUserResponse toResponse(AppUser user) {
        return new SessionUserResponse(
                user.id,
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
