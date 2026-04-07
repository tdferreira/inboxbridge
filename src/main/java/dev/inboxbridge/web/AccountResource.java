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
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.GeoIpLocationService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.remote.RemoteSessionService;
import dev.inboxbridge.service.auth.SessionLocationAlertService;
import dev.inboxbridge.service.auth.SessionClientInfoService;
import dev.inboxbridge.service.auth.UserSessionService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
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

    @Inject
    RemoteSessionService remoteSessionService;

    @Inject
    GeoIpLocationService geoIpLocationService;

    @Inject
    SessionClientInfoService sessionClientInfoService;

    @Inject
    SessionLocationAlertService sessionLocationAlertService;

    @Inject
    PollingLiveService pollingLiveService;

    @POST
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public void changePassword(ChangePasswordRequest request) {
        WebResourceSupport.badRequest(() ->
            appUserService.changePassword(
                    currentUserContext.user(),
                    request.currentPassword(),
                    request.newPassword(),
                    request.confirmNewPassword()));
    }

    @DELETE
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public void removePassword(RemovePasswordRequest request) {
        WebResourceSupport.badRequest(() ->
                appUserService.removePassword(currentUserContext.user(), request == null ? null : request.currentPassword()));
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
        return WebResourceSupport.badRequest(() -> passkeyService.startRegistration(currentUserContext.user(), request));
    }

    @POST
    @Path("/passkeys/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public PasskeyView finishPasskeyRegistration(FinishPasskeyCeremonyRequest request) {
        return WebResourceSupport.badRequest(() -> passkeyService.finishRegistration(currentUserContext.user(), request));
    }

    @DELETE
    @Path("/passkeys/{passkeyId}")
    public void deletePasskey(@PathParam("passkeyId") Long passkeyId) {
        WebResourceSupport.badRequest(() -> passkeyService.deleteForUser(currentUserContext.user(), passkeyId));
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
        List<AccountSessionView> recentLogins = java.util.stream.Stream.concat(
                        userSessionService.listRecentSessions(currentUserContext.user().id, 5).stream()
                                .map(session -> toSessionView(session, currentSessionId, now)),
                        remoteSessionService.listRecentSessions(currentUserContext.user().id, 5).stream()
                                .map(session -> toRemoteSessionView(session, now)))
                .sorted(Comparator.comparing(AccountSessionView::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        recentLogins = applyUnusualLocationFlags(recentLogins);
        List<AccountSessionView> activeSessions = java.util.stream.Stream.concat(
                        userSessionService.listActiveSessions(currentUserContext.user().id).stream()
                                .map(session -> toSessionView(session, currentSessionId, now)),
                        remoteSessionService.listActiveSessions(currentUserContext.user().id).stream()
                                .map(session -> toRemoteSessionView(session, now)))
                .sorted(Comparator.comparing(AccountSessionView::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new AccountSessionsResponse(
                recentLogins,
                activeSessions,
                geoIpLocationService.isConfigured());
    }

    @POST
    @Path("/sessions/{sessionId}/revoke")
    public void revokeSession(@PathParam("sessionId") Long sessionId, @QueryParam("type") String type) {
        WebResourceSupport.badRequest(() -> {
            if ("REMOTE".equalsIgnoreCase(type)) {
                remoteSessionService.invalidateSessionForUser(currentUserContext.user().id, sessionId);
                pollingLiveService.publishSessionRevoked(currentUserContext.user().id, PollingLiveService.SessionStreamKind.REMOTE, sessionId);
            } else {
                userSessionService.invalidateSessionForUser(currentUserContext.user().id, sessionId);
                pollingLiveService.publishSessionRevoked(currentUserContext.user().id, PollingLiveService.SessionStreamKind.BROWSER, sessionId);
            }
        });
    }

    @POST
    @Path("/sessions/revoke-others")
    public void revokeOtherSessions() {
        Long currentSessionId = currentUserContext.session() == null ? null : currentUserContext.session().id;
        List<Long> browserSessionIds = userSessionService.listActiveSessions(currentUserContext.user().id).stream()
                .map(session -> session.id)
                .filter(sessionId -> currentSessionId == null || !currentSessionId.equals(sessionId))
                .toList();
        List<Long> remoteSessionIds = remoteSessionService.listActiveSessions(currentUserContext.user().id).stream()
                .map(session -> session.id)
                .toList();
        userSessionService.invalidateOtherSessions(
                currentUserContext.user().id,
                currentSessionId);
        remoteSessionService.invalidateOtherSessions(currentUserContext.user().id);
        browserSessionIds.forEach((sessionId) -> pollingLiveService.publishSessionRevoked(currentUserContext.user().id, PollingLiveService.SessionStreamKind.BROWSER, sessionId));
        remoteSessionIds.forEach((sessionId) -> pollingLiveService.publishSessionRevoked(currentUserContext.user().id, PollingLiveService.SessionStreamKind.REMOTE, sessionId));
    }

    private AccountSessionView toSessionView(dev.inboxbridge.persistence.UserSession session, Long currentSessionId, Instant now) {
        SessionClientInfoService.SessionClientInfo clientInfo = sessionClientInfoService.describe(session.userAgent);
        return new AccountSessionView(
                session.id,
                "BROWSER",
                clientInfo.browserLabel(),
                clientInfo.deviceLabel(),
                session.clientIp,
                session.locationLabel,
                false,
                session.deviceLocationLabel,
                session.deviceLatitude,
                session.deviceLongitude,
                session.deviceLocationCapturedAt,
                session.loginMethod == null ? null : session.loginMethod.name(),
                session.createdAt,
                session.lastSeenAt,
                session.expiresAt,
                currentSessionId != null && currentSessionId.equals(session.id),
                session.revokedAt == null && now.isBefore(session.expiresAt));
    }

    private AccountSessionView toRemoteSessionView(dev.inboxbridge.persistence.RemoteSession session, Instant now) {
        SessionClientInfoService.SessionClientInfo clientInfo = sessionClientInfoService.describe(session.userAgent);
        return new AccountSessionView(
                session.id,
                "REMOTE",
                clientInfo.browserLabel(),
                clientInfo.deviceLabel(),
                session.clientIp,
                session.locationLabel,
                false,
                session.deviceLocationLabel,
                session.deviceLatitude,
                session.deviceLongitude,
                session.deviceLocationCapturedAt,
                session.loginMethod == null ? null : session.loginMethod.name(),
                session.createdAt,
                session.lastSeenAt,
                session.expiresAt,
                false,
                session.revokedAt == null && now.isBefore(session.expiresAt));
    }

    private List<AccountSessionView> applyUnusualLocationFlags(List<AccountSessionView> sessions) {
        if (sessionLocationAlertService == null || sessions == null || sessions.isEmpty()) {
            return sessions;
        }
        java.util.Map<String, Boolean> unusualBySession = sessionLocationAlertService.assessSnapshots(
                sessions.stream()
                        .map(session -> new SessionLocationAlertService.SessionLocationSnapshot(
                                session.sessionType(),
                                session.id(),
                                session.createdAt(),
                                session.locationLabel()))
                        .toList());
        return sessions.stream()
                .map(session -> new AccountSessionView(
                        session.id(),
                        session.sessionType(),
                        session.browserLabel(),
                        session.deviceLabel(),
                        session.ipAddress(),
                        session.locationLabel(),
                        Boolean.TRUE.equals(unusualBySession.get(session.sessionType() + ":" + session.id())),
                        session.deviceLocationLabel(),
                        session.deviceLatitude(),
                        session.deviceLongitude(),
                        session.deviceLocationCapturedAt(),
                        session.loginMethod(),
                        session.createdAt(),
                        session.lastSeenAt(),
                        session.expiresAt(),
                        session.current(),
                        session.active()))
                .toList();
    }
}
