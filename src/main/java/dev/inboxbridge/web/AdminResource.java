package dev.inboxbridge.web;

import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.SystemOAuthAppSettingsView;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSystemOAuthAppSettingsRequest;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AdminDashboardService;
import dev.inboxbridge.service.PollingSettingsService;
import dev.inboxbridge.service.PollingStatsService;
import dev.inboxbridge.service.PollingService;
import dev.inboxbridge.service.RuntimeBridgeService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.SystemOAuthAppSettingsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RequireAdmin
public class AdminResource {

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AdminDashboardService adminDashboardService;

    @Inject
    PollingService pollingService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    PollingStatsService pollingStatsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    RuntimeBridgeService runtimeBridgeService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @GET
    @Path("/dashboard")
    public AdminDashboardResponse dashboard() {
        return adminDashboardService.dashboard();
    }

    @GET
    @Path("/oauth-app-settings")
    public SystemOAuthAppSettingsView oauthAppSettings() {
        return systemOAuthAppSettingsService.view();
    }

    @PUT
    @Path("/oauth-app-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public SystemOAuthAppSettingsView updateOauthAppSettings(UpdateSystemOAuthAppSettingsRequest request) {
        try {
            return systemOAuthAppSettingsService.update(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/poll/run")
    public PollRunResult runPoll() {
        return pollingService.runPollForAllUsers(currentUserContext.user(), "admin-ui");
    }

    @GET
    @Path("/polling-settings")
    public AdminPollingSettingsView pollingSettings() {
        return pollingSettingsService.view();
    }

    @GET
    @Path("/polling-stats/range")
    public PollingTimelineBundleView pollingStatsRange(
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        try {
            return pollingStatsService.globalTimelineBundle(
                    parseInstant(from, true),
                    parseInstant(to, false));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @PUT
    @Path("/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public AdminPollingSettingsView updatePollingSettings(UpdateAdminPollingSettingsRequest request) {
        try {
            return pollingSettingsService.update(request);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/bridges/{bridgeId}/polling-settings")
    public SourcePollingSettingsView bridgePollingSettings(@jakarta.ws.rs.PathParam("bridgeId") String bridgeId) {
        try {
            return sourcePollingSettingsService.viewForSystemSource(bridgeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/bridges/{bridgeId}/polling-stats")
    public SourcePollingStatsView bridgePollingStats(@jakarta.ws.rs.PathParam("bridgeId") String bridgeId) {
        try {
            return pollingStatsService.sourceStats(
                    runtimeBridgeService.findSystemBridge(bridgeId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/bridges/{bridgeId}/polling-stats/range")
    public PollingTimelineBundleView bridgePollingStatsRange(
            @jakarta.ws.rs.PathParam("bridgeId") String bridgeId,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        try {
            return pollingStatsService.sourceTimelineBundle(
                    runtimeBridgeService.findSystemBridge(bridgeId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                    parseInstant(from, true),
                    parseInstant(to, false));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @PUT
    @Path("/bridges/{bridgeId}/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public SourcePollingSettingsView updateBridgePollingSettings(
            @jakarta.ws.rs.PathParam("bridgeId") String bridgeId,
            UpdateSourcePollingSettingsRequest request) {
        try {
            return sourcePollingSettingsService.updateForSystemSource(bridgeId, request);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/bridges/{bridgeId}/poll/run")
    public PollRunResult runBridgePoll(@jakarta.ws.rs.PathParam("bridgeId") String bridgeId) {
        try {
            return pollingService.runPollForSource(
                    runtimeBridgeService.findSystemBridge(bridgeId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                    "admin-fetcher",
                    currentUserContext.user().role + ":" + currentUserContext.user().id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    private Instant parseInstant(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("The \"from\" date-time is required");
            }
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO-8601 date-time: " + value, e);
        }
    }
}
