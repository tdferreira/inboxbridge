package dev.inboxbridge.web.oauth;

import dev.inboxbridge.dto.GoogleOAuthCodeRequest;
import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.dto.OAuthUrlResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.EnvSourceService;
import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.UserEmailAccountService;
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

    @Inject
    GoogleOAuthCallbackPageRenderer callbackPageRenderer;

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
    @Produces(MediaType.TEXT_HTML)
    public String callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {
        String language = resolveCallbackLanguage(state);
        boolean secureStorageConfigured = googleOAuthService.secureStorageConfigured();
        if (error != null && !error.isBlank()) {
            return callbackPageRenderer.renderErrorPage(
                    language,
                    localized(language, googleErrorTitle(error), "access_denied".equalsIgnoreCase(error) ? "Permissao necessaria no Google OAuth" : "Erro de Google OAuth"),
                    localized(language, googleErrorMessage(error, errorDescription), localizeGoogleErrorMessage(error, errorDescription)),
                    error,
                    errorDescription);
        }
        GoogleOAuthService.CallbackValidation callbackValidation = null;
        String statusMessage = localized(language,
                secureStorageConfigured
                        ? "Secure token storage is enabled. Use the button below to exchange the code and InboxBridge will store the token securely and renew access automatically."
                        : "Secure token storage is required before exchanging this authorization code. Set SECURITY_TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key, restart InboxBridge, and then retry the OAuth flow.",
                secureStorageConfigured
                        ? "O armazenamento seguro de tokens esta ativo. Use o botao abaixo para trocar o codigo e o InboxBridge vai guardar o token de forma segura e renovar o acesso automaticamente."
                        : "O armazenamento seguro de tokens e obrigatorio antes de trocar este codigo de autorizacao. Defina SECURITY_TOKEN_ENCRYPTION_KEY com uma chave base64 de 32 bytes, reinicie o InboxBridge e repita o fluxo OAuth.");
        if (state != null && !state.isBlank()) {
            try {
                callbackValidation = googleOAuthService.validateCallback(state);
                language = callbackValidation.language();
                statusMessage = localized(language,
                        secureStorageConfigured
                                ? "Secure token storage is enabled for " + callbackValidation.targetLabel() + ". Use the button below to exchange the code and InboxBridge will store the token securely and renew access automatically."
                                : "Secure token storage is required before exchanging the authorization code for " + callbackValidation.targetLabel() + ". Set SECURITY_TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key, restart InboxBridge, and then retry the OAuth flow.",
                        secureStorageConfigured
                                ? "O armazenamento seguro de tokens esta ativo para " + callbackValidation.targetLabel() + ". Use o botao abaixo para trocar o codigo e o InboxBridge vai guardar o token de forma segura e renovar o acesso automaticamente."
                                : "O armazenamento seguro de tokens e obrigatorio antes de trocar o codigo de autorizacao para " + callbackValidation.targetLabel() + ". Defina SECURITY_TOKEN_ENCRYPTION_KEY com uma chave base64 de 32 bytes, reinicie o InboxBridge e repita o fluxo OAuth.");
            } catch (IllegalArgumentException e) {
                statusMessage = localized(language,
                        "The Google OAuth state is missing or expired. Start the flow again from InboxBridge.",
                        "O estado do Google OAuth esta em falta ou expirou. Inicie novamente o fluxo a partir do InboxBridge.");
            }
        }
        return callbackPageRenderer.renderCallbackPage(language, statusMessage, code, state, error, errorDescription);
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

    private String googleErrorTitle(String error) {
        if ("access_denied".equalsIgnoreCase(error)) {
            return "Google OAuth Permission Required";
        }
        return "Google OAuth Error";
    }

    private String googleErrorMessage(String error, String errorDescription) {
        if ("access_denied".equalsIgnoreCase(error)) {
            return "Google OAuth did not receive the required consent. Retry the flow and approve every requested Gmail permission so InboxBridge can write imported mail and labels.";
        }
        if (errorDescription != null && !errorDescription.isBlank()) {
            return "Google OAuth returned an error. Retry the flow after correcting the Google consent or OAuth app configuration.";
        }
        return "Google OAuth returned an error. Retry the flow and make sure every requested permission is approved.";
    }

    private String localizeGoogleErrorMessage(String error, String errorDescription) {
        if ("access_denied".equalsIgnoreCase(error)) {
            return "O Google OAuth nao recebeu o consentimento necessario. Repita o processo e aceite todas as permissoes pedidas do Gmail para que o InboxBridge possa gravar emails importados e etiquetas.";
        }
        if (errorDescription != null && !errorDescription.isBlank()) {
            return "O Google OAuth devolveu um erro. Repita o processo depois de corrigir o consentimento Google ou a configuracao da aplicacao OAuth.";
        }
        return "O Google OAuth devolveu um erro. Repita o processo e garanta que todas as permissoes pedidas sao aceites.";
    }

    private String localized(String language, String english, String portuguese) {
        return OAuthPageSupport.localized(language, english, portuguese);
    }

    private String resolveCallbackLanguage(String state) {
        try {
            return googleOAuthService.validateCallback(state).language();
        } catch (Exception ignored) {
            return "en";
        }
    }
}
