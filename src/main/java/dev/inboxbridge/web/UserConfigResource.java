package dev.inboxbridge.web;

import java.util.List;

import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.dto.UpdateUserGmailConfigRequest;
import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.UserBridgeView;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.dto.UserUiPreferenceView;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.UserBridgeService;
import dev.inboxbridge.service.UserGmailConfigService;
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

    @DELETE
    @Path("/bridges/{bridgeId}")
    public void deleteBridge(@PathParam("bridgeId") String bridgeId) {
        try {
            userBridgeService.delete(currentUserContext.user(), bridgeId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }
}
