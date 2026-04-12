package dev.inboxbridge.web.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.RemoteControlView;
import dev.inboxbridge.dto.RemoteSessionUserResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.auth.AuthClientAddressService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.remote.RemoteControlService;
import io.smallrye.common.annotation.Blocking;

class RemoteControlResourceTest {

    @Test
    void controlReturnsRemoteDashboardView() {
        RemoteControlResource resource = new RemoteControlResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 12L;
        user.username = "carol";
        user.role = AppUser.Role.USER;
        context.setUser(user);
        resource.currentUserContext = context;
        resource.remoteControlService = new FakeRemoteControlService();
        resource.authClientAddressService = new AuthClientAddressService();
        resource.httpHeaders = new RemoteAuthResourceTest.StaticHttpHeaders("203.0.113.22");

        RemoteControlView response = resource.control();

        assertEquals("carol", response.session().username());
        assertEquals(1, response.sources().size());
        assertEquals(true, response.setupRequired());
    }

    @Test
    void runSourcePollDelegatesWithSourceId() {
        RemoteControlResource resource = new RemoteControlResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 12L;
        user.username = "carol";
        user.role = AppUser.Role.USER;
        context.setUser(user);
        FakeRemoteControlService service = new FakeRemoteControlService();
        resource.currentUserContext = context;
        resource.remoteControlService = service;
        resource.authClientAddressService = new AuthClientAddressService();
        resource.httpHeaders = new RemoteAuthResourceTest.StaticHttpHeaders("203.0.113.22");

        PollRunResult result = resource.runSourcePoll("source-1");

        assertEquals("source-1", service.lastSourceId);
        assertEquals(0, result.getErrors().size());
    }

    @Test
    void livePollEndpointsDelegateToPollingLiveService() {
        RemoteControlResource resource = new RemoteControlResource();
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 12L;
        user.username = "carol";
        user.role = AppUser.Role.USER;
        context.setUser(user);
        TrackingPollingLiveService liveService = new TrackingPollingLiveService();
        resource.currentUserContext = context;
        resource.pollingLiveService = liveService;

        assertEquals("RUNNING", resource.livePoll().state());
        assertEquals("RUNNING", resource.pauseLivePoll().state());
        assertEquals("RUNNING", resource.resumeLivePoll().state());
        assertEquals("RUNNING", resource.stopLivePoll().state());
        assertEquals("RUNNING", resource.moveSourceNext("source-1").state());
        assertEquals("RUNNING", resource.retrySource("source-1").state());
        assertEquals(
                java.util.List.of(
                        "pause:carol",
                        "resume:carol",
                        "stop:carol",
                        "move:carol:source-1",
                        "retry:carol:source-1"),
                liveService.actions);
    }

    @Test
    void pollEventsIsBlocking() throws NoSuchMethodException {
        assertTrue(RemoteControlResource.class.getMethod("pollEvents").isAnnotationPresent(Blocking.class));
    }

    private static final class FakeRemoteControlService extends RemoteControlService {
        private String lastSourceId;

        @Override
        public RemoteControlView viewFor(AppUser actor) {
            return new RemoteControlView(
                    new RemoteSessionUserResponse(actor.id, 701L, actor.username, actor.role.name(), true, false, true, false, "pt-PT", "DARK", "DMY_24", "MANUAL", "Europe/Lisbon"),
                    java.util.List.of(new dev.inboxbridge.dto.RemoteSourceView(
                            "source-1", "USER", actor.id, actor.username, true, true, "5m", 50,
                            "IMAP", "imap.example.com", 993, "carol@example.com", "INBOX", "", 0, null, null, null)),
                    false,
                    false,
                    true,
                    "PT1M",
                    60);
        }

        @Override
        public PollRunResult runSourcePoll(AppUser actor, String sourceId, String actorKey) {
            lastSourceId = sourceId;
            PollRunResult result = new PollRunResult();
            result.finish();
            return result;
        }
    }

    private static final class TrackingPollingLiveService extends PollingLiveService {
        private final java.util.List<String> actions = new java.util.ArrayList<>();
        private final PollLiveView view = new PollLiveView(
                true,
                "run-1",
                "RUNNING",
                "remote-ui",
                "carol",
                true,
                "source-1",
                java.time.Instant.parse("2026-04-01T10:00:00Z"),
                java.time.Instant.parse("2026-04-01T10:00:05Z"),
                java.util.List.of());

        @Override
        public PollLiveView snapshotFor(AppUser viewer) {
            return view;
        }

        @Override
        public boolean requestPause(AppUser actor) {
            actions.add("pause:" + actor.username);
            return true;
        }

        @Override
        public boolean requestResume(AppUser actor) {
            actions.add("resume:" + actor.username);
            return true;
        }

        @Override
        public boolean requestStop(AppUser actor) {
            actions.add("stop:" + actor.username);
            return true;
        }

        @Override
        public boolean moveSourceToFront(AppUser actor, String sourceId) {
            actions.add("move:" + actor.username + ":" + sourceId);
            return true;
        }

        @Override
        public boolean retrySource(AppUser actor, String sourceId) {
            actions.add("retry:" + actor.username + ":" + sourceId);
            return true;
        }
    }
}
