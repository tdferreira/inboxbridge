package dev.inboxbridge.service;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {

    @Inject
    AppUserService appUserService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    PasskeyService passkeyService;

    public LoginResult login(String username, String password) {
        AppUser user = appUserService.findByUsername(username)
                .filter(found -> found.active && found.approved)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        boolean hasPassword = appUserService.hasPassword(user);
        boolean requiresPasskey = appUserService.requiresPasskey(user);

        // Accounts with both factors configured treat the password form as the
        // first factor and only create the session after a successful passkey
        // ceremony completes.
        if (requiresPasskey && hasPassword) {
            if (!appUserService.passwordMatches(user, password)) {
                throw new IllegalArgumentException("Invalid username or password");
            }
            return LoginResult.passkeyRequired(passkeyService.startAuthenticationForUser(user, true));
        }

        if (requiresPasskey) {
            return LoginResult.passkeyRequired(passkeyService.startAuthenticationForUser(user, false));
        }

        if (!hasPassword || !appUserService.passwordMatches(user, password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String token = userSessionService.createSession(user);
        return LoginResult.authenticated(user, token);
    }

    public AuthenticatedSession loginWithPasskey(AppUser user) {
        AppUser authenticatedUser = appUserService.findById(user.id)
                .filter(found -> found.active && found.approved)
                .orElseThrow(() -> new IllegalArgumentException("Invalid passkey sign-in"));
        String token = userSessionService.createSession(authenticatedUser);
        return new AuthenticatedSession(authenticatedUser, token);
    }

    public AppUser requireAuthenticatedUser(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Not authenticated");
        }
        UserSession session = userSessionService.findValidSession(token)
                .orElseThrow(() -> new IllegalArgumentException("Not authenticated"));
        return appUserService.findById(session.userId)
                .filter(user -> user.active && user.approved)
                .orElseThrow(() -> new IllegalArgumentException("Not authenticated"));
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            userSessionService.invalidate(token);
        }
    }

    public record AuthenticatedSession(AppUser user, String token) {
    }

    public record LoginResult(LoginStatus status, AuthenticatedSession session,
            dev.inboxbridge.dto.StartPasskeyCeremonyResponse passkeyChallenge) {

        public static LoginResult authenticated(AppUser user, String token) {
            return new LoginResult(LoginStatus.AUTHENTICATED, new AuthenticatedSession(user, token), null);
        }

        public static LoginResult passkeyRequired(dev.inboxbridge.dto.StartPasskeyCeremonyResponse challenge) {
            return new LoginResult(LoginStatus.PASSKEY_REQUIRED, null, challenge);
        }
    }

    public enum LoginStatus {
        AUTHENTICATED,
        PASSKEY_REQUIRED
    }
}
