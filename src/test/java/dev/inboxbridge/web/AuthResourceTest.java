package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.LoginRequest;
import dev.inboxbridge.dto.LoginResponse;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.StartPasskeyLoginRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.AuthService;
import dev.inboxbridge.service.PasskeyService;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

class AuthResourceTest {

    @Test
    void startPasskeyLoginReturnsCeremonyPayload() {
        AuthResource resource = new AuthResource();
        resource.passkeyService = new FakePasskeyService();

        StartPasskeyCeremonyResponse response = resource.startPasskeyLogin(new StartPasskeyLoginRequest(null));

        assertEquals("ceremony-1", response.ceremonyId());
        assertEquals("{\"challenge\":\"abc\"}", response.publicKeyJson());
    }

    @Test
    void finishPasskeyLoginReturnsSessionCookie() {
        AuthResource resource = new AuthResource();
        resource.authService = new FakeAuthService();
        resource.passkeyService = new FakePasskeyService();
        resource.appUserService = new AppUserService();
        resource.currentUserContext = new CurrentUserContext();

        Response response = resource.finishPasskeyLogin(new FinishPasskeyCeremonyRequest("ceremony-1", "{\"id\":\"cred\"}"));

        assertEquals(200, response.getStatus());
        NewCookie cookie = response.getCookies().get("inboxbridge_session");
        assertNotNull(cookie);
        assertEquals("session-1", cookie.getValue());
        LoginResponse payload = (LoginResponse) response.getEntity();
        assertEquals("AUTHENTICATED", payload.status());
    }

    private static final class FakePasskeyService extends PasskeyService {
        @Override
        public StartPasskeyCeremonyResponse startAuthentication() {
            return new StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}");
        }

        @Override
        public StartPasskeyCeremonyResponse startAuthenticationForUser(AppUser user, boolean passwordVerified) {
            return new StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}");
        }

        @Override
        public AppUser finishAuthentication(FinishPasskeyCeremonyRequest request) {
            AppUser user = new AppUser();
            user.id = 7L;
            user.username = "admin";
            user.role = AppUser.Role.ADMIN;
            user.approved = true;
            user.active = true;
            return user;
        }

        @Override
        public long countForUser(Long userId) {
            return 2;
        }
    }

    private static final class FakeAuthService extends AuthService {
        @Override
        public LoginResult login(String username, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthenticatedSession loginWithPasskey(AppUser user) {
            return new AuthenticatedSession(user, "session-1");
        }
    }
}
