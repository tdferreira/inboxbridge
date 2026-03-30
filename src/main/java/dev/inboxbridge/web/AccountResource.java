package dev.inboxbridge.web;

import java.time.Instant;
import dev.inboxbridge.dto.ChangePasswordRequest;
import dev.inboxbridge.dto.AccountSessionView;
import dev.inboxbridge.dto.AccountSessionsResponse;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.PasskeyView;
import dev.inboxbridge.dto.RemovePasswordRequest;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.StartPasskeyRegistrationRequest;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.AppUserService;
import dev.inboxbridge.service.PasskeyService;
import dev.inboxbridge.service.UserSessionService;
import dev.inboxbridge.service.UserGmailConfigService;
import dev.inboxbridge.service.UserMailDestinationConfigService;
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

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    @Inject
    UserSessionService userSessionService;

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
    @Consumes(MediaType.APPLICATION_JSON)
    public void removePassword(RemovePasswordRequest request) {
        try {
            appUserService.removePassword(currentUserContext.user(), request == null ? null : request.currentPassword());
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

    @DELETE
    @Path("/gmail-link")
    public UserGmailConfigService.GmailUnlinkResult unlinkGmailAccount() {
        return userGmailConfigService.unlinkForUser(currentUserContext.user().id);
    }

    @DELETE
    @Path("/destination-link")
    public UserMailDestinationConfigService.DestinationUnlinkResult unlinkDestinationAccount() {
        return userMailDestinationConfigService.unlinkForUser(currentUserContext.user().id);
    }

    @GET
    @Path("/sessions")
    public AccountSessionsResponse sessions() {
        Long currentSessionId = currentUserContext.session() == null ? null : currentUserContext.session().id;
        Instant now = Instant.now();
        return new AccountSessionsResponse(
                userSessionService.listRecentSessions(currentUserContext.user().id, 5).stream()
                        .map(session -> toSessionView(session, currentSessionId, now))
                        .toList(),
                userSessionService.listActiveSessions(currentUserContext.user().id).stream()
                        .map(session -> toSessionView(session, currentSessionId, now))
                        .toList());
    }

    @POST
    @Path("/sessions/{sessionId}/revoke")
    public void revokeSession(@PathParam("sessionId") Long sessionId) {
        try {
            userSessionService.invalidateSessionForUser(currentUserContext.user().id, sessionId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @POST
    @Path("/sessions/revoke-others")
    public void revokeOtherSessions() {
        userSessionService.invalidateOtherSessions(
                currentUserContext.user().id,
                currentUserContext.session() == null ? null : currentUserContext.session().id);
    }

    private AccountSessionView toSessionView(dev.inboxbridge.persistence.UserSession session, Long currentSessionId, Instant now) {
        return new AccountSessionView(
                session.id,
                session.clientIp,
                session.locationLabel,
                session.loginMethod == null ? null : session.loginMethod.name(),
                session.createdAt,
                session.lastSeenAt,
                session.expiresAt,
                currentSessionId != null && currentSessionId.equals(session.id),
                session.revokedAt == null && now.isBefore(session.expiresAt));
    }
}
