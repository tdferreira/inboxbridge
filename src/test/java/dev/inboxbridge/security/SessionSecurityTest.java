package dev.inboxbridge.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.service.AuthService;
import dev.inboxbridge.service.UserSessionService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

class SessionSecurityTest {

    @Test
    void browserSessionSecurityAcceptsSameOriginUnsafeRequestWithMatchingCsrf() {
        BrowserSessionSecurity security = new BrowserSessionSecurity();
        security.userSessionService = new UserSessionService() {
            @Override
            public boolean csrfMatches(UserSession session, String csrfToken) {
                return "csrf-token".equals(csrfToken);
            }
        };
        UserSession session = new UserSession();
        ContainerRequestContext requestContext = requestContext(
                "POST",
                URI.create("https://app.example.com/api/poll/live/pause"),
                Map.of(
                        "Origin", "https://app.example.com",
                        BrowserSessionSecurity.CSRF_HEADER, "csrf-token",
                        HttpHeaders.HOST, "app.example.com"),
                Map.of(BrowserSessionSecurity.CSRF_COOKIE, new Cookie(BrowserSessionSecurity.CSRF_COOKIE, "csrf-token")));

        security.validateOrigin(requestContext);
        security.validateCsrf(requestContext, session);

        assertTrue(security.requiresCsrfValidation(requestContext));
    }

    @Test
    void browserSessionSecurityRejectsCrossOriginUnsafeRequest() {
        BrowserSessionSecurity security = new BrowserSessionSecurity();

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> security.validateOrigin(requestContext(
                        "POST",
                        URI.create("https://app.example.com/api/auth/logout"),
                        Map.of(
                                "Origin", "https://attacker.example.net",
                                HttpHeaders.HOST, "app.example.com"),
                        Map.of())));

        assertEquals("Session origin validation failed", error.getMessage());
    }

    @Test
    void browserSessionSecurityRejectsMissingOrMismatchedCsrf() {
        BrowserSessionSecurity security = new BrowserSessionSecurity();
        security.userSessionService = new UserSessionService() {
            @Override
            public boolean csrfMatches(UserSession session, String csrfToken) {
                return false;
            }
        };

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> security.validateCsrf(
                        requestContext(
                                "POST",
                                URI.create("https://app.example.com/api/auth/logout"),
                                Map.of(HttpHeaders.HOST, "app.example.com"),
                                Map.of(BrowserSessionSecurity.CSRF_COOKIE,
                                        new Cookie(BrowserSessionSecurity.CSRF_COOKIE, "cookie-token"))),
                        new UserSession()));

        assertEquals("Session CSRF validation failed", error.getMessage());
    }

    @Test
    void authenticatedFilterEnforcesBrowserSessionChecksForUnsafeMethods() {
        AppUser user = user(7L, "alice", AppUser.Role.USER);
        UserSession session = new UserSession();
        session.userId = user.id;
        TrackingBrowserSessionSecurity browserSecurity = new TrackingBrowserSessionSecurity();

        AuthenticatedFilter filter = new AuthenticatedFilter();
        filter.authService = new AuthService() {
            @Override
            public AuthenticatedRequest requireAuthenticatedRequest(String token) {
                assertEquals("session-1", token);
                return new AuthenticatedRequest(user, session);
            }
        };
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = browserSecurity;

        filter.filter(requestContext(
                "POST",
                URI.create("https://app.example.com/api/poll/live/stop"),
                Map.of(HttpHeaders.HOST, "app.example.com"),
                Map.of(AuthenticatedFilter.SESSION_COOKIE, new Cookie(AuthenticatedFilter.SESSION_COOKIE, "session-1"))));

        assertTrue(browserSecurity.originValidated);
        assertTrue(browserSecurity.csrfValidated);
        assertEquals(user, filter.currentUserContext.user());
        assertEquals(session, filter.currentUserContext.session());
    }

    @Test
    void adminFilterRejectsNonAdminUsers() {
        AppUser user = user(7L, "alice", AppUser.Role.USER);
        UserSession session = new UserSession();
        session.userId = user.id;

        AdminFilter filter = new AdminFilter();
        filter.authService = new AuthService() {
            @Override
            public AuthenticatedRequest requireAuthenticatedRequest(String token) {
                return new AuthenticatedRequest(user, session);
            }
        };
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = new TrackingBrowserSessionSecurity();

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> filter.filter(requestContext(
                        "GET",
                        URI.create("https://app.example.com/api/admin/dashboard"),
                        Map.of(HttpHeaders.HOST, "app.example.com"),
                        Map.of(AuthenticatedFilter.SESSION_COOKIE, new Cookie(AuthenticatedFilter.SESSION_COOKIE, "session-1")))));

        assertEquals("Admin access required", error.getMessage());
    }

    @Test
    void authenticatedFilterRejectsMissingSession() {
        AuthenticatedFilter filter = new AuthenticatedFilter();
        filter.authService = new AuthService() {
            @Override
            public AuthenticatedRequest requireAuthenticatedRequest(String token) {
                throw new IllegalArgumentException("Not authenticated");
            }
        };
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = new TrackingBrowserSessionSecurity();

        assertThrows(
                NotAuthorizedException.class,
                () -> filter.filter(requestContext(
                        "GET",
                        URI.create("https://app.example.com/api/auth/me"),
                        Map.of(HttpHeaders.HOST, "app.example.com"),
                        Map.of())));
    }

    private static ContainerRequestContext requestContext(
            String method,
            URI requestUri,
            Map<String, String> headers,
            Map<String, Cookie> cookies) {
        MultivaluedHashMap<String, String> headerMap = new MultivaluedHashMap<>();
        headers.forEach(headerMap::putSingle);
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class<?>[] { UriInfo.class },
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getRequestUri" -> requestUri;
                    case "getPath" -> requestUri.getPath().startsWith("/")
                            ? requestUri.getPath().substring(1)
                            : requestUri.getPath();
                    default -> null;
                });
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[] { ContainerRequestContext.class },
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getMethod" -> method;
                    case "getHeaderString" -> headerMap.getFirst((String) args[0]);
                    case "getHeaders" -> headerMap;
                    case "getCookies" -> cookies;
                    case "getUriInfo" -> uriInfo;
                    default -> null;
                });
    }

    private static AppUser user(Long id, String username, AppUser.Role role) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = username;
        user.role = role;
        user.active = true;
        user.approved = true;
        return user;
    }

    private static final class TrackingBrowserSessionSecurity extends BrowserSessionSecurity {
        private boolean originValidated;
        private boolean csrfValidated;

        @Override
        public void validateOrigin(ContainerRequestContext requestContext) {
            originValidated = true;
        }

        @Override
        public void validateCsrf(ContainerRequestContext requestContext, UserSession session) {
            csrfValidated = true;
        }

        @Override
        public boolean requiresCsrfValidation(ContainerRequestContext requestContext) {
            return !("GET".equals(requestContext.getMethod()) || "HEAD".equals(requestContext.getMethod())
                    || "OPTIONS".equals(requestContext.getMethod()));
        }
    }
}
