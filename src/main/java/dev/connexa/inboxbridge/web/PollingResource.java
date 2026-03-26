package dev.connexa.inboxbridge.web;

import dev.connexa.inboxbridge.dto.PollRunResult;
import dev.connexa.inboxbridge.service.PollingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/poll")
@Produces(MediaType.APPLICATION_JSON)
public class PollingResource {

    @Inject
    PollingService pollingService;

    @POST
    @Path("/run")
    public PollRunResult runNow() {
        return pollingService.runPoll("manual-api");
    }
}
