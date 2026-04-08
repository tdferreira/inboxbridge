package dev.inboxbridge.web.oauth;

import dev.inboxbridge.dto.GoogleOAuthCodeRequest;
import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.dto.OAuthUrlResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.mail.EnvSourceService;
import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.user.UserEmailAccountService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/google-oauth")
@Produces(MediaType.APPLICATION_JSON)
public class GoogleOAuthResource {

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    CurrentUserContext currentUserContext;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    UserEmailAccountService userEmailAccountService;

    @GET
    @Path("/url")
    public OAuthUrlResponse authorizationUrl() {
        return new OAuthUrlResponse(googleOAuthService.buildAuthorizationUrl());
    }

    @GET
    @Path("/start/system")
    @RequireAdmin
    public Response startSystem(@QueryParam("lang") String language) {
        return Response.seeOther(java.net.URI.create(
                googleOAuthService.buildAuthorizationUrlWithState(
                        googleOAuthService.systemProfileForCallbacks(),
                        "Shared Gmail account",
                        language))).build();
    }

    @GET
    @Path("/start/self")
    @RequireAuth
    public Response startSelf(@QueryParam("lang") String language) {
        GoogleOAuthService.GoogleOAuthProfile profile = userGmailConfigService.googleProfileForUser(currentUserContext.user().id)
                .orElseThrow(() -> new jakarta.ws.rs.BadRequestException(
                        "Configure your Gmail redirect URI or a shared Google OAuth client before starting Google OAuth."));
        return Response.seeOther(java.net.URI.create(
                googleOAuthService.buildAuthorizationUrlWithState(profile, "User Gmail account", language))).build();
    }

    @GET
    @Path("/start/source")
    @RequireAuth
    public Response startSource(@QueryParam("sourceId") String sourceId, @QueryParam("lang") String language) {
        GoogleOAuthService.GoogleOAuthProfile profile = authorizeGoogleSource(sourceId);
        return Response.seeOther(java.net.URI.create(
                googleOAuthService.buildAuthorizationUrlWithState(profile, "Mail account " + sourceId, language))).build();
    }

    @POST
    @Path("/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    public GoogleTokenExchangeResponse exchange(GoogleOAuthCodeRequest request) {
        if (request.state() != null && !request.state().isBlank()) {
            return googleOAuthService.exchangeAuthorizationCode(request.state(), request.code());
        }
        return googleOAuthService.exchangeAuthorizationCode(request.code());
    }

    @GET
    @Path("/callback")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {
        String language = resolveCallbackLanguage(state);
        if (error != null && !error.isBlank()) {
            return redirectCallback(language, null, null, error, errorDescription);
        }
        if (state != null && !state.isBlank()) {
            try {
                language = googleOAuthService.validateCallback(state).language();
            } catch (IllegalArgumentException e) {
                return redirectCallback(
                        language,
                        null,
                        null,
                        "invalid_state",
                        "The Google OAuth state is missing or expired. Start the flow again from InboxBridge.");
            }
        }
        if (code == null || code.isBlank()) {
            return redirectCallback(
                    language,
                    null,
                    state,
                    "missing_code",
                    "Google OAuth returned without an authorization code. Start the flow again from InboxBridge.");
        }
        return redirectCallback(language, code, state, null, null);
    }

    private GoogleOAuthService.GoogleOAuthProfile authorizeGoogleSource(String sourceId) {
        AppUser actor = currentUserContext.user();
        return envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(source -> source.id().equals(sourceId))
                .findFirst()
                .map(source -> {
                    if (actor.role != AppUser.Role.ADMIN) {
                        throw new jakarta.ws.rs.ForbiddenException("Only admins can connect environment-managed Google source OAuth.");
                    }
                    if (source.authMethod() != dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.OAUTH2
                            || source.oauthProvider() != dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.GOOGLE) {
                        throw new jakarta.ws.rs.BadRequestException("This mail account is not configured for Google OAuth.");
                    }
                    return googleOAuthService.sourceProfile(source);
                })
                .orElseGet(() -> userEmailAccountService.findByEmailAccountId(sourceId)
                        .filter(bridge -> bridge.userId.equals(actor.id))
                        .map(bridge -> {
                            if (bridge.authMethod != dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.OAUTH2
                                    || bridge.oauthProvider != dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.GOOGLE) {
                                throw new jakarta.ws.rs.BadRequestException("This mail account is not configured for Google OAuth.");
                            }
                            return googleOAuthService.sourceProfile(
                                    bridge.emailAccountId,
                                    userEmailAccountService.decryptRefreshToken(bridge),
                                    null);
                        })
                        .orElseThrow(() -> new jakarta.ws.rs.BadRequestException("Unknown mail account id")));
    }

    private Response redirectCallback(String language, String code, String state, String error, String errorDescription) {
        return Response.seeOther(OAuthCallbackRedirectBuilder.build(
                "/oauth/google/callback",
                OAuthCallbackRedirectBuilder.parameters(language, code, state, error, errorDescription))).build();
    }

    private String resolveCallbackLanguage(String state) {
        try {
            return googleOAuthService.validateCallback(state).language();
        } catch (Exception ignored) {
            return "en";
        }
    }
}
