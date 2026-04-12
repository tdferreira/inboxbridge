package dev.inboxbridge.web.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ExtensionAuthLoginRequest;
import dev.inboxbridge.dto.ExtensionAuthPasskeyVerifyRequest;
import dev.inboxbridge.dto.ExtensionAuthRefreshRequest;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.service.auth.AuthClientAddressService;
import dev.inboxbridge.service.auth.AuthLoginProtectionService;
import dev.inboxbridge.service.auth.AuthService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.extension.ExtensionBrowserAuthHandoffService;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.oauth.PublicUrlService;
import dev.inboxbridge.service.user.UserUiPreferenceService;
import dev.inboxbridge.security.CurrentUserContext;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

class ExtensionAuthResourceTest {

    @Test
    void loginReturnsAuthenticatedExtensionSession() {
        ExtensionAuthResource resource = configuredResource();
        resource.authService = new AuthService() {
            @Override
            public AuthenticationResult authenticate(String username, String password) {
                AppUser user = new AppUser();
                user.id = 7L;
                user.username = "alice";
                return AuthenticationResult.authenticated(user, null);
            }
        };
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public CreatedExtensionAuthSession createAuthenticatedSession(AppUser user, String label, String browserFamily, String extensionVersion) {
                ExtensionSession session = new ExtensionSession();
                session.id = 11L;
                session.label = label;
                session.browserFamily = browserFamily;
                session.extensionVersion = extensionVersion;
                session.accessExpiresAt = Instant.parse("2026-04-12T12:00:00Z");
                session.expiresAt = Instant.parse("2026-05-12T12:00:00Z");
                return new CreatedExtensionAuthSession("access-1", "refresh-1", session);
            }
        };

        Response response = resource.login(new ExtensionAuthLoginRequest("alice", "secret", "Laptop", "chromium", "0.1.0"));

