package dev.inboxbridge.security;

import java.net.URI;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.RemoteServiceTokenAuthService;
import dev.inboxbridge.service.RemoteSessionService;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!remoteControlEnabled) {
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
    }

    private boolean requiresCsrfValidation(ContainerRequestContext requestContext) {
        String method = requestContext.getMethod();
        return !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
    }

    private void validateOrigin(ContainerRequestContext requestContext) {
        String originHeader = requestContext.getHeaderString("Origin");
        if (originHeader == null || originHeader.isBlank()) {
            return;
        }
        URI originUri = URI.create(originHeader);
        String forwardedProto = firstForwardedValue(requestContext.getHeaderString("X-Forwarded-Proto"));
        String forwardedHost = firstForwardedValue(requestContext.getHeaderString("X-Forwarded-Host"));
        String forwardedPort = firstForwardedValue(requestContext.getHeaderString("X-Forwarded-Port"));
        String hostHeader = firstForwardedValue(requestContext.getHeaderString(HttpHeaders.HOST));

        URI requestUri = requestContext.getUriInfo().getRequestUri();
        String expectedScheme = forwardedProto != null ? forwardedProto : requestUri.getScheme();
        String expectedHostPort = forwardedHost != null ? forwardedHost : hostHeader;
        if (expectedHostPort == null || expectedHostPort.isBlank()) {
            expectedHostPort = requestUri.getHost()
                    + (requestUri.getPort() > 0 ? ":" + requestUri.getPort() : "");
        }
        if (forwardedPort != null && !forwardedPort.isBlank() && !containsExplicitPort(expectedHostPort)) {
            expectedHostPort = expectedHostPort + ":" + forwardedPort;
        }

        URI expectedOrigin = URI.create(expectedScheme + "://" + expectedHostPort);
        boolean sameOrigin = equalsIgnoreCase(originUri.getScheme(), expectedOrigin.getScheme())
                && equalsIgnoreCase(originUri.getHost(), expectedOrigin.getHost())
                && effectivePort(originUri) == effectivePort(expectedOrigin);
        if (!sameOrigin) {
            throw new ForbiddenException("Remote session origin validation failed");
        }
    }

    private String firstForwardedValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String first = headerValue.split(",", 2)[0].trim();
        return first.isBlank() ? null : first;
    }

    private boolean containsExplicitPort(String hostValue) {
        if (hostValue == null || hostValue.isBlank()) {
            return false;
        }
        int closingBracket = hostValue.lastIndexOf(']');
        int colonIndex = hostValue.lastIndexOf(':');
        return colonIndex > -1 && colonIndex > closingBracket;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
