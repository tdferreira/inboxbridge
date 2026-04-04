package dev.inboxbridge.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import dev.inboxbridge.dto.UpdateUserEmailAccountRequest;
import dev.inboxbridge.dto.UpdateUserMailDestinationRequest;
import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.DestinationMailboxFolderOptionsView;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.UserEmailAccountView;
import dev.inboxbridge.dto.UserMailDestinationView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.dto.UserPollingSettingsView;
import dev.inboxbridge.dto.UserUiPreferenceView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.PollingService;
import dev.inboxbridge.service.RuntimeEmailAccountService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.UserEmailAccountService;
import dev.inboxbridge.service.UserMailDestinationConfigService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/app")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
public class UserConfigResource {

    private static final String TIMEZONE_HEADER = "X-InboxBridge-Timezone";

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    @Inject
    UserEmailAccountService userEmailAccountService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    @Inject
    PollingStatsService pollingStatsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    PollingService pollingService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    @GET
    @Path("/destination-config")
    public UserMailDestinationView destinationConfig() {
        return userMailDestinationConfigService.viewForUser(currentUserContext.user().id);
    }

    @PUT
    @Path("/destination-config")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserMailDestinationView updateDestinationConfig(UpdateUserMailDestinationRequest request) {
        try {
            return userMailDestinationConfigService.update(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/destination-config/folders")
    public DestinationMailboxFolderOptionsView destinationFolders() {
        try {
            return userMailDestinationConfigService.listFoldersForUser(currentUserContext.user().id, currentUserContext.user().username);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/destination-config/test-connection")
    @Consumes(MediaType.APPLICATION_JSON)
    public EmailAccountConnectionTestResult testDestinationConnection(UpdateUserMailDestinationRequest request) {
        try {
            return userMailDestinationConfigService.testConnectionForUser(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/email-accounts")
    public List<UserEmailAccountView> emailAccounts() {
        return userEmailAccountService.listForUser(currentUserContext.user().id);
    }

    @GET
    @Path("/polling-settings")
    public UserPollingSettingsView pollingSettings() {
        return userPollingSettingsService.viewForUser(currentUserContext.user().id)
                .orElse(userPollingSettingsService.defaultView(currentUserContext.user().id));
    }

    @GET
    @Path("/polling-stats")
    public UserPollingStatsView pollingStats(@jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone) {
        return pollingStatsService.userStats(currentUserContext.user().id, resolveZoneId(timezone));
    }

    @GET
    @Path("/polling-stats/range")
    public PollingTimelineBundleView pollingStatsRange(
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        try {
            return pollingStatsService.userTimelineBundle(
                    currentUserContext.user().id,
                    parseInstant(from, true),
                    parseInstant(to, false),
                    resolveZoneId(timezone));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/email-accounts/{emailAccountId}/polling-stats")
    public SourcePollingStatsView emailAccountPollingStats(
            @PathParam("emailAccountId") String emailAccountId,
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone) {
        try {
            return pollingStatsService.sourceStats(
                    runtimeEmailAccountService.findAccessibleForUser(currentUserContext.user(), emailAccountId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                    resolveZoneId(timezone));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
        @Path("/email-accounts/{emailAccountId}/polling-stats/range")
        public PollingTimelineBundleView emailAccountPollingStatsRange(
            @PathParam("emailAccountId") String emailAccountId,
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        try {
            return pollingStatsService.sourceTimelineBundle(
                runtimeEmailAccountService.findAccessibleForUser(currentUserContext.user(), emailAccountId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                    parseInstant(from, true),
                    parseInstant(to, false),
                    resolveZoneId(timezone));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
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
    @Path("/email-accounts")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserEmailAccountView upsertEmailAccount(UpdateUserEmailAccountRequest request) {
        try {
            return userEmailAccountService.upsert(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/email-accounts/folders")
    @Consumes(MediaType.APPLICATION_JSON)
    public DestinationMailboxFolderOptionsView emailAccountFolders(UpdateUserEmailAccountRequest request) {
        try {
            return userEmailAccountService.listFolders(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/email-accounts/test-connection")
    @Consumes(MediaType.APPLICATION_JSON)
    public EmailAccountConnectionTestResult testEmailAccountConnection(UpdateUserEmailAccountRequest request) {
        try {
            return userEmailAccountService.testConnection(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/email-accounts/{emailAccountId}")
    public void deleteEmailAccount(@PathParam("emailAccountId") String emailAccountId) {
        try {
            userEmailAccountService.delete(currentUserContext.user(), emailAccountId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/email-accounts/{emailAccountId}/polling-settings")
    public SourcePollingSettingsView emailAccountPollingSettings(@PathParam("emailAccountId") String emailAccountId) {
        try {
            return sourcePollingSettingsService.viewForSource(currentUserContext.user(), emailAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @PUT
    @Path("/email-accounts/{emailAccountId}/polling-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public SourcePollingSettingsView updateEmailAccountPollingSettings(
            @PathParam("emailAccountId") String emailAccountId,
            UpdateSourcePollingSettingsRequest request) {
        try {
            return sourcePollingSettingsService.updateForSource(currentUserContext.user(), emailAccountId, request);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/poll/run")
    public PollRunResult runUserPoll() {
        return pollingService.runPollForUser(currentUserContext.user(), "user-ui");
    }

    @POST
    @Path("/email-accounts/{emailAccountId}/poll/run")
    public PollRunResult runEmailAccountPoll(@PathParam("emailAccountId") String emailAccountId) {
        try {
            return pollingService.runPollForSource(
                    runtimeEmailAccountService.findAccessibleForUser(currentUserContext.user(), emailAccountId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id")),
                    "app-fetcher",
                    currentUserContext.user(),
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
