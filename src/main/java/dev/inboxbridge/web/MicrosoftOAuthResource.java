package dev.inboxbridge.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.dto.MicrosoftOAuthCodeRequest;
import dev.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.inboxbridge.dto.OAuthUrlResponse;
import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.MicrosoftOAuthService;
import dev.inboxbridge.service.UserEmailAccountService;
import dev.inboxbridge.service.EnvSourceService;
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
                .filter(source -> userEmailAccountService.findByBridgeId(source.id())
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
            return htmlCallbackPage(
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
                    null);
        }

        MicrosoftOAuthService.BrowserCallbackValidation callbackValidation;
        try {
          callbackValidation = microsoftOAuthService.validateBrowserCallback(state);
        } catch (IllegalArgumentException e) {
            return htmlCallbackPage(
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
                    null);
        }

        Map<String, String> fields = orderedFields(
          localized(callbackValidation.language(), "Source ID", "ID da conta"), callbackValidation.subjectId(),
                localized(callbackValidation.language(), "Authorization Code", "Codigo de autorizacao"), code == null ? "" : code,
                localized(callbackValidation.language(), "Exchange Endpoint", "Endpoint de troca"), "POST /api/microsoft-oauth/exchange");
        return htmlCallbackPage(
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
                code == null ? "" : code);
    }

    private Response htmlCallbackPage(
            String language,
            String title,
            String message,
            Map<String, String> fields,
            boolean success,
            String sourceId,
            String configKey,
            String state,
            String code) {
        String statusTone = success ? "Success" : "Attention";
        String accentColor = success ? "#1f7a4a" : "#8a5a12";

        StringBuilder fieldsHtml = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            fieldsHtml.append("""
                    <div class="field">
                      <div class="label">""")
                    .append(escapeHtml(entry.getKey()))
                    .append("""
                    </div>
                      <pre>""")
                    .append(escapeHtml(entry.getValue()))
                    .append("""
                    </pre>
                    </div>
                    """);
        }

        String browserExchangeHtml = "";
        if (success && state != null && !state.isBlank()) {
            browserExchangeHtml = """
                    <div class="exchange-card">
                      <div class="actions">
                        <button class="primary" id="exchangeButton" type="button">%s</button>
                        <button class="secondary" id="copyCodeButton" type="button">%s</button>
                        <button class="danger hidden" id="cancelReturnButton" type="button">%s</button>
                        <a class="button-link" id="returnLink" href="/">%s</a>
                      </div>
                      <div class="status" id="copyStatus"></div>
                      <div class="status" id="exchangeStatus"></div>
                      <div class="status" id="redirectStatus"></div>
                      <div class="field hidden" id="resultField">
                        <div class="label">%s</div>
                        <pre id="resultValue"></pre>
                      </div>
                      <div class="field hidden" id="envField">
                        <div class="label">%s</div>
                        <pre id="envAssignmentValue"></pre>
                        <div class="actions compact">
                          <button class="secondary" id="copyEnvAssignmentButton" type="button">%s</button>
                        </div>
                      </div>
                    </div>
                    <script>
                      const exchangeButton = document.getElementById("exchangeButton");
                      const copyCodeButton = document.getElementById("copyCodeButton");
                      const copyEnvAssignmentButton = document.getElementById("copyEnvAssignmentButton");
                      const copyStatus = document.getElementById("copyStatus");
                      const exchangeStatus = document.getElementById("exchangeStatus");
                      const redirectStatus = document.getElementById("redirectStatus");
                      const resultField = document.getElementById("resultField");
                      const resultValue = document.getElementById("resultValue");
                      const envField = document.getElementById("envField");
                      const envAssignmentValue = document.getElementById("envAssignmentValue");
                      const cancelReturnButton = document.getElementById("cancelReturnButton");
                      const returnLink = document.getElementById("returnLink");
                      const serverRenderedSourceId = %s;
                      const serverRenderedState = %s;
                      const serverRenderedConfigKey = %s;
                      const serverRenderedCode = %s;
                      const callbackParams = new URLSearchParams(window.location.search);
                      const oauthCode = callbackParams.get("code") || serverRenderedCode;
                      const oauthState = callbackParams.get("state") || serverRenderedState;
                      const oauthSourceId = serverRenderedSourceId;
                      const oauthConfigKey = serverRenderedConfigKey;
                      const oauthError = callbackParams.get("error") || "";
                      const oauthErrorDescription = callbackParams.get("error_description") || "";
                      let exchanged = false;
                      let exchangeAttempted = false;
                      let allowLeave = false;
                      let redirectTimerId = null;
                      let countdownIntervalId = null;

                      function clearAutoReturn() {
                        if (redirectTimerId) {
                          window.clearTimeout(redirectTimerId);
                          redirectTimerId = null;
                        }
                        if (countdownIntervalId) {
                          window.clearInterval(countdownIntervalId);
                          countdownIntervalId = null;
                        }
                      }

                      function startAutoReturn() {
                        clearAutoReturn();
                        cancelReturnButton?.classList.remove("hidden");
                        let secondsRemaining = 5;
                        const updateCountdown = () => {
                          redirectStatus.textContent = %s + secondsRemaining + %s;
                        };
                        updateCountdown();
                        countdownIntervalId = window.setInterval(() => {
                          secondsRemaining -= 1;
                          if (secondsRemaining <= 0) {
                            window.clearInterval(countdownIntervalId);
                            countdownIntervalId = null;
                            redirectStatus.textContent = %s;
                            return;
                          }
                          updateCountdown();
                        }, 1000);
                        redirectTimerId = window.setTimeout(() => {
                          cancelReturnButton?.classList.add("hidden");
                          window.location.assign("/");
                        }, 5000);
                      }

                      function cancelAutoReturn() {
                        clearAutoReturn();
                        allowLeave = true;
                        redirectStatus.textContent = %s;
                        cancelReturnButton?.classList.add("hidden");
                      }

                      async function copyText(text, label, manualCopyPrompt, manualCopyMessage, missingValueMessage) {
                        if (!text) {
                          copyStatus.textContent = missingValueMessage;
                          return;
                        }

                        let copied = false;
                        if (navigator.clipboard && navigator.clipboard.writeText && window.isSecureContext) {
                          try {
                            await navigator.clipboard.writeText(text);
                            copied = true;
                          } catch (error) {
                            copied = false;
                          }
                        }

                        if (!copied) {
                          const input = document.createElement("textarea");
                          input.value = text;
                          input.setAttribute("readonly", "readonly");
                          input.style.position = "fixed";
                          input.style.top = "0";
                          input.style.left = "0";
                          input.style.opacity = "0";
                          document.body.appendChild(input);
                          input.focus();
                          input.select();
                          try {
                            copied = document.execCommand("copy");
                          } catch (error) {
                            copied = false;
                          }
                          document.body.removeChild(input);
                        }

                        if (copied) {
                          copyStatus.textContent = label + %s;
                          return;
                        }

                        window.prompt(manualCopyPrompt, text);
                        copyStatus.textContent = manualCopyMessage;
                      }

                      copyCodeButton?.addEventListener("click", async () => {
                        await copyText(oauthCode, %s, %s, %s, %s);
                      });

                      copyEnvAssignmentButton?.addEventListener("click", async () => {
                        await copyText(envAssignmentValue.textContent, %s, %s, %s, %s);
                      });

                      cancelReturnButton?.addEventListener("click", () => {
                        cancelAutoReturn();
                      });

                      function formatExchangeError(payloadText, statusCode) {
                        let parsedMessage = payloadText || "";
                        try {
                          const parsed = JSON.parse(payloadText || "{}");
                          parsedMessage = parsed.error || parsed.message || parsedMessage;
                        } catch (error) {
                          // Keep the original payload text when the backend did not return JSON.
                        }

                        const normalized = parsedMessage.toLowerCase();
                        if (
                          normalized.includes("access_denied") ||
                          normalized.includes("consent_required") ||
                          normalized.includes("did not grant all required permissions") ||
                          normalized.includes("did not return a refresh token") ||
                          normalized.includes("offline_access") ||
                          normalized.includes("missing scopes")
                        ) {
                          return %s + parsedMessage;
                        }
                        if (!parsedMessage) {
                          return %s + statusCode + %s;
                        }
                        return %s + parsedMessage;
                      }

                      async function exchangeCode() {
                        if (!oauthCode) {
                          exchangeStatus.textContent = oauthError
                            ? %s + oauthError + (oauthErrorDescription ? ": " + oauthErrorDescription : ".")
                            : %s;
                          return;
                        }
                        if (!oauthState) {
                          exchangeStatus.textContent = %s;
                          return;
                        }
                        if (exchangeAttempted) {
                          return;
                        }
                        exchangeAttempted = true;
                        exchangeButton.disabled = true;
                        exchangeStatus.textContent = %s;
                        try {
                          const response = await fetch("/api/microsoft-oauth/exchange", {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ sourceId: oauthSourceId, code: oauthCode, state: oauthState })
                          });

                          const payloadText = await response.text();
                          if (!response.ok) {
                            exchangeStatus.textContent = formatExchangeError(payloadText, response.status);
                            return;
                          }

                          const payload = JSON.parse(payloadText);
                          const details = [
                            %s + (payload.sourceId || oauthSourceId),
                            %s + (payload.credentialKey || ""),
                            %s + (payload.storedInDatabase ? %s : %s),
                            %s + (payload.scope || ""),
                            %s + (payload.tokenType || ""),
                            %s + (payload.accessTokenExpiresAt || ""),
                            %s + (payload.nextStep || "")
                          ].join("\\n");
                          exchanged = true;
                          startAutoReturn();
                          resultValue.textContent = details;
                          resultField.classList.remove("hidden");

                          if (payload.usingEnvironmentFallback && payload.refreshToken) {
                            envAssignmentValue.textContent = oauthConfigKey + "=" + payload.refreshToken;
                            envField.classList.remove("hidden");
                            exchangeStatus.textContent = %s;
                          } else {
                            envField.classList.add("hidden");
                            envAssignmentValue.textContent = "";
                            exchangeStatus.textContent = %s;
                          }
                        } catch (error) {
                          exchangeAttempted = false;
                          exchangeStatus.textContent = %s;
                        } finally {
                          exchangeButton.disabled = false;
                        }
                      }

                      exchangeButton?.addEventListener("click", exchangeCode);
                      if (oauthError) {
                        exchangeStatus.textContent = %s + oauthError + (oauthErrorDescription ? ": " + oauthErrorDescription : ".");
                      } else if (oauthCode && oauthState) {
                        exchangeStatus.textContent = %s;
                        window.setTimeout(() => {
                          exchangeCode();
                        }, 0);
                      } else if (oauthCode) {
                        exchangeStatus.textContent = %s;
                      }

                      window.addEventListener("beforeunload", (event) => {
                        if (exchanged || allowLeave) {
                          return;
                        }
                        event.preventDefault();
                        event.returnValue = "";
                      });

                      returnLink?.addEventListener("click", (event) => {
                        if (exchanged) {
                          return;
                        }
                        const leave = window.confirm(%s);
                        if (leave) {
                          allowLeave = true;
                        }
                        if (!leave) {
                          event.preventDefault();
                          exchangeStatus.textContent = %s;
                        }
                      });
                    </script>
                    """.formatted(
                            escapeHtml(localized(language, "Exchange Code In Browser", "Trocar codigo no browser")),
                            escapeHtml(localized(language, "Copy Authorization Code", "Copiar codigo de autorizacao")),
                            escapeHtml(localized(language, "Cancel automatic redirect", "Cancelar redirecionamento automatico")),
                            escapeHtml(localized(language, "Return to InboxBridge", "Voltar ao InboxBridge")),
                            escapeHtml(localized(language, "Exchange Result", "Resultado da troca")),
                            escapeHtml(localized(language, "Env Assignment", "Atribuicao de ambiente")),
                            escapeHtml(localized(language, "Copy Env Assignment", "Copiar atribuicao de ambiente")),
                            jsString(localized(language, "Redirecting to InboxBridge in ", "A redirecionar para o InboxBridge em ")),
                            jsString(localized(language, " seconds.", " segundos.")),
                            jsString(localized(language, "Automatic redirect canceled. You can stay on this page and inspect the exchange details.", "Redirecionamento automatico cancelado. Pode permanecer nesta pagina e verificar os detalhes da troca.")),
                            jsString(localized(language, "Redirecting to InboxBridge now...", "A redirecionar para o InboxBridge agora...")),
                            jsString(localized(language, " copied to clipboard.", " copiado para a area de transferencia.")),
                            jsString(localized(language, "Authorization code", "Codigo de autorizacao")),
                            jsString(localized(language, "Copy the authorization code manually and press Cmd+C, then Enter.", "Copie manualmente o codigo de autorizacao e prima Cmd+C, depois Enter.")),
                            jsString(localized(language, "Clipboard access was blocked by the browser. A manual copy dialog was opened with the authorization code.", "O acesso a area de transferencia foi bloqueado pelo browser. Foi aberta uma janela para copiar manualmente o codigo de autorizacao.")),
                            jsString(localized(language, "No authorization code is available to copy from this callback URL.", "Nao existe nenhum codigo de autorizacao disponivel para copiar deste URL de retorno.")),
                            jsString(localized(language, "Env assignment", "Atribuicao de ambiente")),
                            jsString(localized(language, "Copy the env assignment manually and press Cmd+C, then Enter.", "Copie manualmente a atribuicao de ambiente e prima Cmd+C, depois Enter.")),
                            jsString(localized(language, "Clipboard access was blocked by the browser. A manual copy dialog was opened with the env assignment.", "O acesso a area de transferencia foi bloqueado pelo browser. Foi aberta uma janela para copiar manualmente a atribuicao de ambiente.")),
                            jsString(localized(language, "No env assignment is available to copy yet.", "Ainda nao existe nenhuma atribuicao de ambiente disponivel para copiar.")),
                            jsString(localized(language, "Microsoft OAuth is still missing one or more required permissions. Retry the flow and approve every requested mailbox permission, then try again. Details: ", "Ainda falta uma ou mais permissoes obrigatorias no Microsoft OAuth. Repita o processo e aceite todas as permissoes pedidas da caixa de correio. Detalhes: ")),
                            jsString(localized(language, "Exchange failed with status ", "A troca do codigo falhou com o estado ")),
                            jsString(localized(language, ". Check the server logs and Microsoft app settings.", ". Verifique os logs do servidor e as definicoes da aplicacao Microsoft.")),
                            jsString(localized(language, "Exchange failed: ", "A troca do codigo falhou: ")),
                            jsString(localized(language, "Microsoft OAuth returned ", "O Microsoft OAuth devolveu ")),
                            jsString(localized(language, "No authorization code was present in the callback URL.", "Nao foi encontrado qualquer codigo de autorizacao no URL de retorno.")),
                            jsString(localized(language, "The callback state is missing or expired. Start the Microsoft OAuth flow again from InboxBridge.", "O estado do retorno esta em falta ou expirou. Inicie novamente o fluxo Microsoft OAuth a partir do InboxBridge.")),
                            jsString(localized(language, "Exchanging authorization code...", "A trocar o codigo de autorizacao...")),
                            jsString(localized(language, "Source ID: ", "ID da conta: ")),
                            jsString(localized(language, "Credential Key: ", "Chave da credencial: ")),
                            jsString(localized(language, "Storage: ", "Armazenamento: ")),
                            jsString(localized(language, "Secure storage", "Armazenamento seguro")),
                            jsString(localized(language, "Environment fallback", "Fallback por ambiente")),
                            jsString(localized(language, "Scope: ", "Escopo: ")),
                            jsString(localized(language, "Token Type: ", "Tipo de token: ")),
                            jsString(localized(language, "Access Token Expires At: ", "Token de acesso expira em: ")),
                            jsString(localized(language, "Next Step: ", "Proximo passo: ")),
                            jsString(localized(language, "Exchange completed, but this env-managed source cannot poll yet. Copy the env assignment into your local .env, restart InboxBridge, and then polling will be able to use the new refresh token.", "Troca concluida, mas esta conta gerida por ambiente ainda nao pode ser verificada. Copie a atribuicao para o seu .env local, reinicie o InboxBridge e a verificacao podera usar o novo refresh token.")),
                            jsString(localized(language, "Exchange completed. The refresh token was stored securely and future renewals will be automatic.", "Troca concluida. O refresh token foi guardado de forma segura e as futuras renovacoes serao automaticas.")),
                            jsString(localized(language, "Exchange failed. Check the server logs and Microsoft app settings.", "A troca do codigo falhou. Verifique os logs do servidor e as definicoes da aplicacao Microsoft.")),
                            jsString(localized(language, "Microsoft OAuth returned ", "O Microsoft OAuth devolveu ")),
                            jsString(localized(language, "Authorization code received. Attempting automatic exchange...", "Codigo de autorizacao recebido. A tentar a troca automatica...")),
                            jsString(localized(language, "Authorization code received, but callback state is missing. Start the Microsoft OAuth flow again if automatic exchange does not work.", "Codigo de autorizacao recebido, mas o estado do retorno esta em falta. Inicie novamente o fluxo Microsoft OAuth se a troca automatica nao funcionar.")),
                            jsString(localized(language, "Leave this page without exchanging the code? If you continue, you must copy the code and handle the token exchange manually later.", "Sair desta pagina sem trocar o codigo? Se continuar, tera de copiar o codigo e tratar manualmente da troca do token mais tarde.")),
                            jsString(localized(language, "Exchange the code in the browser before leaving, or copy it now and complete the token exchange manually later.", "Troque o codigo no browser antes de sair, ou copie-o agora e conclua manualmente a troca do token mais tarde.")),
                            jsString(sourceId), jsString(state), jsString(configKey), jsString(code));
        }
        String auxiliaryActionsHtml = "";
        if (browserExchangeHtml.isBlank()) {
            auxiliaryActionsHtml = """
                    <div class="actions">
                      <a class="button-link" href="/">%s</a>
                    </div>
                    """.formatted(escapeHtml(localized(language, "Return to InboxBridge", "Voltar ao InboxBridge")));
        }

        String html = """
                <!doctype html>
                <html lang="%s">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root {
                      --bg: #f4efe6;
                      --panel: rgba(255, 251, 245, 0.9);
                      --ink: #241a14;
                      --muted: #6a5a4e;
                      --accent: %s;
                      --line: rgba(36, 26, 20, 0.12);
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      font-family: "IBM Plex Sans", "Avenir Next", sans-serif;
                      color: var(--ink);
                      background:
                        radial-gradient(circle at top left, rgba(31, 122, 74, 0.12), transparent 30%%),
                        radial-gradient(circle at top right, rgba(138, 90, 18, 0.12), transparent 35%%),
                        linear-gradient(160deg, #f8f2e7 0%%, #efe6d7 100%%);
                      display: grid;
                      place-items: center;
                      padding: 24px;
                    }
                    .card {
                      width: min(780px, 100%%);
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 24px;
                      box-shadow: 0 20px 60px rgba(36, 26, 20, 0.12);
                      overflow: hidden;
                    }
                    .hero {
                      padding: 28px 28px 18px;
                      border-bottom: 1px solid var(--line);
                    }
                    .eyebrow {
                      text-transform: uppercase;
                      letter-spacing: 0.16em;
                      font-size: 0.74rem;
                      color: var(--accent);
                      font-weight: 700;
                      margin-bottom: 10px;
                    }
                    h1 {
                      margin: 0 0 10px;
                      font-size: clamp(1.8rem, 3vw, 2.6rem);
                      line-height: 1.05;
                    }
                    p {
                      margin: 0;
                      color: var(--muted);
                      line-height: 1.6;
                    }
                    .body {
                      padding: 24px 28px 28px;
                      display: grid;
                      gap: 14px;
                    }
                    .field {
                      display: grid;
                      gap: 8px;
                    }
                    .label {
                      font-size: 0.78rem;
                      text-transform: uppercase;
                      letter-spacing: 0.12em;
                      color: var(--muted);
                      font-weight: 700;
                    }
                    pre {
                      margin: 0;
                      white-space: pre-wrap;
                      word-break: break-word;
                      padding: 14px 16px;
                      border-radius: 16px;
                      background: rgba(255, 255, 255, 0.78);
                      border: 1px solid var(--line);
                      font-family: "SFMono-Regular", "JetBrains Mono", monospace;
                      font-size: 0.92rem;
                      line-height: 1.5;
                    }
                    .hint {
                      padding-top: 8px;
                      color: var(--muted);
                      font-size: 0.94rem;
                    }
                    .exchange-card {
                      display: grid;
                      gap: 14px;
                      padding: 18px;
                      border-radius: 18px;
                      background: rgba(255, 255, 255, 0.72);
                      border: 1px solid var(--line);
                    }
                    .actions {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 10px;
                    }
                    .actions.compact {
                      padding-top: 6px;
                    }
                    button,
                    .button-link {
                      border: none;
                      border-radius: 999px;
                      padding: 13px 18px;
                      font: inherit;
                      font-weight: 700;
                      cursor: pointer;
                      text-decoration: none;
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                    }
                    button.primary {
                      color: white;
                      background: linear-gradient(135deg, var(--accent), #155f40);
                    }
                    button.danger {
                      color: white;
                      background: linear-gradient(135deg, #b43a28, #8b2d20);
                    }
                    button.secondary,
                    .button-link {
                      color: var(--ink);
                      background: rgba(255, 255, 255, 0.86);
                      border: 1px solid var(--line);
                    }
                    .status {
                      min-height: 1.3em;
                      color: var(--muted);
                    }
                    .hidden {
                      display: none;
                    }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <section class="hero">
                      <div class="eyebrow">%s</div>
                      <h1>%s</h1>
                      <p>%s</p>
                    </section>
                    <section class="body">
                      %s
                      %s
                      %s
                      <div class="hint">%s</div>
                    </section>
                  </main>
                </body>
                </html>
                """
                .formatted(
                        escapeHtml(language),
                        escapeHtml(title),
                        accentColor,
                        escapeHtml(localized(language, statusTone, "Success".equals(statusTone) ? "Sucesso" : "Atencao")),
                        escapeHtml(title),
                        escapeHtml(message.strip()),
                        fieldsHtml,
                        browserExchangeHtml,
                        auxiliaryActionsHtml,
                        escapeHtml(localized(language, "You can close this tab after the exchange succeeds and any remaining setup is complete.", "Pode fechar este separador depois de a troca ser concluida com sucesso e de qualquer configuracao restante ficar concluida.")));

        return Response.ok(html, MediaType.TEXT_HTML).build();
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

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String jsString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "'" + escaped + "'";
    }

    private Map<String, String> orderedFields(String... values) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            fields.put(values[i], values[i + 1]);
        }
        return fields;
    }

    private void authorizeSource(String sourceId) {
        AppUser user = currentUserContext.user();
        boolean envSource = envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .anyMatch(source -> source.id().equals(sourceId));
        if (envSource) {
            if (user.role != AppUser.Role.ADMIN) {
                throw new ForbiddenException("Admin access is required for environment-managed bridges");
            }
            return;
        }
        UserEmailAccount bridge = userEmailAccountService.findByBridgeId(sourceId).orElseThrow(() -> new BadRequestException("Unknown source id"));
        if (user.role == AppUser.Role.ADMIN) {
            return;
        }
        if (!bridge.userId.equals(user.id)) {
            throw new ForbiddenException("You do not have access to that bridge");
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
