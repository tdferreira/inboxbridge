package dev.inboxbridge.web.extension;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.inboxbridge.dto.ExtensionPollTriggerResultView;
import dev.inboxbridge.dto.ExtensionStatusView;
import dev.inboxbridge.dto.LiveEventView;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireExtensionAuth;
import dev.inboxbridge.service.extension.ExtensionStatusService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.polling.PollingService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Exposes the narrow extension-authenticated status and poll endpoints used by
 * the browser extension popup and badge updater.
 */
@Path("/api/extension")
@Produces(MediaType.APPLICATION_JSON)
@RequireExtensionAuth
public class ExtensionStatusResource {

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    ExtensionStatusService extensionStatusService;

    @Inject
    PollingService pollingService;

    @Inject
    PollingLiveService pollingLiveService;

    @GET
    @Path("/status")
    public ExtensionStatusView status() {
        return extensionStatusService.statusForUser(currentUserContext.user());
    }

    @GET
    @Path("/events")
    @Blocking
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<LiveEventView> events() {
        return pollingLiveService.subscribe(
                currentUserContext.user(),
                PollingLiveService.SessionStreamKind.EXTENSION,
                currentUserContext.extensionSession() == null ? null : currentUserContext.extensionSession().id);
    }

    @POST
    @Path("/poll")
    public ExtensionPollTriggerResultView poll() {
        PollRunResult result = pollingService.runPollForUser(currentUserContext.user(), "extension-user");
        boolean busy = result.getErrorDetails().stream()
                .map(PollRunError::code)
                .anyMatch("poll_busy"::equals);
        if (busy) {
            return new ExtensionPollTriggerResultView(
                    false,
                    false,
                    "poll_busy",
                    "Another polling run is already active.");
        }
        return new ExtensionPollTriggerResultView(
                true,
                result.getStartedAt() != null,
                null,
                "User poll started.");
    }
}
