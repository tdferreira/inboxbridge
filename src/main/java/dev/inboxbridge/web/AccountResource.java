package dev.inboxbridge.web;

import dev.inboxbridge.dto.ChangePasswordRequest;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.AppUserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/account")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
public class AccountResource {

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AppUserService appUserService;

    @POST
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public void changePassword(ChangePasswordRequest request) {
        try {
            appUserService.changePassword(
                    currentUserContext.user(),
                    request.currentPassword(),
                    request.newPassword(),
                    request.confirmNewPassword());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }
}
