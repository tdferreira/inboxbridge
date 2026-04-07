package dev.inboxbridge.web;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.AuthSecuritySettingsView;
import dev.inboxbridge.dto.LiveEventView;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.SystemOAuthAppSettingsView;
import dev.inboxbridge.dto.UpdateAuthSecuritySettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSystemOAuthAppSettingsRequest;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.AdminDashboardService;
import dev.inboxbridge.service.AuthSecuritySettingsService;
import dev.inboxbridge.service.PollingSettingsService;
import dev.inboxbridge.service.polling.PollingStatsService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.RuntimeEmailAccountService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.SystemOAuthAppSettingsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Multi;
import io.smallrye.common.annotation.Blocking;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RequireAdmin
public class AdminResource {

    private static final String TIMEZONE_HEADER = "X-InboxBridge-Timezone";

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AdminDashboardService adminDashboardService;

    @Inject
    PollingService pollingService;

    @Inject
    PollingLiveService pollingLiveService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    PollingStatsService pollingStatsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    @GET
    @Path("/dashboard")
    public AdminDashboardResponse dashboard(@jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone) {
        return adminDashboardService.dashboard(resolveZoneId(timezone));
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
        return WebResourceSupport.badRequest(() -> systemOAuthAppSettingsService.update(request));
    }

    @GET
    @Path("/auth-security-settings")
    public AuthSecuritySettingsView authSecuritySettings() {
        return authSecuritySettingsService.view();
    }

    @PUT
    @Path("/auth-security-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthSecuritySettingsView updateAuthSecuritySettings(UpdateAuthSecuritySettingsRequest request) {
        return WebResourceSupport.badRequest(() -> authSecuritySettingsService.update(request));
    }

    @POST
    @Path("/poll/run")
    public PollRunResult runPoll() {
        return pollingService.runPollForAllUsers(currentUserContext.user(), "admin-ui");
    }

    @GET
    @Path("/poll/live")
    public PollLiveView livePoll() {
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @GET
    @Path("/poll/events")
    @Blocking
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<LiveEventView> pollEvents() {
        return pollingLiveService.subscribe(
                currentUserContext.user(),
                PollingLiveService.SessionStreamKind.BROWSER,
                currentUserContext.session() == null ? null : currentUserContext.session().id);
    }

    @POST
    @Path("/poll/live/pause")
    public PollLiveView pauseLivePoll() {
        pollingLiveService.requestPause(currentUserContext.user());
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/poll/live/resume")
    public PollLiveView resumeLivePoll() {
        pollingLiveService.requestResume(currentUserContext.user());
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/poll/live/stop")
    public PollLiveView stopLivePoll() {
        pollingLiveService.requestStop(currentUserContext.user());
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/poll/live/sources/{emailAccountId}/move-next")
    public PollLiveView moveSourceNext(@jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId) {
        pollingLiveService.moveSourceToFront(currentUserContext.user(), emailAccountId);
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @POST
    @Path("/poll/live/sources/{emailAccountId}/retry")
    public PollLiveView retrySource(@jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId) {
        pollingLiveService.retrySource(currentUserContext.user(), emailAccountId);
        return pollingLiveService.snapshotFor(currentUserContext.user());
    }

    @GET
    @Path("/polling-settings")
    public AdminPollingSettingsView pollingSettings() {
        return pollingSettingsService.view();
    }

    @GET
    @Path("/polling-stats/range")
    public PollingTimelineBundleView pollingStatsRange(
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        return WebResourceSupport.badRequest(() -> pollingStatsService.globalTimelineBundle(
                parseInstant(from, true),
                parseInstant(to, false),
                resolveZoneId(timezone)));
    }

    @PUT
    @Path("/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public AdminPollingSettingsView updatePollingSettings(UpdateAdminPollingSettingsRequest request) {
        return WebResourceSupport.badRequest(() -> pollingSettingsService.update(request));
    }

    @GET
    @Path("/email-accounts/{emailAccountId}/polling-settings")
    public SourcePollingSettingsView emailAccountPollingSettings(@jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId) {
        return WebResourceSupport.badRequest(() ->
                sourcePollingSettingsService.viewForSystemSource(emailAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")));
    }

    @GET
    @Path("/email-accounts/{emailAccountId}/polling-stats")
    public SourcePollingStatsView emailAccountPollingStats(
            @jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId,
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone) {
        return WebResourceSupport.badRequest(() ->
                pollingStatsService.sourceStats(
                        runtimeEmailAccountService.findSystemBridge(emailAccountId)
                                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                        resolveZoneId(timezone)));
    }

    @GET
        @Path("/email-accounts/{emailAccountId}/polling-stats/range")
        public PollingTimelineBundleView emailAccountPollingStatsRange(
            @jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId,
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        return WebResourceSupport.badRequest(() ->
                pollingStatsService.sourceTimelineBundle(
                        runtimeEmailAccountService.findSystemBridge(emailAccountId)
                                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                        parseInstant(from, true),
                        parseInstant(to, false),
                        resolveZoneId(timezone)));
    }

    @PUT
    @Path("/email-accounts/{emailAccountId}/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public SourcePollingSettingsView updateEmailAccountPollingSettings(
            @jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId,
            UpdateSourcePollingSettingsRequest request) {
        return WebResourceSupport.badRequest(() -> sourcePollingSettingsService.updateForSystemSource(emailAccountId, request));
    }

    @POST
    @Path("/email-accounts/{emailAccountId}/poll/run")
    public PollRunResult runEmailAccountPoll(@jakarta.ws.rs.PathParam("emailAccountId") String emailAccountId) {
        return WebResourceSupport.badRequest(() ->
                pollingService.runPollForSource(
                        runtimeEmailAccountService.findSystemBridge(emailAccountId)
                                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                        "admin-fetcher",
                        currentUserContext.user(),
                        currentUserContext.user().role + ":" + currentUserContext.user().id));
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

    private ZoneId resolveZoneId(String value) {
        if (value == null || value.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(value);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }
}
