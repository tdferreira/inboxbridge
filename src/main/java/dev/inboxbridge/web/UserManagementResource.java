package dev.inboxbridge.web;

import java.util.List;

import dev.inboxbridge.dto.AdminUserConfigurationResponse;
import dev.inboxbridge.dto.CreateUserRequest;
import dev.inboxbridge.dto.UpdateUserRequest;
import dev.inboxbridge.dto.UserSummaryResponse;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.UserBridgeService;
import dev.inboxbridge.service.UserGmailConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RequireAdmin
/**
 * Admin-only endpoints for creating users, approving or suspending them,
 * granting or revoking admin rights, and inspecting non-sensitive summaries of
 * user-owned InboxBridge configuration.
 */
public class UserManagementResource {

    @Inject
    AppUserService appUserService;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    UserBridgeService userBridgeService;

    @GET
    public List<UserSummaryResponse> listUsers() {
        return appUserService.listUsers();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public UserSummaryResponse createUser(CreateUserRequest request) {
        try {
            return appUserService.toSummary(appUserService.createUser(request));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @PUT
    @Path("/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserSummaryResponse updateUser(@PathParam("userId") Long userId, UpdateUserRequest request) {
        try {
            return appUserService.toSummary(appUserService.updateUser(userId, request));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/{userId}/configuration")
    public AdminUserConfigurationResponse configuration(@PathParam("userId") Long userId) {
        return appUserService.findById(userId)
                .map(user -> new AdminUserConfigurationResponse(
                        appUserService.toSummary(user),
                        userGmailConfigService.viewForUser(user.id)
                                .orElse(userGmailConfigService.defaultView(user.id)),
                        userBridgeService.listForUser(user.id)))
                .orElseThrow(() -> new BadRequestException("Unknown user id"));
    }
}
