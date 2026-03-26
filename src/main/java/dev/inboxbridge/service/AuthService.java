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

    public AuthenticatedSession login(String username, String password) {
        AppUser user = appUserService.findByUsername(username)
                .filter(found -> found.active && found.approved)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!appUserService.passwordMatches(user, password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String token = userSessionService.createSession(user);
        return new AuthenticatedSession(user, token);
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
}
