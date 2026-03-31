package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.RemoteControlView;
import dev.inboxbridge.dto.RemoteSessionUserResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AuthClientAddressService;
import dev.inboxbridge.service.RemoteControlService;

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

    private static final class FakeRemoteControlService extends RemoteControlService {
        private String lastSourceId;

        @Override
        public RemoteControlView viewFor(AppUser actor) {
            return new RemoteControlView(
                    new RemoteSessionUserResponse(actor.id, actor.username, actor.role.name(), true, false),
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
}
