package dev.inboxbridge.web;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.inboxbridge.dto.LiveEventView;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollStatusView;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.PollingLiveService;
import dev.inboxbridge.service.PollingService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/poll")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
public class PollingResource {

    @Inject
    PollingService pollingService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    PollingLiveService pollingLiveService;

    @POST
    @Path("/run")
    public PollRunResult runNow() {
        return pollingService.runPollForUser(currentUserContext.user(), "manual-api");
    }

    @GET
    @Path("/status")
    public PollStatusView status() {
        return pollingService.status();
    }

    @GET
    @Path("/live")
    public PollLiveView live() {
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @GET
    @Path("/events")
    @Blocking
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<LiveEventView> events() {
        return pollingLiveService.subscribe(
                currentUserContext.user(),
                PollingLiveService.SessionStreamKind.BROWSER,
                currentUserContext.session() == null ? null : currentUserContext.session().id);
    }

    @POST
    @Path("/live/pause")
    public PollLiveView pause() {
        pollingLiveService.requestPause(currentUserContext.user());
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/live/resume")
    public PollLiveView resume() {
        pollingLiveService.requestResume(currentUserContext.user());
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/live/stop")
    public PollLiveView stop() {
        pollingLiveService.requestStop(currentUserContext.user());
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/live/sources/{emailAccountId}/move-next")
    public PollLiveView moveNext(@jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId) {
        pollingLiveService.moveSourceToFront(currentUserContext.user(), emailAccountId);
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/live/sources/{emailAccountId}/retry")
    public PollLiveView retry(@jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId) {
        pollingLiveService.retrySource(currentUserContext.user(), emailAccountId);
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }
}
