package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.service.AuthService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;

@Provider
@RequireAdmin
@Priority(Priorities.AUTHORIZATION)
public class AdminFilter implements ContainerRequestFilter {

    @Inject
    AuthService authService;

    @Inject
    CurrentUserContext currentUserContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Cookie sessionCookie = requestContext.getCookies().get(AuthenticatedFilter.SESSION_COOKIE);
        AppUser user;
        try {
            user = authService.requireAuthenticatedUser(sessionCookie == null ? null : sessionCookie.getValue());
        } catch (IllegalArgumentException e) {
            throw new NotAuthorizedException("Not authenticated", e);
        }
        if (user.role != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Admin access required");
        }
        currentUserContext.setUser(user);
    }
}
