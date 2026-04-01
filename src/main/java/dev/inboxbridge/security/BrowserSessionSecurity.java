package dev.inboxbridge.security;

import java.net.URI;

import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.service.UserSessionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class BrowserSessionSecurity {

    public static final String CSRF_COOKIE = "inboxbridge_csrf";
    public static final String CSRF_HEADER = "X-InboxBridge-CSRF";

    @Inject
    UserSessionService userSessionService;

    public boolean requiresCsrfValidation(ContainerRequestContext requestContext) {
        String method = requestContext.getMethod();
        return !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
    }

    public void validateOrigin(ContainerRequestContext requestContext) {
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
            throw new ForbiddenException("Session origin validation failed");
        }
    }

    public void validateCsrf(ContainerRequestContext requestContext, UserSession session) {
        Cookie csrfCookie = requestContext.getCookies().get(CSRF_COOKIE);
        String csrfHeader = requestContext.getHeaderString(CSRF_HEADER);
        String csrfCookieValue = csrfCookie == null ? null : csrfCookie.getValue();
        if (csrfHeader == null || csrfCookieValue == null || !csrfHeader.equals(csrfCookieValue)
                || !userSessionService.csrfMatches(session, csrfHeader)) {
            throw new ForbiddenException("Session CSRF validation failed");
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
