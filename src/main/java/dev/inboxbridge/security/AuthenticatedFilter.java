package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.service.AuthService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;

@Provider
@RequireAuth
@Priority(Priorities.AUTHENTICATION)
public class AuthenticatedFilter implements ContainerRequestFilter {

    public static final String SESSION_COOKIE = "inboxbridge_session";

    @Inject
    AuthService authService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    ResourceInfo resourceInfo;

    @Inject
    BrowserSessionSecurity browserSessionSecurity;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Cookie sessionCookie = requestContext.getCookies().get(SESSION_COOKIE);
        AuthService.AuthenticatedRequest authenticatedRequest;
        try {
            authenticatedRequest = authService.requireAuthenticatedRequest(sessionCookie == null ? null : sessionCookie.getValue());
        } catch (IllegalArgumentException e) {
            throw new NotAuthorizedException("Not authenticated", e);
        }
        if (browserSessionSecurity.requiresCsrfValidation(requestContext)) {
            browserSessionSecurity.validateOrigin(requestContext);
            browserSessionSecurity.validateCsrf(requestContext, authenticatedRequest.session());
        }
        currentUserContext.setUser(authenticatedRequest.user());
        currentUserContext.setSession(authenticatedRequest.session());
    }
}
