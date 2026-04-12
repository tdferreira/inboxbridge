package dev.inboxbridge.web.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ExtensionSessionCreateRequest;
import dev.inboxbridge.dto.ExtensionSessionCreateView;
import dev.inboxbridge.dto.ExtensionSessionView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.polling.PollingLiveService;
import jakarta.ws.rs.core.Response;

class ExtensionSessionResourceTest {

    @Test
    void createAndListDelegateToTheCurrentUserScope() {
        ExtensionSessionResource resource = new ExtensionSessionResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 5L;
        user.username = "alice";
        context.setUser(user);
        resource.currentUserContext = context;
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public ExtensionSessionCreateView createSession(AppUser actor, ExtensionSessionCreateRequest request) {
                assertEquals(5L, actor.id);
                return new ExtensionSessionCreateView(1L, "Chrome", "chromium", "0.1.0", "token", "ibx_123", Instant.now(), null, null, null);
            }

            @Override
            public List<ExtensionSessionView> listSessions(AppUser actor) {
                assertEquals(5L, actor.id);
                return List.of(new ExtensionSessionView(1L, "Chrome", "chromium", "0.1.0", "ibx_123", Instant.now(), null, null, null));
            }
        };

        assertEquals("token", resource.create(new ExtensionSessionCreateRequest("Chrome", "chromium", "0.1.0")).token());
        assertEquals(1, resource.list().size());
    }

    @Test
    void revokeReturnsNotFoundWhenTheUserDoesNotOwnTheSession() {
        ExtensionSessionResource resource = new ExtensionSessionResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 5L;
        context.setUser(user);
        resource.currentUserContext = context;
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public boolean revokeSession(AppUser actor, Long sessionId) {
                assertEquals(5L, actor.id);
                assertEquals(9L, sessionId);
                return false;
            }
        };

        Response response = resource.revoke(9L);

        assertEquals(404, response.getStatus());
    }

    @Test
    void revokeReturnsNoContentForOwnedSessions() {
        ExtensionSessionResource resource = new ExtensionSessionResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 5L;
        context.setUser(user);
        resource.currentUserContext = context;
        resource.extensionSessionService = new ExtensionSessionService() {
            @Override
            public boolean revokeSession(AppUser actor, Long sessionId) {
                return true;
            }
        };
        final long[] published = { -1L };
        resource.pollingLiveService = new PollingLiveService() {
            @Override
            public void publishSessionRevoked(Long viewerId, SessionStreamKind streamKind, Long streamSessionId) {
                assertEquals(5L, viewerId);
                assertEquals(SessionStreamKind.EXTENSION, streamKind);
                published[0] = streamSessionId;
            }
        };

        Response response = resource.revoke(9L);

        assertEquals(204, response.getStatus());
        assertEquals(9L, published[0]);
    }
}
