package dev.inboxbridge.web.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ExtensionLastRunSummaryView;
import dev.inboxbridge.dto.ExtensionPollStateView;
import dev.inboxbridge.dto.ExtensionStatusView;
import dev.inboxbridge.dto.ExtensionSummaryView;
import dev.inboxbridge.dto.ExtensionUserView;
import dev.inboxbridge.dto.LiveEventView;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.extension.ExtensionStatusService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.polling.PollingService;

class ExtensionStatusResourceTest {

    @Test
    void pollReturnsBusyPayloadWhenPollingServiceReportsPollBusy() {
        ExtensionStatusResource resource = new ExtensionStatusResource();
        CurrentUserContext currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";
        currentUserContext.setUser(user);
        resource.currentUserContext = currentUserContext;
        resource.pollingService = new PollingService() {
            @Override
            public PollRunResult runPollForUser(AppUser actor, String trigger) {
                PollRunResult result = new PollRunResult();
                result.addError(new PollRunError("poll_busy", null, "busy", null));
                return result;
            }
        };

        var response = resource.poll();

        assertFalse(response.accepted());
        assertFalse(response.started());
        assertEquals("poll_busy", response.reason());
    }

    @Test
    void statusDelegatesToExtensionStatusServiceForAuthenticatedUser() {
        ExtensionStatusResource resource = new ExtensionStatusResource();
        CurrentUserContext currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 9L;
        user.username = "alice";
        currentUserContext.setUser(user);
        resource.currentUserContext = currentUserContext;
        resource.extensionStatusService = new ExtensionStatusService() {
            @Override
            public ExtensionStatusView statusForUser(AppUser actor) {
                return new ExtensionStatusView(
                        new ExtensionUserView(actor.username, actor.username, "fr", "LIGHT"),
                        new ExtensionPollStateView(false, "IDLE", true, null, null, null),
                        new ExtensionSummaryView(1, 1, 0, null, new ExtensionLastRunSummaryView(0, 0, 0, 0)),
                        List.of());
            }
        };

        var status = resource.status();

        assertEquals("alice", status.user().username());
        assertEquals("fr", status.user().language());
        assertEquals(1, status.summary().sourceCount());
        assertTrue(status.poll().canRun());
    }

    @Test
    void eventsDelegateToPollingLiveServiceForTheCurrentExtensionSession() {
        ExtensionStatusResource resource = new ExtensionStatusResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 9L;
        user.username = "alice";
        ExtensionSession extensionSession = new ExtensionSession();
        extensionSession.id = 41L;
        context.setUser(user);
        context.setExtensionSession(extensionSession);
        resource.currentUserContext = context;

        AtomicReference<PollingLiveService.SessionStreamKind> streamKind = new AtomicReference<>();
        AtomicReference<Long> sessionId = new AtomicReference<>();
        resource.pollingLiveService = new PollingLiveService() {
            @Override
            public io.smallrye.mutiny.Multi<LiveEventView> subscribe(AppUser viewer, SessionStreamKind kind, Long streamSessionId) {
                assertEquals(user, viewer);
                streamKind.set(kind);
                sessionId.set(streamSessionId);
                return io.smallrye.mutiny.Multi.createFrom().item(
                        new LiveEventView("keepalive", java.time.Instant.now(), null, null, null));
            }
        };

        LiveEventView event = resource.events().collect().first().await().indefinitely();

        assertEquals("keepalive", event.type());
        assertEquals(PollingLiveService.SessionStreamKind.EXTENSION, streamKind.get());
        assertEquals(41L, sessionId.get());
    }
}
