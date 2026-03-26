package dev.inboxbridge.web;

import dev.inboxbridge.dto.LoginRequest;
import dev.inboxbridge.dto.RegisterUserRequest;
import dev.inboxbridge.dto.SessionUserResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.AuthenticatedFilter;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.AuthService;
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

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest request) {
        try {
            AuthService.AuthenticatedSession session = authService.login(request.username(), request.password());
            return Response.ok(toResponse(session.user()))
                    .cookie(sessionCookie(session.token()))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(RegisterUserRequest request) {
        try {
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

    private SessionUserResponse toResponse(AppUser user) {
        return new SessionUserResponse(user.id, user.username, user.role.name(), user.approved, user.mustChangePassword);
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
}
