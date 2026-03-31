package dev.inboxbridge.web;

import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.RemoteControlView;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireRemoteControl;
import dev.inboxbridge.service.AuthClientAddressService;
import dev.inboxbridge.service.RemoteControlService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/api/remote")
@Produces(MediaType.APPLICATION_JSON)
@RequireRemoteControl
public class RemoteControlResource {

    @Inject
    RemoteControlService remoteControlService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    AuthClientAddressService authClientAddressService;

    @Context
    HttpHeaders httpHeaders;

    @Context
    HttpServerRequest httpServerRequest;

    @GET
    @Path("/control")
    public RemoteControlView control() {
        return remoteControlService.viewFor(currentUserContext.user());
    }

    @POST
    @Path("/poll/run")
    public PollRunResult runUserPoll() {
        return remoteControlService.runUserPoll(currentUserContext.user(), actorKey("remote-user"));
    }

    @POST
    @Path("/poll/all-users/run")
    public PollRunResult runAllUsersPoll() {
        return remoteControlService.runAllUsersPoll(currentUserContext.user(), actorKey("remote-admin"));
    }

    @POST
    @Path("/sources/{sourceId}/poll/run")
    public PollRunResult runSourcePoll(@PathParam("sourceId") String sourceId) {
        try {
            return remoteControlService.runSourcePoll(currentUserContext.user(), sourceId, actorKey("remote-source:" + sourceId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    private String actorKey(String prefix) {
        String client = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
        return prefix + ":" + currentUserContext.user().id + ":" + client;
    }

    private String directRemoteAddress() {
        if (httpServerRequest == null || httpServerRequest.remoteAddress() == null) {
            return null;
        }
        return httpServerRequest.remoteAddress().hostAddress();
    }
}
