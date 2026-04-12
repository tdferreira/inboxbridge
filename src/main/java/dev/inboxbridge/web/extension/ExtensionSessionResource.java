package dev.inboxbridge.web.extension;

import java.util.List;

import dev.inboxbridge.dto.ExtensionSessionCreateRequest;
import dev.inboxbridge.dto.ExtensionSessionCreateView;
import dev.inboxbridge.dto.ExtensionSessionView;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.polling.PollingLiveService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Lets authenticated InboxBridge users manage their browser-extension bearer
 * sessions from the normal app session.
 */
@Path("/api/extension/sessions")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
public class ExtensionSessionResource {

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    ExtensionSessionService extensionSessionService;

    @Inject
    PollingLiveService pollingLiveService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public ExtensionSessionCreateView create(ExtensionSessionCreateRequest request) {
        return extensionSessionService.createSession(currentUserContext.user(), request);
    }

    @GET
    public List<ExtensionSessionView> list() {
        return extensionSessionService.listSessions(currentUserContext.user());
    }

    @DELETE
    @Path("/{id}")
    public Response revoke(@PathParam("id") Long id) {
        boolean revoked = extensionSessionService.revokeSession(currentUserContext.user(), id);
        if (revoked) {
            pollingLiveService.publishSessionRevoked(
                    currentUserContext.user().id,
                    PollingLiveService.SessionStreamKind.EXTENSION,
                    id);
        }
        return revoked ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
