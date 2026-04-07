package dev.inboxbridge.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.remote.RemoteServiceTokenAuthService;
import dev.inboxbridge.service.remote.RemoteSessionService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;

class RemoteControlFilterTest {

    @Test
    void serviceTokenBypassesRemoteSessionCookieValidation() {
        AppUser admin = user(1L, "admin", AppUser.Role.ADMIN);
        RemoteControlFilter filter = new RemoteControlFilter();
        filter.remoteControlEnabled = true;
        filter.remoteServiceTokenAuthService = new RemoteServiceTokenAuthService() {
            @Override
            public Optional<AppUser> authenticate(String authorizationHeader) {
                return Optional.of(admin);
            }
        };
        filter.appUserService = new AppUserService();
        filter.remoteSessionService = new RemoteSessionService();
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = new BrowserSessionSecurity();

        filter.filter(requestContext(
                "POST",
                URI.create("https://app.example.com/api/remote/control/poll"),
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer secret"),
                Map.of()));

        assertEquals(admin, filter.currentUserContext.user());
    }

    @Test
    void browserRemoteSessionRequiresMatchingOriginAndCsrf() {
        AppUser user = user(7L, "alice", AppUser.Role.USER);
        RemoteSession remoteSession = new RemoteSession();
        remoteSession.userId = user.id;

        RemoteControlFilter filter = new RemoteControlFilter();
        filter.remoteControlEnabled = true;
        filter.remoteServiceTokenAuthService = new RemoteServiceTokenAuthService() {
            @Override
            public Optional<AppUser> authenticate(String authorizationHeader) {
                return Optional.empty();
            }
        };
        filter.appUserService = new AppUserService() {
            @Override
            public Optional<AppUser> findById(Long id) {
                return Optional.of(user);
            }
        };
        filter.remoteSessionService = new RemoteSessionService() {
            @Override
            public Optional<RemoteSession> findValidSession(String token) {
                return Optional.of(remoteSession);
            }

            @Override
            public boolean csrfMatches(RemoteSession session, String csrfToken) {
                return "remote-csrf".equals(csrfToken);
            }
        };
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = new BrowserSessionSecurity();

        filter.filter(requestContext(
                "POST",
                URI.create("https://app.example.com/api/remote/control/poll"),
                Map.of(
                        "Origin", "https://app.example.com",
                        HttpHeaders.HOST, "app.example.com",
                        RemoteControlFilter.REMOTE_CSRF_HEADER, "remote-csrf"),
                Map.of(
                        RemoteControlFilter.REMOTE_SESSION_COOKIE, new Cookie(RemoteControlFilter.REMOTE_SESSION_COOKIE, "remote-session"),
                        RemoteControlFilter.REMOTE_CSRF_COOKIE, new Cookie(RemoteControlFilter.REMOTE_CSRF_COOKIE, "remote-csrf"))));

        assertEquals(user, filter.currentUserContext.user());
        assertEquals(remoteSession, filter.currentUserContext.remoteSession());
    }

    @Test
    void browserRemoteSessionRejectsMissingCsrfHeader() {
        AppUser user = user(7L, "alice", AppUser.Role.USER);
        RemoteSession remoteSession = new RemoteSession();
        remoteSession.userId = user.id;

        RemoteControlFilter filter = new RemoteControlFilter();
        filter.remoteControlEnabled = true;
        filter.remoteServiceTokenAuthService = new RemoteServiceTokenAuthService() {
            @Override
            public Optional<AppUser> authenticate(String authorizationHeader) {
                return Optional.empty();
            }
        };
        filter.appUserService = new AppUserService() {
            @Override
            public Optional<AppUser> findById(Long id) {
                return Optional.of(user);
            }
        };
        filter.remoteSessionService = new RemoteSessionService() {
            @Override
            public Optional<RemoteSession> findValidSession(String token) {
                return Optional.of(remoteSession);
            }
        };
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = new BrowserSessionSecurity();

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> filter.filter(requestContext(
                        "POST",
                        URI.create("https://app.example.com/api/remote/control/poll"),
                        Map.of(
                                "Origin", "https://app.example.com",
                                HttpHeaders.HOST, "app.example.com"),
                        Map.of(
                                RemoteControlFilter.REMOTE_SESSION_COOKIE, new Cookie(RemoteControlFilter.REMOTE_SESSION_COOKIE, "remote-session"),
                                RemoteControlFilter.REMOTE_CSRF_COOKIE, new Cookie(RemoteControlFilter.REMOTE_CSRF_COOKIE, "remote-csrf")))));

        assertEquals("Remote session CSRF validation failed", error.getMessage());
    }

    @Test
    void missingRemoteSessionIsRejected() {
        RemoteControlFilter filter = new RemoteControlFilter();
        filter.remoteControlEnabled = true;
        filter.remoteServiceTokenAuthService = new RemoteServiceTokenAuthService() {
            @Override
            public Optional<AppUser> authenticate(String authorizationHeader) {
                return Optional.empty();
            }
        };
        filter.appUserService = new AppUserService();
        filter.remoteSessionService = new RemoteSessionService();
        filter.currentUserContext = new CurrentUserContext();
        filter.browserSessionSecurity = new BrowserSessionSecurity();

        assertThrows(
                NotAuthorizedException.class,
                () -> filter.filter(requestContext(
                        "GET",
                        URI.create("https://app.example.com/api/remote/control"),
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
}
