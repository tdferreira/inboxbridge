package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CurrentUserContext {

    private AppUser user;

    public AppUser user() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }
}
