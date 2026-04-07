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
import dev.inboxbridge.service.EnvSourceService;
import dev.inboxbridge.service.MicrosoftOAuthService;
import dev.inboxbridge.service.UserEmailAccountService;
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

    @Inject
    MicrosoftOAuthCallbackPageRenderer callbackPageRenderer;

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
    @Produces(MediaType.TEXT_HTML)
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {
        String language = callbackLanguage(state);
        boolean secureStorageConfigured = microsoftOAuthService.secureStorageConfigured();
        if (error != null && !error.isBlank()) {
            return Response.ok(callbackPageRenderer.renderPage(
                    language,
                    localized(language, microsoftErrorTitle(error), "access_denied".equalsIgnoreCase(error) ? "Permissao necessaria no Microsoft OAuth" : "Erro de Microsoft OAuth"),
                    localized(language, microsoftErrorMessage(error, errorDescription), localizeMicrosoftErrorMessage(error, errorDescription)),
                    Map.of(
                            localized(language, "Error", "Erro"), error,
                            localized(language, "Description", "Descricao"), errorDescription == null ? "" : errorDescription),
                    false,
                    null,
                    null,
                    null,
                    null), MediaType.TEXT_HTML).build();
        }

        MicrosoftOAuthService.BrowserCallbackValidation callbackValidation;
        try {
            callbackValidation = microsoftOAuthService.validateBrowserCallback(state);
        } catch (IllegalArgumentException e) {
            return Response.ok(callbackPageRenderer.renderPage(
                    language,
                    localized(language, "Invalid OAuth State", "Estado OAuth invalido"),
                    localized(language,
                            "The callback state was missing or expired. Start the Microsoft OAuth flow again from InboxBridge.",
                            "O estado do retorno estava em falta ou expirou. Inicie novamente o fluxo Microsoft OAuth a partir do InboxBridge."),
                    Map.of(
                            localized(language, "Code", "Codigo"), code == null ? "" : code,
                            localized(language, "Error", "Erro"), e.getMessage()),
                    false,
                    null,
                    null,
                    null,
                    null), MediaType.TEXT_HTML).build();
        }

        Map<String, String> fields = OAuthPageSupport.orderedFields(
                localized(callbackValidation.language(), "Source ID", "ID da conta"), callbackValidation.subjectId(),
                localized(callbackValidation.language(), "Authorization Code", "Codigo de autorizacao"), code == null ? "" : code,
                localized(callbackValidation.language(), "Exchange Endpoint", "Endpoint de troca"), "POST /api/microsoft-oauth/exchange");
        return Response.ok(callbackPageRenderer.renderPage(
                callbackValidation.language(),
                localized(callbackValidation.language(), "Microsoft OAuth Code Received", "Codigo do Microsoft OAuth recebido"),
                localized(callbackValidation.language(),
                        secureStorageConfigured
                                ? "Secure token storage is enabled. You can exchange this authorization code directly in the browser and InboxBridge will store it securely and renew access automatically."
                                : "Secure token storage is required before exchanging this authorization code. Set SECURITY_TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key, restart InboxBridge, and then retry the OAuth flow.",
                        secureStorageConfigured
                                ? "O armazenamento seguro de tokens esta ativo. Pode trocar este codigo de autorizacao diretamente no browser e o InboxBridge vai guarda-lo de forma segura e renovar o acesso automaticamente."
                                : "O armazenamento seguro de tokens e obrigatorio antes de trocar este codigo de autorizacao. Defina SECURITY_TOKEN_ENCRYPTION_KEY com uma chave base64 de 32 bytes, reinicie o InboxBridge e repita o fluxo OAuth."),
                fields,
                true,
                callbackValidation.subjectId(),
                "",
                state,
                code == null ? "" : code), MediaType.TEXT_HTML).build();
    }

    private String microsoftErrorTitle(String error) {
        if ("access_denied".equalsIgnoreCase(error)) {
            return "Microsoft OAuth Permission Required";
        }
        return "Microsoft OAuth Error";
    }

    private String microsoftErrorMessage(String error, String errorDescription) {
        if ("access_denied".equalsIgnoreCase(error)) {
            return "Microsoft OAuth did not receive the required consent. Retry the flow and approve every requested mailbox permission so InboxBridge can refresh tokens and read the source mailbox.";
        }
        if (errorDescription != null && !errorDescription.isBlank()) {
            return "The Microsoft authorization step failed. Retry the flow after correcting the Microsoft consent or app configuration.";
        }
        return "The Microsoft authorization step failed. Retry the flow and approve every requested permission.";
    }

    private String localizeMicrosoftErrorMessage(String error, String errorDescription) {
        if ("access_denied".equalsIgnoreCase(error)) {
            return "O Microsoft OAuth nao recebeu o consentimento necessario. Repita o processo e aceite todas as permissoes pedidas para a caixa de correio, para que o InboxBridge possa renovar tokens e ler a conta de origem.";
        }
        if (errorDescription != null && !errorDescription.isBlank()) {
            return "O passo de autorizacao da Microsoft falhou. Repita o processo depois de corrigir o consentimento Microsoft ou a configuracao da aplicacao.";
        }
        return "O passo de autorizacao da Microsoft falhou. Repita o processo e aceite todas as permissoes pedidas.";
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

    private String localized(String language, String english, String portuguese) {
        String normalized = OAuthPageI18n.normalize(language);
        if ("pt-PT".equals(normalized) || "pt-BR".equals(normalized)) {
            return portuguese;
        }
        return OAuthPageI18n.text(language, english);
    }
}