        assertEquals(200, response.getStatus());
        var payload = (dev.inboxbridge.dto.ExtensionAuthResponse) response.getEntity();
        assertEquals("AUTHENTICATED", payload.status());
        assertEquals("https://mail.example.com", payload.session().publicBaseUrl());
        assertEquals("alice", payload.session().user().username());
        assertEquals("pt-BR", payload.session().user().language());
        assertEquals("DARK_BLUE", payload.session().user().themeMode());
        assertEquals("access-1", payload.session().tokens().accessToken());
        assertEquals("refresh-1", payload.session().tokens().refreshToken());
    }

    @Test
    void loginReturnsPasskeyChallengeWhenPasswordFlowNeedsIt() {
        ExtensionAuthResource resource = configuredResource();
        resource.authService = new AuthService() {
            @Override
            public AuthenticationResult authenticate(String username, String password) {
                return AuthenticationResult.passkeyRequired(new StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}"));
            }
        };

        Response response = resource.login(new ExtensionAuthLoginRequest("alice", "secret", null, null, null));

        assertEquals(200, response.getStatus());
        var payload = (dev.inboxbridge.dto.ExtensionAuthResponse) response.getEntity();
        assertEquals("PASSKEY_REQUIRED", payload.status());
        assertEquals("ceremony-1", payload.passkeyChallenge().ceremonyId());
    }

    @Test
    void passkeyVerifyCreatesAnAuthenticatedExtensionSession() {
        ExtensionAuthResource resource = configuredResource();
        Set<String> observedOrigins = new java.util.LinkedHashSet<>();
        resource.passkeyService = new PasskeyService() {
            @Override
            public PasskeyAuthenticationResult finishAuthentication(
                    dev.inboxbridge.dto.FinishPasskeyCeremonyRequest request,
                    Collection<String> additionalAllowedOrigins) {
                observedOrigins.addAll(additionalAllowedOrigins);
                AppUser user = new AppUser();
                user.id = 9L;
                user.username = "carol";
                return new PasskeyAuthenticationResult(user, false);
            }
        };
        resource.authService = new AuthService() {
            @Override
            public AuthenticationResult authenticateWithPasskey(PasskeyService.PasskeyAuthenticationResult result) {
                return AuthenticationResult.authenticated(result.user(), null);
            }
        };
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public CreatedExtensionAuthSession createAuthenticatedSession(AppUser user, String label, String browserFamily, String extensionVersion) {
                ExtensionSession session = new ExtensionSession();
                session.id = 15L;
                session.label = "Passkey browser";
                session.browserFamily = "firefox";
                session.extensionVersion = "0.1.0";
                session.accessExpiresAt = Instant.parse("2026-04-12T12:00:00Z");
                session.expiresAt = Instant.parse("2026-05-12T12:00:00Z");
                return new CreatedExtensionAuthSession("access-passkey", "refresh-passkey", session);
            }
        };

        var payload = resource.finishPasskeyLogin(new ExtensionAuthPasskeyVerifyRequest("ceremony-1", "{\"id\":\"cred\"}", null, null, null));

        assertEquals("AUTHENTICATED", payload.status());
        assertEquals("carol", payload.session().user().username());
        assertEquals("pt-BR", payload.session().user().language());
        assertEquals("access-passkey", payload.session().tokens().accessToken());
        assertEquals(Set.of("chrome-extension://test-extension"), observedOrigins);
    }

    @Test
    void refreshRejectsUnknownRefreshTokens() {
        ExtensionAuthResource resource = configuredResource();
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public java.util.Optional<CreatedExtensionAuthSession> refresh(String rawRefreshToken) {
                return java.util.Optional.empty();
            }
        };

        assertThrows(NotAuthorizedException.class, () -> resource.refresh(new ExtensionAuthRefreshRequest("missing")));
    }

    @Test
    void refreshReturnsRotatedTokens() {
        ExtensionAuthResource resource = configuredResource();
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public java.util.Optional<CreatedExtensionAuthSession> refresh(String rawRefreshToken) {
                ExtensionSession session = new ExtensionSession();
                session.id = 21L;
                session.label = "Laptop";
                session.browserFamily = "chromium";
                session.extensionVersion = "0.1.0";
                session.accessExpiresAt = Instant.parse("2026-04-12T12:00:00Z");
                session.expiresAt = Instant.parse("2026-05-12T12:00:00Z");
                return java.util.Optional.of(new CreatedExtensionAuthSession("access-2", "refresh-2", session));
            }
        };

        var payload = resource.refresh(new ExtensionAuthRefreshRequest("refresh-1"));

        assertEquals("AUTHENTICATED", payload.status());
        assertEquals("access-2", payload.session().tokens().accessToken());
        assertNotNull(payload.session().tokens().refreshExpiresAt());
    }

    @Test
    void browserHandoffStartReturnsRequestIdAndBrowserUrl() {
        ExtensionAuthResource resource = configuredResource();
        resource.extensionBrowserAuthHandoffService = new ExtensionBrowserAuthHandoffService() {
            @Override
            public StartedBrowserAuthHandoff start(String codeChallenge, String codeChallengeMethod, String label, String browserFamily, String extensionVersion) {
                assertEquals("challenge-1", codeChallenge);
                assertEquals("S256", codeChallengeMethod);
                return new StartedBrowserAuthHandoff("request-1", Instant.parse("2026-04-12T12:05:00Z"));
            }
        };

        var payload = resource.startBrowserHandoff(new dev.inboxbridge.dto.ExtensionBrowserAuthStartRequest(
                "challenge-1",
                "S256",
                "Laptop",
                "chromium",
                "0.1.0"));

        assertEquals("request-1", payload.requestId());
        assertEquals("https://mail.example.com/?extensionAuthRequest=request-1", payload.browserUrl());
    }

    @Test
    void browserHandoffStartPrefersTheCurrentRequestOriginOverConfiguredPublicBaseUrl() {
        ExtensionAuthResource resource = configuredResource();
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Origin", "chrome-extension://test-extension");
        headers.add("X-Forwarded-Proto", "https");
        headers.add("X-Forwarded-Host", "actual.example.com");
        resource.httpHeaders = staticHttpHeaders(headers);
        resource.extensionBrowserAuthHandoffService = new ExtensionBrowserAuthHandoffService() {
            @Override
            public StartedBrowserAuthHandoff start(String codeChallenge, String codeChallengeMethod, String label, String browserFamily, String extensionVersion) {
                return new StartedBrowserAuthHandoff("request-2", Instant.parse("2026-04-12T12:05:00Z"));
            }
        };

        var payload = resource.startBrowserHandoff(new dev.inboxbridge.dto.ExtensionBrowserAuthStartRequest(
                "challenge-1",
                "S256",
                "Laptop",
                "chromium",
                "0.1.0"));

        assertEquals("https://actual.example.com/?extensionAuthRequest=request-2", payload.browserUrl());
    }

    @Test
    void browserHandoffCompleteUsesAuthenticatedBrowserUser() {
        ExtensionAuthResource resource = configuredResource();
        AppUser user = new AppUser();
        user.id = 17L;
        resource.currentUserContext = new CurrentUserContext();
        resource.currentUserContext.setUser(user);
        resource.extensionBrowserAuthHandoffService = new ExtensionBrowserAuthHandoffService() {
            @Override
            public boolean complete(String requestId, AppUser completedUser) {
                assertEquals("request-1", requestId);
                assertEquals(17L, completedUser.id);
                return true;
            }
        };

        var payload = resource.completeBrowserHandoff(new dev.inboxbridge.dto.ExtensionBrowserAuthCompleteRequest("request-1"));

        assertEquals("COMPLETED", payload.status());
    }

    @Test
    void browserHandoffRedeemReturnsPendingAndAuthenticatedStates() {
        ExtensionAuthResource resource = configuredResource();
        resource.extensionBrowserAuthHandoffService = new ExtensionBrowserAuthHandoffService() {
            @Override
            public BrowserAuthRedeemResult redeem(String requestId, String codeVerifier) {
                assertEquals("request-1", requestId);
                assertEquals("verifier-1", codeVerifier);
                ExtensionSession session = new ExtensionSession();
                session.id = 32L;
                session.userId = 9L;
                session.label = "Laptop";
                session.browserFamily = "firefox";
                session.extensionVersion = "0.1.0";
                session.accessExpiresAt = Instant.parse("2026-04-12T12:00:00Z");
                session.expiresAt = Instant.parse("2026-05-12T12:00:00Z");
                return BrowserAuthRedeemResult.authenticated(
                        new ExtensionSessionService.CreatedExtensionAuthSession("access-handoff", "refresh-handoff", session),
                        Instant.parse("2026-04-12T12:05:00Z"));
            }
        };

        var payload = resource.redeemBrowserHandoff(new dev.inboxbridge.dto.ExtensionBrowserAuthRedeemRequest("request-1", "verifier-1"));

        assertEquals("AUTHENTICATED", payload.status());
        assertEquals("access-handoff", payload.session().tokens().accessToken());
    }

    private static ExtensionAuthResource configuredResource() {
        ExtensionAuthResource resource = new ExtensionAuthResource();
        resource.authClientAddressService = new AuthClientAddressService() {
            @Override
            public String resolveClientKey(jakarta.ws.rs.core.HttpHeaders httpHeaders, String directRemoteAddress) {
                return "203.0.113.5";
            }
        };
        resource.authLoginProtectionService = new AuthLoginProtectionService() {
            @Override
            public void requireLoginAllowed(String clientKey) {
            }

            @Override
            public void recordSuccessfulLogin(String clientKey) {
            }

            @Override
            public FailureResult recordFailedLogin(String clientKey) {
                return new FailureResult(false, null);
            }
        };
        resource.publicUrlService = new PublicUrlService() {
            @Override
            public String publicBaseUrl() {
                return "https://mail.example.com";
            }
        };
        resource.userUiPreferenceService = new UserUiPreferenceService() {
            @Override
            public dev.inboxbridge.dto.UserUiPreferenceView defaultView() {
                return new dev.inboxbridge.dto.UserUiPreferenceView(
                        false, false, false, false, false, false, false, false, false, false, false, false, false,
                        false, false, false, List.of(), List.of(), "pt-BR", "DARK_BLUE", "AUTO", "AUTO", "", List.of());
            }

            @Override
            public java.util.Optional<dev.inboxbridge.dto.UserUiPreferenceView> viewForUser(Long userId) {
                return java.util.Optional.of(defaultView());
            }
        };
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Origin", "chrome-extension://test-extension");
        resource.httpHeaders = staticHttpHeaders(headers);
        return resource;
    }

    private static HttpHeaders staticHttpHeaders(MultivaluedHashMap<String, String> headers) {
        return new HttpHeaders() {
            @Override
            public List<String> getRequestHeader(String name) {
                List<String> values = headers.get(name);
                return values == null ? List.of() : values;
            }

            @Override
            public String getHeaderString(String name) {
                return getRequestHeader(name).stream().findFirst().orElse(null);
            }

            @Override
            public MultivaluedHashMap<String, String> getRequestHeaders() {
                return headers;
            }

            @Override public List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() { return List.of(); }
            @Override public List<java.util.Locale> getAcceptableLanguages() { return List.of(); }
            @Override public jakarta.ws.rs.core.MediaType getMediaType() { return null; }
            @Override public java.util.Locale getLanguage() { return null; }
            @Override public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() { return java.util.Map.of(); }
            @Override public java.util.Date getDate() { return null; }
            @Override public int getLength() { return 0; }
        };
    }
}
