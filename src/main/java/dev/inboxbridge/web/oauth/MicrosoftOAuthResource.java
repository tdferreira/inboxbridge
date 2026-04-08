package dev.inboxbridge.web.oauth;

import java.net.URI;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.MicrosoftOAuthCodeRequest;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.dto.OAuthUrlResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.mail.EnvSourceService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.user.UserEmailAccountService;
import dev.inboxbridge.web.ApiErrorCodes;
import dev.inboxbridge.web.ApiErrorDetails;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/microsoft-oauth")
@Produces(MediaType.APPLICATION_JSON)
public class MicrosoftOAuthResource {

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    UserEmailAccountService userEmailAccountService;

    @Inject
    EnvSourceService envSourceService;

    @GET
    @Path("/url")
    @RequireAuth
    public OAuthUrlResponse authorizationUrl(@QueryParam("sourceId") String sourceId) {
        try {
            authorizeSource(sourceId);
            return new OAuthUrlResponse(microsoftOAuthService.buildAuthorizationUrl(sourceId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/start")
    @RequireAuth
    public Response start(@QueryParam("sourceId") String sourceId, @QueryParam("lang") String language) {
        try {
            authorizeSource(sourceId);
            return Response.seeOther(URI.create(microsoftOAuthService.buildAuthorizationUrl(sourceId, language))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/start/destination")
    @RequireAuth
    public Response startDestination(@QueryParam("lang") String language) {
        try {
            return Response.seeOther(URI.create(microsoftOAuthService.buildDestinationAuthorizationUrl(currentUserContext.user().id, language))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/sources")
    @RequireAuth
    public List<MicrosoftOAuthSourceOption> sources() {
        AppUser user = currentUserContext.user();
        List<MicrosoftOAuthSourceOption> visible = microsoftOAuthService.listMicrosoftOAuthSources();
        if (user.role == AppUser.Role.ADMIN) {
            return visible;
        }
        return visible.stream()
                .filter(source -> userEmailAccountService.findByEmailAccountId(source.id())
                        .map(bridge -> bridge.userId.equals(user.id))
                        .orElse(false))
                .toList();
    }

    @POST
    @Path("/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response exchange(MicrosoftOAuthCodeRequest request) {
        try {
            if (request.state() != null && !request.state().isBlank()) {
                return Response.ok(microsoftOAuthService.exchangeAuthorizationCodeByState(request.state(), request.code())).build();
            }
            return Response.ok(microsoftOAuthService.exchangeAuthorizationCode(request.sourceId(), request.code())).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(
                            ApiErrorCodes.resolve(ApiErrorDetails.deepestMessage(e).isBlank() ? e.getMessage() : ApiErrorDetails.deepestMessage(e), 400),
                            e.getMessage(),
                            ApiErrorDetails.deepestMessage(e)))
                    .build();
        }
    }

    @GET
    @Path("/callback")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {
        String language = callbackLanguage(state);
        if (error != null && !error.isBlank()) {
            return redirectCallback(language, null, null, error, errorDescription);
        }

        try {
            language = microsoftOAuthService.validateBrowserCallback(state).language();
        } catch (IllegalArgumentException e) {
            return redirectCallback(
                    language,
                    null,
                    null,
                    "invalid_state",
                    "The callback state was missing or expired. Start the Microsoft OAuth flow again from InboxBridge.");
        }
        if (code == null || code.isBlank()) {
            return redirectCallback(
                    language,
                    null,
                    state,
                    "missing_code",
                    "Microsoft OAuth returned without an authorization code. Start the flow again from InboxBridge.");
        }
        return redirectCallback(language, code, state, null, null);
    }

    private void authorizeSource(String sourceId) {
        AppUser user = currentUserContext.user();
        boolean envSource = envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .anyMatch(source -> source.id().equals(sourceId));
        if (envSource) {
            if (user.role != AppUser.Role.ADMIN) {
                throw new ForbiddenException("Admin access is required for environment-managed email accounts");
            }
            return;
        }
        UserEmailAccount bridge = userEmailAccountService.findByEmailAccountId(sourceId).orElseThrow(() -> new BadRequestException("Unknown source id"));
        if (user.role == AppUser.Role.ADMIN) {
            return;
        }
        if (!bridge.userId.equals(user.id)) {
            throw new ForbiddenException("You do not have access to that email account");
        }
    }

    private String callbackLanguage(String state) {
        try {
            return microsoftOAuthService.validateCallback(state).language();
        } catch (Exception ignored) {
            return "en";
        }
    }

    private Response redirectCallback(String language, String code, String state, String error, String errorDescription) {
        return Response.seeOther(OAuthCallbackRedirectBuilder.build(
                "/oauth/microsoft/callback",
                OAuthCallbackRedirectBuilder.parameters(language, code, state, error, errorDescription))).build();
    }
}
