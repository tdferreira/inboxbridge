package dev.inboxbridge.web;

import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.service.AdminDashboardService;
import dev.inboxbridge.service.PollingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RequireAdmin
public class AdminResource {

    @Inject
    AdminDashboardService adminDashboardService;

    @Inject
    PollingService pollingService;

    @GET
    @Path("/dashboard")
    public AdminDashboardResponse dashboard() {
        return adminDashboardService.dashboard();
    }

    @POST
    @Path("/poll/run")
    public PollRunResult runPoll() {
        return pollingService.runPoll("admin-ui");
    }
}
