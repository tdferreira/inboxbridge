package dev.inboxbridge.web;

import dev.inboxbridge.dto.GoogleOAuthCodeRequest;
import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.dto.OAuthUrlResponse;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAdmin;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.service.GoogleOAuthService;
import dev.inboxbridge.service.UserGmailConfigService;
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
        if (error != null && !error.isBlank()) {
            return errorPage(
                    language,
                    localized(language, googleErrorTitle(error), "access_denied".equalsIgnoreCase(error) ? "Permissao necessaria no Google OAuth" : "Erro de Google OAuth"),
                    localized(language, googleErrorMessage(error, errorDescription), localizeGoogleErrorMessage(error, errorDescription)),
                    error,
                    errorDescription);
        }
        GoogleOAuthService.CallbackValidation callbackValidation = null;
        String statusMessage = localized(language,
                "Use the button below to exchange the code. If secure token storage is configured, InboxBridge will store the token encrypted in PostgreSQL.",
                "Use o botao abaixo para trocar o codigo. Se o armazenamento seguro de tokens estiver configurado, o InboxBridge vai guardar o token de forma encriptada em PostgreSQL.");
        if (state != null && !state.isBlank()) {
            try {
                callbackValidation = googleOAuthService.validateCallback(state);
                language = callbackValidation.language();
                statusMessage = localized(language,
                        "Use the button below to exchange the code for " + callbackValidation.targetLabel() + ". If secure token storage is configured, InboxBridge will store the token encrypted in PostgreSQL.",
                        "Use o botao abaixo para trocar o codigo de " + callbackValidation.targetLabel() + ". Se o armazenamento seguro de tokens estiver configurado, o InboxBridge vai guardar o token de forma encriptada em PostgreSQL.");
            } catch (IllegalArgumentException e) {
                statusMessage = localized(language,
                        "The Google OAuth state is missing or expired. Start the flow again from the admin UI.",
                        "O estado do Google OAuth esta em falta ou expirou. Inicie novamente o fluxo a partir da interface de administracao.");
            }
        }
        String safeCode = code == null ? "" : code.replace("\\", "\\\\").replace("'", "\\'");
        String safeState = state == null ? "" : state.replace("\\", "\\\\").replace("'", "\\'");
        return """
                <!doctype html>
                <html lang="%s">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { font-family: "IBM Plex Sans", "Avenir Next", sans-serif; margin: 0; background: linear-gradient(160deg, #f7f1e8, #ece5d8); color: #241a14; padding: 24px; }
                    main { max-width: 760px; margin: 0 auto; background: rgba(255,255,255,0.92); border: 1px solid rgba(36,26,20,0.12); border-radius: 24px; padding: 28px; box-shadow: 0 20px 60px rgba(36,26,20,0.12); display: grid; gap: 16px; }
                    h1 { margin: 0; }
                    pre { margin: 0; white-space: pre-wrap; word-break: break-word; padding: 14px 16px; border-radius: 16px; background: rgba(255,255,255,0.8); border: 1px solid rgba(36,26,20,0.12); font-family: "JetBrains Mono", monospace; }
                    .actions { display: flex; flex-wrap: wrap; gap: 12px; }
                    button, a.button-link { border: none; border-radius: 999px; padding: 13px 18px; font: inherit; font-weight: 700; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; justify-content: center; }
                    button { color: white; background: linear-gradient(135deg, #17654b, #0f4c38); }
                    a.button-link { color: #241a14; background: rgba(255,255,255,0.9); border: 1px solid rgba(36,26,20,0.12); }
                    .status { color: #66584d; min-height: 1.4em; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>%s</h1>
                    <p>%s</p>
                    <pre id="codeValue"></pre>
                    <div class="actions">
                      <button id="copyCodeButton" type="button">%s</button>
                      <button id="exchangeButton" type="button">%s</button>
                      <a class="button-link" id="returnLink" href="/">%s</a>
                    </div>
                    <pre id="resultValue" hidden></pre>
                    <div class="status" id="status"></div>
                    <div class="status" id="redirectStatus"></div>
                  </main>
                  <script>
                    const text = {
                      noCodeReceived: %s,
                      returningPrefix: %s,
                      returningSuffix: %s,
                      returningNow: %s,
                      noCodeToCopy: %s,
                      codeCopied: %s,
                      copyFailed: %s,
                      permissionMissingPrefix: %s,
                      exchangeFailedPrefix: %s,
                      oauthReturnedPrefix: %s,
                      noCodeInUrl: %s,
                      exchanging: %s,
                      storageLabel: %s,
                      encryptedDatabase: %s,
                      environmentFallback: %s,
                      previousAccountReplacedLabel: %s,
                      sameAccountLabel: %s,
                      previousGrantRevokedLabel: %s,
                      yes: %s,
                      no: %s,
                      credentialKeyLabel: %s,
                      scopeLabel: %s,
                      tokenTypeLabel: %s,
                      accessExpiresAtLabel: %s,
                      nextStepLabel: %s,
                      refreshTokenLabel: %s,
                      successSameDb: %s,
                      successReplacedDbRevoked: %s,
                      successReplacedDbManualCleanup: %s,
                      successStoredDb: %s,
                      successSameEnv: %s,
                      successReplacedEnv: %s,
                      successStoredEnv: %s,
                      exchangeFailedGeneric: %s,
                      autoAttempting: %s,
                      missingState: %s,
                      beforeUnload: %s,
                      leaveConfirm: %s,
                      stayMessage: %s
                    };
                    const serverRenderedCode = '%s';
                    const serverRenderedState = '%s';
                    const callbackParams = new URLSearchParams(window.location.search);
                    const oauthCode = callbackParams.get('code') || serverRenderedCode;
                    const oauthState = callbackParams.get('state') || serverRenderedState;
                    const oauthError = callbackParams.get('error') || '';
                    const oauthErrorDescription = callbackParams.get('error_description') || '';
                    let exchanged = false;
                    let exchangeAttempted = false;
                    let allowLeave = false;
                    let redirectTimerId = null;
                    let countdownIntervalId = null;
                    document.getElementById('codeValue').textContent = oauthCode || text.noCodeReceived;
                    const status = document.getElementById('status');
                    const redirectStatus = document.getElementById('redirectStatus');
                    const resultValue = document.getElementById('resultValue');
                    const returnLink = document.getElementById('returnLink');
                    function startAutoReturn() {
                      if (redirectTimerId) {
                        window.clearTimeout(redirectTimerId);
                      }
                      if (countdownIntervalId) {
                        window.clearInterval(countdownIntervalId);
                      }
                      let secondsRemaining = 5;
                      const updateCountdown = () => {
                        redirectStatus.textContent = text.returningPrefix + secondsRemaining + text.returningSuffix;
                      };
                      updateCountdown();
                      countdownIntervalId = window.setInterval(() => {
                        secondsRemaining -= 1;
                        if (secondsRemaining <= 0) {
                          window.clearInterval(countdownIntervalId);
                          countdownIntervalId = null;
                          redirectStatus.textContent = text.returningNow;
                          return;
                        }
                        updateCountdown();
                      }, 1000);
                      redirectTimerId = window.setTimeout(() => {
                        window.location.assign('/');
                      }, 5000);
                    }
                    document.getElementById('copyCodeButton').addEventListener('click', async () => {
                      if (!oauthCode) {
                        status.textContent = text.noCodeToCopy;
                        return;
                      }
                      try {
                        if (navigator.clipboard && navigator.clipboard.writeText) {
                          await navigator.clipboard.writeText(oauthCode);
                        } else {
                          const textarea = document.createElement('textarea');
                          textarea.value = oauthCode;
                          textarea.setAttribute('readonly', '');
                          textarea.style.position = 'absolute';
                          textarea.style.left = '-9999px';
                          document.body.appendChild(textarea);
                          textarea.select();
                          document.execCommand('copy');
                          document.body.removeChild(textarea);
                        }
                        status.textContent = text.codeCopied;
                      } catch (error) {
                        status.textContent = text.copyFailed;
                      }
                    });
                    function formatExchangeError(payloadText) {
                      const normalized = (payloadText || '').toLowerCase();
                      if (
                        normalized.includes('access_denied') ||
                        normalized.includes('did not grant all required permissions') ||
                        normalized.includes('did not return a refresh token') ||
                        normalized.includes('offline access is granted') ||
                        normalized.includes('missing scopes')
                      ) {
                        return text.permissionMissingPrefix + payloadText;
                      }
                      return text.exchangeFailedPrefix + payloadText;
                    }
                    async function exchangeCode() {
                      if (!oauthCode) {
                        status.textContent = oauthError
                          ? text.oauthReturnedPrefix + oauthError + (oauthErrorDescription ? ': ' + oauthErrorDescription : '.')
                          : text.noCodeInUrl;
                        return;
                      }
                      if (exchangeAttempted) {
                        return;
                      }
                      exchangeAttempted = true;
                      status.textContent = text.exchanging;
                      try {
                        const response = await fetch('/api/google-oauth/exchange', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ code: oauthCode, state: oauthState })
                        });
                        const payloadText = await response.text();
                        if (!response.ok) {
                          status.textContent = formatExchangeError(payloadText);
                          return;
                        }
                        const payload = JSON.parse(payloadText);
                        resultValue.hidden = false;
                        resultValue.textContent = [
                          text.storageLabel + (payload.storedInDatabase ? text.encryptedDatabase : text.environmentFallback),
                          text.previousAccountReplacedLabel + (payload.replacedExistingAccount ? text.yes : text.no),
                          text.sameAccountLabel + (payload.sameLinkedAccount ? text.yes : text.no),
                          text.previousGrantRevokedLabel + (payload.previousGrantRevoked ? text.yes : text.no),
                          text.credentialKeyLabel + (payload.credentialKey || ''),
                          text.scopeLabel + (payload.scope || ''),
                          text.tokenTypeLabel + (payload.tokenType || ''),
                          text.accessExpiresAtLabel + (payload.accessTokenExpiresAt || ''),
                          text.nextStepLabel + (payload.nextStep || ''),
                          payload.refreshToken ? text.refreshTokenLabel + payload.refreshToken : ''
                        ].filter(Boolean).join('\\n');
                        exchanged = true;
                        startAutoReturn();
                        if (payload.storedInDatabase) {
                          if (payload.sameLinkedAccount) {
                            status.textContent = text.successSameDb
                          } else if (payload.replacedExistingAccount) {
                            status.textContent = payload.previousGrantRevoked
                              ? text.successReplacedDbRevoked
                              : text.successReplacedDbManualCleanup
                          } else {
                            status.textContent = text.successStoredDb
                          }
                        } else {
                          status.textContent = payload.sameLinkedAccount
                            ? text.successSameEnv
                            : payload.replacedExistingAccount
                            ? text.successReplacedEnv
                            : text.successStoredEnv
                        }
                      } catch (error) {
                        exchangeAttempted = false;
                        status.textContent = text.exchangeFailedGeneric;
                      }
                    }
                    document.getElementById('exchangeButton').addEventListener('click', exchangeCode);
                    if (oauthError) {
                      status.textContent = text.oauthReturnedPrefix + oauthError + (oauthErrorDescription ? ': ' + oauthErrorDescription : '.');
                    } else if (oauthCode && oauthState) {
                      status.textContent = text.autoAttempting;
                      window.setTimeout(() => {
                        exchangeCode();
                      }, 0);
                    } else if (oauthCode) {
                      status.textContent = text.missingState;
                    }
                    window.addEventListener('beforeunload', (event) => {
                      if (exchanged || allowLeave) {
                        return;
                      }
                      event.preventDefault();
                      event.returnValue = text.beforeUnload;
                    });
                    returnLink.addEventListener('click', (event) => {
                      if (exchanged) {
                        return;
                      }
                      const confirmed = window.confirm(text.leaveConfirm);
                      if (confirmed) {
                        allowLeave = true;
                      }
                      if (!confirmed) {
                        event.preventDefault();
                        status.textContent = text.stayMessage;
                      }
                    });
                  </script>
                </body>
                </html>
                """.formatted(
                escapeHtml(language),
                escapeHtml(localized(language, "Google OAuth Callback", "Retorno do Google OAuth")),
                escapeHtml(localized(language, "Google OAuth Code Received", "Codigo do Google OAuth recebido")),
                statusMessage.replace("%", "%%"),
                escapeHtml(localized(language, "Copy Code", "Copiar codigo")),
                escapeHtml(localized(language, "Exchange Code In Browser", "Trocar codigo no browser")),
                escapeHtml(localized(language, "Return To Admin UI", "Voltar a interface de administracao")),
                js(localized(language, "(no authorization code received)", "(nenhum codigo de autorizacao recebido)")),
                js(localized(language, "Returning to the admin UI in ", "A voltar para a interface de administracao em ")),
                js(localized(language, " seconds.", " segundos.")),
                js(localized(language, "Returning to the admin UI now...", "A voltar para a interface de administracao agora...")),
                js(localized(language, "No authorization code is available to copy from this callback URL.", "Nao existe nenhum codigo de autorizacao disponivel para copiar deste URL de retorno.")),
                js(localized(language, "Authorization code copied to clipboard.", "Codigo de autorizacao copiado para a area de transferencia.")),
                js(localized(language, "Unable to copy automatically. Copy the code manually before leaving this page.", "Nao foi possivel copiar automaticamente. Copie o codigo manualmente antes de sair desta pagina.")),
                js(localized(language, "Google OAuth is still missing one or more required permissions. Retry the flow and approve every requested Gmail permission, then try again. Details: ", "Ainda falta uma ou mais permissoes obrigatorias no Google OAuth. Repita o processo e aceite todas as permissoes pedidas do Gmail. Detalhes: ")),
                js(localized(language, "Exchange failed: ", "A troca do codigo falhou: ")),
                js(localized(language, "OAuth returned ", "O OAuth devolveu ")),
                js(localized(language, "No authorization code was present in the callback URL.", "Nao foi encontrado qualquer codigo de autorizacao no URL de retorno.")),
                js(localized(language, "Exchanging authorization code...", "A trocar o codigo de autorizacao...")),
                js(localized(language, "Storage: ", "Armazenamento: ")),
                js(localized(language, "Encrypted database", "Base de dados encriptada")),
                js(localized(language, "Environment fallback", "Fallback por ambiente")),
                js(localized(language, "Previous Gmail account replaced: ", "Conta Gmail anterior substituida: ")),
                js(localized(language, "Same Gmail account reauthorized: ", "Mesma conta Gmail reautorizada: ")),
                js(localized(language, "Previous Google grant revoked: ", "Permissao Google anterior revogada: ")),
                js(localized(language, "Yes", "Sim")),
                js(localized(language, "No", "Nao")),
                js(localized(language, "Credential Key: ", "Chave da credencial: ")),
                js(localized(language, "Scope: ", "Escopo: ")),
                js(localized(language, "Token Type: ", "Tipo de token: ")),
                js(localized(language, "Access Token Expires At: ", "Token de acesso expira em: ")),
                js(localized(language, "Next Step: ", "Proximo passo: ")),
                js(localized(language, "Refresh Token: ", "Refresh token: ")),
                js(localized(language, "Exchange completed. The same Gmail account was reauthorized, so the existing Google grant was kept in place.", "Troca concluida. A mesma conta Gmail foi reautorizada, por isso a permissao Google existente foi mantida.")),
                js(localized(language, "Exchange completed. Token stored encrypted in PostgreSQL. The previously linked Gmail account was automatically unlinked and its Google grant was revoked.", "Troca concluida. O token foi guardado de forma encriptada em PostgreSQL. A conta Gmail anteriormente ligada foi desligada automaticamente e a permissao Google foi revogada.")),
                js(localized(language, "Exchange completed. Token stored encrypted in PostgreSQL. The previously linked Gmail account was replaced here, but you may still need to remove the old Google grant manually from myaccount.google.com.", "Troca concluida. O token foi guardado de forma encriptada em PostgreSQL. A conta Gmail anteriormente ligada foi substituida aqui, mas pode ainda ser necessario remover manualmente a permissao Google antiga em myaccount.google.com.")),
                js(localized(language, "Exchange completed. Token stored encrypted in PostgreSQL.", "Troca concluida. O token foi guardado de forma encriptada em PostgreSQL.")),
                js(localized(language, "Exchange completed. The same Gmail account was reauthorized. Copy the refresh token into your local .env if you are using env fallback.", "Troca concluida. A mesma conta Gmail foi reautorizada. Copie o refresh token para o seu .env local se estiver a usar fallback por ambiente.")),
                js(localized(language, "Exchange completed. The previously linked Gmail account was replaced. Copy the new refresh token into your local .env if you are using env fallback.", "Troca concluida. A conta Gmail anteriormente ligada foi substituida. Copie o novo refresh token para o seu .env local se estiver a usar fallback por ambiente.")),
                js(localized(language, "Exchange completed. Copy the refresh token into your local .env if you are using env fallback.", "Troca concluida. Copie o refresh token para o seu .env local se estiver a usar fallback por ambiente.")),
                js(localized(language, "Exchange failed. Check the server logs and OAuth client settings.", "A troca do codigo falhou. Verifique os logs do servidor e as definicoes do cliente OAuth.")),
                js(localized(language, "Authorization code received. Attempting automatic exchange...", "Codigo de autorizacao recebido. A tentar a troca automatica...")),
                js(localized(language, "Authorization code received, but callback state is missing. Use the exchange button if you are completing a manual flow.", "Codigo de autorizacao recebido, mas o estado do retorno esta em falta. Use o botao de troca se estiver a completar um processo manual.")),
                js(localized(language, "Leave this page without exchanging the code? You will need to add it manually later.", "Sair desta pagina sem trocar o codigo? Vai ter de o adicionar manualmente mais tarde.")),
                js(localized(language, "Leave this page without exchanging the code? If you continue, you will need to add it manually from the admin UI later.", "Sair desta pagina sem trocar o codigo? Se continuar, vai ter de o adicionar manualmente mais tarde a partir da interface de administracao.")),
                js(localized(language, "Exchange the code here, or copy it before leaving so you can add it manually later.", "Troque o codigo aqui, ou copie-o antes de sair para o poder adicionar manualmente mais tarde.")),
                safeCode,
                safeState);
    }

    private String errorPage(String language, String title, String message, String error, String errorDescription) {
        return """
                <!doctype html>
                <html lang="%s">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { font-family: "IBM Plex Sans", "Avenir Next", sans-serif; margin: 0; background: linear-gradient(160deg, #f7f1e8, #ece5d8); color: #241a14; padding: 24px; }
                    main { max-width: 760px; margin: 0 auto; background: rgba(255,255,255,0.92); border: 1px solid rgba(36,26,20,0.12); border-radius: 24px; padding: 28px; box-shadow: 0 20px 60px rgba(36,26,20,0.12); display: grid; gap: 16px; }
                    h1 { margin: 0; }
                    pre { margin: 0; white-space: pre-wrap; word-break: break-word; padding: 14px 16px; border-radius: 16px; background: rgba(255,255,255,0.8); border: 1px solid rgba(36,26,20,0.12); font-family: "JetBrains Mono", monospace; }
                    a.button-link { color: #241a14; background: rgba(255,255,255,0.9); border: 1px solid rgba(36,26,20,0.12); border-radius: 999px; padding: 13px 18px; font: inherit; font-weight: 700; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; justify-content: center; width: fit-content; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>%s</h1>
                    <p>%s</p>
                    <pre>%s: %s\n%s: %s</pre>
                    <a class="button-link" href="/">%s</a>
                  </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(language),
                escapeHtml(title),
                escapeHtml(title),
                escapeHtml(message),
                escapeHtml(localized(language, "Error", "Erro")),
                escapeHtml(error == null ? "" : error),
                escapeHtml(localized(language, "Description", "Descricao")),
                escapeHtml(errorDescription == null ? "" : errorDescription),
                escapeHtml(localized(language, "Return To Admin UI", "Voltar a interface de administracao")));
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

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String localized(String language, String english, String portuguese) {
        return OAuthPageI18n.text(language, english);
    }

    private String resolveCallbackLanguage(String state) {
        try {
            return googleOAuthService.validateCallback(state).language();
        } catch (Exception ignored) {
            return "en";
        }
    }

    private String js(String value) {
        return "'" + escapeJs(value) + "'";
    }

    private String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }
}
