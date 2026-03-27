package dev.inboxbridge.web;

import java.util.List;

import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.dto.UpdateUserGmailConfigRequest;
import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.BridgeConnectionTestResult;
import dev.inboxbridge.dto.UserBridgeView;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.dto.UserPollingSettingsView;
import dev.inboxbridge.dto.UserUiPreferenceView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.PollingService;
import dev.inboxbridge.service.RuntimeBridgeService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.UserBridgeService;
import dev.inboxbridge.service.UserGmailConfigService;
import dev.inboxbridge.service.PollingStatsService;
import dev.inboxbridge.service.UserPollingSettingsService;
import dev.inboxbridge.service.UserUiPreferenceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/app")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
public class UserConfigResource {

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    UserBridgeService userBridgeService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    @Inject
    PollingStatsService pollingStatsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    RuntimeBridgeService runtimeBridgeService;

    @Inject
    PollingService pollingService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    @GET
    @Path("/gmail-config")
    public UserGmailConfigView gmailConfig() {
        return userGmailConfigService.viewForUser(currentUserContext.user().id)
                .orElse(userGmailConfigService.defaultView(currentUserContext.user().id));
    }

    @PUT
    @Path("/gmail-config")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserGmailConfigView updateGmailConfig(UpdateUserGmailConfigRequest request) {
        try {
            return userGmailConfigService.update(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/bridges")
    public List<UserBridgeView> bridges() {
        return userBridgeService.listForUser(currentUserContext.user().id);
    }

    @GET
    @Path("/polling-settings")
    public UserPollingSettingsView pollingSettings() {
        return userPollingSettingsService.viewForUser(currentUserContext.user().id)
                .orElse(userPollingSettingsService.defaultView(currentUserContext.user().id));
    }

    @GET
    @Path("/polling-stats")
    public UserPollingStatsView pollingStats() {
        return pollingStatsService.userStats(currentUserContext.user().id);
    }

    @PUT
    @Path("/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserPollingSettingsView updatePollingSettings(UpdateUserPollingSettingsRequest request) {
        try {
            return userPollingSettingsService.update(currentUserContext.user(), request);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/ui-preferences")
    public UserUiPreferenceView uiPreferences() {
        return userUiPreferenceService.viewForUser(currentUserContext.user().id)
                .orElse(userUiPreferenceService.defaultView());
    }

    @PUT
    @Path("/ui-preferences")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserUiPreferenceView updateUiPreferences(UpdateUserUiPreferenceRequest request) {
        return userUiPreferenceService.update(currentUserContext.user(), request);
    }

    @PUT
    @Path("/bridges")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserBridgeView upsertBridge(UpdateUserBridgeRequest request) {
        try {
            return userBridgeService.upsert(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/bridges/test-connection")
    @Consumes(MediaType.APPLICATION_JSON)
    public BridgeConnectionTestResult testBridgeConnection(UpdateUserBridgeRequest request) {
        try {
            return userBridgeService.testConnection(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/bridges/{bridgeId}")
    public void deleteBridge(@PathParam("bridgeId") String bridgeId) {
        try {
            userBridgeService.delete(currentUserContext.user(), bridgeId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/bridges/{bridgeId}/polling-settings")
    public SourcePollingSettingsView bridgePollingSettings(@PathParam("bridgeId") String bridgeId) {
        try {
            return sourcePollingSettingsService.viewForSource(currentUserContext.user(), bridgeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @PUT
    @Path("/bridges/{bridgeId}/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public SourcePollingSettingsView updateBridgePollingSettings(
            @PathParam("bridgeId") String bridgeId,
            UpdateSourcePollingSettingsRequest request) {
        try {
            return sourcePollingSettingsService.updateForSource(currentUserContext.user(), bridgeId, request);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/bridges/{bridgeId}/poll/run")
    public PollRunResult runBridgePoll(@PathParam("bridgeId") String bridgeId) {
        try {
            return pollingService.runPollForSource(
                    runtimeBridgeService.findAccessibleForUser(currentUserContext.user(), bridgeId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                    "app-fetcher");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }
}
