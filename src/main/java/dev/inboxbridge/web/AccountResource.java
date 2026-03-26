package dev.inboxbridge.web;

import dev.inboxbridge.dto.ChangePasswordRequest;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.PasskeyView;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.StartPasskeyRegistrationRequest;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.PasskeyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/account")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
/**
 * Own-account security endpoints for password changes and passkey lifecycle.
 */
public class AccountResource {

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AppUserService appUserService;

    @Inject
    PasskeyService passkeyService;

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

    @DELETE
    @Path("/password")
    public void removePassword() {
        try {
            appUserService.removePassword(currentUserContext.user());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/passkeys")
    public List<PasskeyView> passkeys() {
        return passkeyService.listForUser(currentUserContext.user().id);
    }

    @POST
    @Path("/passkeys/options")
    @Consumes(MediaType.APPLICATION_JSON)
    public StartPasskeyCeremonyResponse startPasskeyRegistration(StartPasskeyRegistrationRequest request) {
        try {
            return passkeyService.startRegistration(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/passkeys/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public PasskeyView finishPasskeyRegistration(FinishPasskeyCeremonyRequest request) {
        try {
            return passkeyService.finishRegistration(currentUserContext.user(), request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/passkeys/{passkeyId}")
    public void deletePasskey(@PathParam("passkeyId") Long passkeyId) {
        try {
            passkeyService.deleteForUser(currentUserContext.user(), passkeyId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }
}
