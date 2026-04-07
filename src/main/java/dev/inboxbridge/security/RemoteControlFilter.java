package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.remote.RemoteServiceTokenAuthService;
import dev.inboxbridge.service.remote.RemoteSessionService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

@Provider
@RequireRemoteControl
@Priority(Priorities.AUTHENTICATION)
public class RemoteControlFilter implements ContainerRequestFilter {

    public static final String REMOTE_SESSION_COOKIE = "inboxbridge_remote_session";
    public static final String REMOTE_CSRF_COOKIE = "inboxbridge_remote_csrf";
    public static final String REMOTE_CSRF_HEADER = "X-InboxBridge-CSRF";

    @Inject
    RemoteSessionService remoteSessionService;

    @ConfigProperty(name = "inboxbridge.security.remote.enabled", defaultValue = "true")
    boolean remoteControlEnabled;

    @Inject
    RemoteServiceTokenAuthService remoteServiceTokenAuthService;

    @Inject
    AppUserService appUserService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    BrowserSessionSecurity browserSessionSecurity;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!remoteControlIsEnabled()) {
            throw new ForbiddenException("Remote control is disabled");
        }
        AppUser serviceTokenUser = remoteServiceTokenAuthService.authenticate(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
                .orElse(null);
        if (serviceTokenUser != null) {
            currentUserContext.setUser(serviceTokenUser);
            return;
        }

        Cookie sessionCookie = requestContext.getCookies().get(REMOTE_SESSION_COOKIE);
        RemoteSession remoteSession = remoteSessionService.findValidSession(sessionCookie == null ? null : sessionCookie.getValue())
                .orElseThrow(() -> new NotAuthorizedException("Not authenticated"));
        AppUser user = appUserService.findById(remoteSession.userId)
                .filter(candidate -> candidate.active && candidate.approved)
                .orElseThrow(() -> new NotAuthorizedException("Not authenticated"));

        if (requiresCsrfValidation(requestContext)) {
            validateOrigin(requestContext);
            Cookie csrfCookie = requestContext.getCookies().get(REMOTE_CSRF_COOKIE);
            String csrfHeader = requestContext.getHeaderString(REMOTE_CSRF_HEADER);
            String csrfCookieValue = csrfCookie == null ? null : csrfCookie.getValue();
            if (csrfHeader == null || csrfCookieValue == null || !csrfHeader.equals(csrfCookieValue)
                    || !remoteSessionService.csrfMatches(remoteSession, csrfHeader)) {
                throw new ForbiddenException("Remote session CSRF validation failed");
            }
        }

        currentUserContext.setUser(user);
        currentUserContext.setRemoteSession(remoteSession);
    }

    private boolean requiresCsrfValidation(ContainerRequestContext requestContext) {
        return browserSessionSecurity.requiresCsrfValidation(requestContext);
    }

    private void validateOrigin(ContainerRequestContext requestContext) {
        try {
            browserSessionSecurity.validateOrigin(requestContext);
        } catch (ForbiddenException forbidden) {
            throw new ForbiddenException("Remote session origin validation failed", forbidden);
        }
    }

    private boolean remoteControlIsEnabled() {
        return remoteControlEnabled;
    }
}
