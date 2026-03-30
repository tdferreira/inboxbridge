package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CurrentUserContext {

    private AppUser user;
    private UserSession session;

    public AppUser user() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public UserSession session() {
        return session;
    }

    public void setSession(UserSession session) {
        this.session = session;
    }
}
