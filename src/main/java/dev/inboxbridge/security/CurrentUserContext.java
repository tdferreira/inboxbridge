package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.UserSession;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CurrentUserContext {

    private AppUser user;
    private UserSession session;
    private RemoteSession remoteSession;
    private ExtensionSession extensionSession;

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

    public RemoteSession remoteSession() {
        return remoteSession;
    }

    public void setRemoteSession(RemoteSession remoteSession) {
        this.remoteSession = remoteSession;
    }

    public ExtensionSession extensionSession() {
        return extensionSession;
    }

    public void setExtensionSession(ExtensionSession extensionSession) {
        this.extensionSession = extensionSession;
    }
}
