package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.RemoteControlView;
import dev.inboxbridge.dto.RemoteSessionUserResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.auth.AuthClientAddressService;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.remote.RemoteControlService;
import dev.inboxbridge.web.polling.PollingResource;
import dev.inboxbridge.web.remote.RemoteControlResource;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class WebInfrastructureTest {

    @Test
    void healthSummaryReturnsStatusAndImportedMessageCount() {
        HealthResource resource = new HealthResource();
        resource.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public long count() {
                return 42L;
            }
        };

        Map<String, Object> summary = resource.summary();

        assertEquals("UP", summary.get("status"));
        assertEquals(42L, summary.get("importedMessages"));
    }

    @Test
    void pollingResourceRunsManualUserPollForCurrentUser() {
        AppUser user = user(7L, "alice", AppUser.Role.USER);
        PollRunResult result = finishedResult(2, 1);

        PollingResource resource = new PollingResource();
        resource.setCurrentUserContext(currentUserContext(user));
        resource.setPollingService(new PollingService() {
            @Override
            public PollRunResult runPollForUser(AppUser actor, String trigger) {
                assertEquals(user, actor);
                assertEquals("manual-api", trigger);
                return result;
            }
        });

        PollRunResult response = resource.runNow();

        assertEquals(2, response.getFetched());
        assertEquals(1, response.getImported());
        assertTrue(response.getFinishedAt() != null);
    }

    @Test
    void pollingResourceLiveControlsDelegatePauseResumeStopMoveNextAndRetry() {
        AppUser user = user(7L, "alice", AppUser.Role.USER);
        PollLiveView view = new PollLiveView(true, "run-1", "RUNNING", "manual-api", "alice", true, "source-1", null, null, List.of());
        TrackingPollingLiveService liveService = new TrackingPollingLiveService(view);

        PollingResource resource = new PollingResource();
        resource.setCurrentUserContext(currentUserContext(user));
        resource.setPollingLiveService(liveService);

        assertEquals(view, resource.live());
        assertEquals(view, resource.pause());
        assertEquals(view, resource.resume());
        assertEquals(view, resource.stop());
        assertEquals(view, resource.moveNext("source-2"));
        assertEquals(view, resource.retry("source-3"));
        assertEquals(List.of("pause:alice", "resume:alice", "stop:alice", "move:alice:source-2", "retry:alice:source-3"), liveService.actions);
    }

    @Test
    void remoteControlResourceBuildsActorKeyAndDelegatesPolls() {
        AppUser admin = user(11L, "admin", AppUser.Role.ADMIN);
        PollRunResult pollResult = finishedResult(1, 1);
        RemoteControlView view = new RemoteControlView(
                new RemoteSessionUserResponse(admin.id, 501L, admin.username, admin.role.name(), true, true, true, false, "en", "SYSTEM", "AUTO", "AUTO", ""),
                List.of(),
                false,
                true,
                false,
                "PT1M",
                3);

        RemoteControlResource resource = new RemoteControlResource();
        resource.setCurrentUserContext(currentUserContext(admin));
        resource.setAuthClientAddressService(new AuthClientAddressService() {
            @Override
            public String resolveClientKey(jakarta.ws.rs.core.HttpHeaders headers, String directRemoteAddress) {
                assertNull(directRemoteAddress);
                return "203.0.113.9";
            }
        });
        resource.setRemoteControlService(new RemoteControlService() {
            @Override
            public RemoteControlView viewFor(AppUser actor) {
                assertEquals(admin, actor);
                return view;
            }

            @Override
            public PollRunResult runUserPoll(AppUser actor, String actorKey) {
                assertEquals(admin, actor);
                assertEquals("remote-user:11:203.0.113.9", actorKey);
                return pollResult;
            }

            @Override
            public PollRunResult runAllUsersPoll(AppUser actor, String actorKey) {
                assertEquals(admin, actor);
                assertEquals("remote-admin:11:203.0.113.9", actorKey);
                return pollResult;
            }

            @Override
            public PollRunResult runSourcePoll(AppUser actor, String sourceId, String actorKey) {
                assertEquals(admin, actor);
                assertEquals("fetcher-1", sourceId);
                assertEquals("remote-source:fetcher-1:11:203.0.113.9", actorKey);
                return pollResult;
            }
        });

        assertEquals(view, resource.control());
        assertEquals(pollResult, resource.runUserPoll());
        assertEquals(pollResult, resource.runAllUsersPoll());
        assertEquals(pollResult, resource.runSourcePoll("fetcher-1"));
    }

    @Test
    void remoteControlResourceWrapsInvalidSourceErrorsAsBadRequest() {
        AppUser user = user(8L, "alice", AppUser.Role.USER);

        RemoteControlResource resource = new RemoteControlResource();
        resource.setCurrentUserContext(currentUserContext(user));
        resource.setAuthClientAddressService(new AuthClientAddressService() {
            @Override
            public String resolveClientKey(jakarta.ws.rs.core.HttpHeaders headers, String directRemoteAddress) {
                return "198.51.100.4";
            }
        });
        resource.setRemoteControlService(new RemoteControlService() {
            @Override
            public PollRunResult runSourcePoll(AppUser actor, String sourceId, String actorKey) {
                throw new IllegalArgumentException("Unknown mail fetcher id");
            }
        });

        BadRequestException error = assertThrows(BadRequestException.class, () -> resource.runSourcePoll("missing"));

        assertEquals("Unknown mail fetcher id", error.getMessage());
        assertTrue(error.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void exceptionMappersReturnStructuredApiErrors() {
        IllegalArgumentExceptionMapper illegalArgumentMapper = new IllegalArgumentExceptionMapper();
        Response illegalArgumentResponse = illegalArgumentMapper.toResponse(
                new IllegalArgumentException("Outer problem", new IllegalStateException("Invalid or expired OAuth state")));

        assertEquals(400, illegalArgumentResponse.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, illegalArgumentResponse.getMediaType().toString());
        ApiError illegalArgumentPayload = (ApiError) illegalArgumentResponse.getEntity();
        assertEquals("oauth_state_invalid_or_expired", illegalArgumentPayload.code());
        assertEquals("Outer problem", illegalArgumentPayload.message());
        assertEquals("Invalid or expired OAuth state", illegalArgumentPayload.details());

        BadRequestExceptionMapper badRequestMapper = new BadRequestExceptionMapper();
        Response badRequestResponse = badRequestMapper.toResponse(
                new BadRequestException("Top level", new IllegalArgumentException("Current password is incorrect")));

        assertEquals(400, badRequestResponse.getStatus());
        ApiError badRequestPayload = (ApiError) badRequestResponse.getEntity();
        assertEquals("account_current_password_incorrect", badRequestPayload.code());
        assertEquals("Current password is incorrect", badRequestPayload.message());
        assertEquals("Current password is incorrect", badRequestPayload.details());

        ForbiddenExceptionMapper forbiddenMapper = new ForbiddenExceptionMapper();
        Response forbiddenResponse = forbiddenMapper.toResponse(
                new ForbiddenException("Wrapper", new IllegalStateException("Admin access required")));

        assertEquals(403, forbiddenResponse.getStatus());
        ApiError forbiddenPayload = (ApiError) forbiddenResponse.getEntity();
        assertEquals("admin_access_required", forbiddenPayload.code());
        assertEquals("Wrapper", forbiddenPayload.message());
        assertEquals("Admin access required", forbiddenPayload.details());
    }

    @Test
    void deepestMessagePrefersLastNonBlankCauseMessage() {
        Throwable nested = new IllegalArgumentException(" top level ", new IllegalStateException(" ", new RuntimeException(" final detail ")));

        assertEquals("final detail", ApiErrorDetails.deepestMessage(nested));
        assertEquals("", ApiErrorDetails.deepestMessage(null));
    }

    @Test
    void apiErrorCodesResolveKnownMessagesAndFallbacks() {
        assertEquals("bad_request", ApiErrorCodes.resolve(null, 400));
        assertEquals("forbidden", ApiErrorCodes.resolve(" ", 403));
        assertEquals("auth_invalid_credentials", ApiErrorCodes.resolve("Invalid username or password", 400));
        assertEquals("auth_login_blocked", ApiErrorCodes.resolve("Too many failed sign-in attempts from this address.", 429));
        assertEquals("auth_registration_blocked", ApiErrorCodes.resolve("Too many failed registration attempts from this address.", 429));
        assertEquals("source_unknown", ApiErrorCodes.resolve("Unknown source id: fetcher-1", 400));
        assertEquals("poll_interval_format_invalid", ApiErrorCodes.resolve("Unsupported poll interval format. Value must use ISO-8601 duration syntax.", 400));
        assertEquals("poll_interval_unit_invalid", ApiErrorCodes.resolve("Unsupported poll interval unit. Use seconds, minutes, hours, or days.", 400));
        assertEquals("secure_token_storage_required", ApiErrorCodes.resolve("Secure token storage is required before completing Microsoft OAuth.", 400));
        assertEquals("secure_token_storage_not_configured", ApiErrorCodes.resolve("Secure token storage is not configured. Configure SECURITY_TOKEN_ENCRYPTION_KEY first.", 400));
        assertEquals("passkey_wrong_user", ApiErrorCodes.resolve("That passkey belongs to a different user.", 400));
        assertEquals("mail_authentication_failed", ApiErrorCodes.resolve("AUTHENTICATE failed for mailbox", 400));
        assertEquals("forbidden", ApiErrorCodes.resolve("Something else", 403));
        assertEquals("bad_request", ApiErrorCodes.resolve("Something else", 400));
    }

    private static AppUser user(Long id, String username, AppUser.Role role) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = username;
        user.role = role;
        user.active = true;
        user.approved = true;
        return user;
    }

    private static CurrentUserContext currentUserContext(AppUser user) {
        CurrentUserContext context = new CurrentUserContext();
        context.setUser(user);
        return context;
    }

    private static PollRunResult finishedResult(int fetched, int imported) {
        PollRunResult result = new PollRunResult();
        for (int index = 0; index < fetched; index += 1) {
            result.incrementFetched();
        }
        for (int index = 0; index < imported; index += 1) {
            result.incrementImported();
        }
        result.finish();
        return result;
    }

    private static final class TrackingPollingLiveService extends PollingLiveService {
        private final PollLiveView view;
        private final java.util.List<String> actions = new java.util.ArrayList<>();

        private TrackingPollingLiveService(PollLiveView view) {
            this.view = view;
        }

        @Override
        public PollLiveView snapshotFor(AppUser viewer) {
            return view;
        }

        @Override
        public boolean requestPause(AppUser actor) {
            actions.add("pause:" + actor.username);
            return true;
        }

        @Override
        public boolean requestResume(AppUser actor) {
            actions.add("resume:" + actor.username);
            return true;
        }

        @Override
        public boolean requestStop(AppUser actor) {
            actions.add("stop:" + actor.username);
            return true;
        }

        @Override
        public boolean moveSourceToFront(AppUser actor, String sourceId) {
            actions.add("move:" + actor.username + ":" + sourceId);
            return true;
        }

        @Override
        public boolean retrySource(AppUser actor, String sourceId) {
            actions.add("retry:" + actor.username + ":" + sourceId);
            return true;
        }
    }
}
