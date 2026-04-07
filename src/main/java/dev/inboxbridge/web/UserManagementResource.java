package dev.inboxbridge.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import dev.inboxbridge.dto.AdminUserConfigurationResponse;
import dev.inboxbridge.dto.AdminResetPasswordRequest;
import dev.inboxbridge.dto.CreateUserRequest;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.SystemOAuthAppSettingsView;
import dev.inboxbridge.dto.UpdateApplicationModeRequest;
import dev.inboxbridge.dto.UpdateUserRequest;
import dev.inboxbridge.dto.UserSummaryResponse;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.ApplicationModeService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.polling.PollingStatsService;
import dev.inboxbridge.service.UserEmailAccountService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;
import dev.inboxbridge.service.UserMailDestinationConfigService;
import dev.inboxbridge.service.UserPollingSettingsService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RequireAdmin
/**
 * Admin-only endpoints for creating users, approving or suspending them,
 * granting or revoking admin rights, resetting credentials, and inspecting
 * non-sensitive summaries of user-owned InboxBridge configuration.
 */
public class UserManagementResource {

    private static final String TIMEZONE_HEADER = "X-InboxBridge-Timezone";

    @Inject
    AppUserService appUserService;

    @Inject
    ApplicationModeService applicationModeService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    @Inject
    UserEmailAccountService userEmailAccountService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    @Inject
    PollingStatsService pollingStatsService;

    @Inject
    PasskeyService passkeyService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @GET
    public List<UserSummaryResponse> listUsers() {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            return appUserService.listUsers();
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public UserSummaryResponse createUser(CreateUserRequest request) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            return appUserService.toSummary(appUserService.createUser(request));
        });
    }

    @PUT
    @Path("/mode")
    @Consumes(MediaType.APPLICATION_JSON)
    public SystemOAuthAppSettingsView updateMode(UpdateApplicationModeRequest request) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.setMultiUserEnabled(currentUserContext.user(), request.multiUserEnabled());
            return systemOAuthAppSettingsService.view();
        });
    }

    @PUT
    @Path("/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserSummaryResponse updateUser(@PathParam("userId") Long userId, UpdateUserRequest request) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            return appUserService.toSummary(appUserService.updateUser(currentUserContext.user(), userId, request));
        });
    }

    @POST
    @Path("/{userId}/password-reset")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserSummaryResponse resetPassword(@PathParam("userId") Long userId, AdminResetPasswordRequest request) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            return appUserService.toSummary(appUserService.adminResetPassword(currentUserContext.user(), userId, request));
        });
    }

    @DELETE
    @Path("/{userId}")
    public java.util.Map<String, Object> deleteUser(@PathParam("userId") Long userId) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            appUserService.deleteUser(currentUserContext.user(), userId);
            return java.util.Map.of("deleted", Boolean.TRUE);
        });
    }

    @DELETE
    @Path("/{userId}/passkeys")
    public java.util.Map<String, Object> resetPasskeys(@PathParam("userId") Long userId) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            long deleted = passkeyService.resetForUser(userId);
            return java.util.Map.of("deleted", deleted);
        });
    }

    @GET
    @Path("/{userId}/configuration")
    public AdminUserConfigurationResponse configuration(
            @PathParam("userId") Long userId,
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            return appUserService.findById(userId)
                    .map(user -> new AdminUserConfigurationResponse(
                            appUserService.toSummary(user),
                            userMailDestinationConfigService.viewForUser(user.id),
                            userPollingSettingsService.viewForUser(user.id)
                                    .orElse(userPollingSettingsService.defaultView(user.id)),
                            pollingStatsService.userStats(user.id, resolveZoneId(timezone)),
                            userEmailAccountService.listForUser(user.id),
                            passkeyService.listForUser(user.id)))
                    .orElseThrow(() -> new BadRequestException("Unknown user id"));
        });
    }

    @GET
    @Path("/{userId}/polling-stats/range")
    public PollingTimelineBundleView pollingStatsRange(
            @PathParam("userId") Long userId,
            @jakarta.ws.rs.HeaderParam(TIMEZONE_HEADER) String timezone,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        return WebResourceSupport.badRequest(() -> {
            applicationModeService.requireMultiUserMode();
            appUserService.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
            return pollingStatsService.userTimelineBundle(
                    userId,
                    parseInstant(from, true),
                    parseInstant(to, false),
                    resolveZoneId(timezone));
        });
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
