package dev.inboxbridge.web.oauth;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GoogleOAuthCallbackPageRenderer {

    public String renderCallbackPage(
            String language,
            String statusMessage,
            String code,
            String state,
            String error,
            String errorDescription) {
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
                    button.danger { background: linear-gradient(135deg, #b43a28, #8b2d20); }
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
                      <button id="exchangeButton" type="button">%s</button>
                      <button id="copyCodeButton" type="button">%s</button>
                      <button class="danger" id="cancelReturnButton" type="button" hidden>%s</button>
                      <a class="button-link" id="returnLink" href="/">%s</a>
                    </div>
                    <div class="status" id="copyStatus"></div>
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
                      manualCopyPrompt: %s,
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
                      autoReturnCanceled: %s,
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
                    const copyStatus = document.getElementById('copyStatus');
                    const status = document.getElementById('status');
                    const redirectStatus = document.getElementById('redirectStatus');
                    const resultValue = document.getElementById('resultValue');
                    const cancelReturnButton = document.getElementById('cancelReturnButton');
                    const returnLink = document.getElementById('returnLink');
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
                      cancelReturnButton.hidden = false;
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
                        cancelReturnButton.hidden = true;
                        window.location.assign('/');
                      }, 5000);
                    }
                    function cancelAutoReturn() {
                      clearAutoReturn();
                      allowLeave = true;
                      cancelReturnButton.hidden = true;
                      redirectStatus.textContent = text.autoReturnCanceled;
                    }
                    document.getElementById('copyCodeButton').addEventListener('click', async () => {
                      if (!oauthCode) {
                        copyStatus.textContent = text.noCodeToCopy;
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
                        copyStatus.textContent = text.codeCopied;
                      } catch (copyError) {
                        window.prompt(text.manualCopyPrompt, oauthCode);
                        copyStatus.textContent = text.copyFailed;
                      }
                    });
                    cancelReturnButton.addEventListener('click', cancelAutoReturn);
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
                      } catch (exchangeError) {
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
                OAuthPageSupport.escapeHtml(language),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Google OAuth Callback", "Retorno do Google OAuth")),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Google OAuth Code Received", "Codigo do Google OAuth recebido")),
                statusMessage.replace("%", "%%"),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Exchange Code In Browser", "Trocar codigo no browser")),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Copy Code", "Copiar codigo")),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Cancel automatic redirect", "Cancelar redirecionamento automatico")),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Return to InboxBridge", "Voltar ao InboxBridge")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "(no authorization code received)", "(nenhum codigo de autorizacao recebido)")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Redirecting to InboxBridge in ", "A redirecionar para o InboxBridge em ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, " seconds.", " segundos.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Redirecting to InboxBridge now...", "A redirecionar para o InboxBridge agora...")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "No authorization code is available to copy from this callback URL.", "Nao existe nenhum codigo de autorizacao disponivel para copiar deste URL de retorno.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Authorization code copied to clipboard.", "Codigo de autorizacao copiado para a area de transferencia.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Clipboard access was blocked by the browser. A manual copy dialog was opened with the authorization code.", "O acesso a area de transferencia foi bloqueado pelo browser. Foi aberta uma janela para copiar manualmente o codigo de autorizacao.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Copy the authorization code manually and press Cmd+C, then Enter.", "Copie manualmente o codigo de autorizacao e prima Cmd+C, depois Enter.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Google OAuth is still missing one or more required permissions. Retry the flow and approve every requested Gmail permission, then try again. Details: ", "Ainda falta uma ou mais permissoes obrigatorias no Google OAuth. Repita o processo e aceite todas as permissoes pedidas do Gmail. Detalhes: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange failed: ", "A troca do codigo falhou: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "OAuth returned ", "O OAuth devolveu ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "No authorization code was present in the callback URL.", "Nao foi encontrado qualquer codigo de autorizacao no URL de retorno.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchanging authorization code...", "A trocar o codigo de autorizacao...")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Storage: ", "Armazenamento: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Encrypted storage", "Armazenamento encriptado")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Environment fallback", "Fallback por ambiente")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Previous Gmail account replaced: ", "Conta Gmail anterior substituida: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Same Gmail account reauthorized: ", "Mesma conta Gmail reautorizada: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Previous Google grant revoked: ", "Permissao Google anterior revogada: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Yes", "Sim")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "No", "Nao")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Credential Key: ", "Chave da credencial: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Scope: ", "Escopo: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Token Type: ", "Tipo de token: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Access Token Expires At: ", "Token de acesso expira em: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Next Step: ", "Proximo passo: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Refresh Token: ", "Refresh token: ")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. The same Gmail account was reauthorized, so the existing Google grant was kept in place.", "Troca concluida. A mesma conta Gmail foi reautorizada, por isso a permissao Google existente foi mantida.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. The token was stored securely. The previously linked Gmail account was automatically unlinked and its Google grant was revoked.", "Troca concluida. O token foi guardado de forma segura. A conta Gmail anteriormente ligada foi desligada automaticamente e a permissao Google foi revogada.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. The token was stored securely. The previously linked Gmail account was replaced here, but you may still need to remove the old Google grant manually from myaccount.google.com.", "Troca concluida. O token foi guardado de forma segura. A conta Gmail anteriormente ligada foi substituida aqui, mas pode ainda ser necessario remover manualmente a permissao Google antiga em myaccount.google.com.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. The token was stored securely.", "Troca concluida. O token foi guardado de forma segura.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. The same Gmail account was reauthorized. Copy the refresh token into your local .env if you are using env fallback.", "Troca concluida. A mesma conta Gmail foi reautorizada. Copie o refresh token para o seu .env local se estiver a usar fallback por ambiente.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. The previously linked Gmail account was replaced. Copy the new refresh token into your local .env if you are using env fallback.", "Troca concluida. A conta Gmail anteriormente ligada foi substituida. Copie o novo refresh token para o seu .env local se estiver a usar fallback por ambiente.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange completed. Copy the refresh token into your local .env if you are using env fallback.", "Troca concluida. Copie o refresh token para o seu .env local se estiver a usar fallback por ambiente.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange failed. Check the server logs and OAuth client settings.", "A troca do codigo falhou. Verifique os logs do servidor e as definicoes do cliente OAuth.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Authorization code received. Attempting automatic exchange...", "Codigo de autorizacao recebido. A tentar a troca automatica...")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Automatic redirect canceled. You can stay on this page and inspect the exchange details.", "Redirecionamento automatico cancelado. Pode permanecer nesta pagina e verificar os detalhes da troca.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Authorization code received, but callback state is missing. Use the exchange button if you are completing a manual flow.", "Codigo de autorizacao recebido, mas o estado do retorno esta em falta. Use o botao de troca se estiver a completar um processo manual.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Leave this page without exchanging the code? You will need to add it manually later.", "Sair desta pagina sem trocar o codigo? Vai ter de o adicionar manualmente mais tarde.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Leave this page without exchanging the code? If you continue, you will need to add it manually from InboxBridge later.", "Sair desta pagina sem trocar o codigo? Se continuar, vai ter de o adicionar manualmente mais tarde a partir do InboxBridge.")),
                OAuthPageSupport.js(OAuthPageSupport.localized(language, "Exchange the code here, or copy it before leaving so you can add it manually later.", "Troque o codigo aqui, ou copie-o antes de sair para o poder adicionar manualmente mais tarde.")),
                safeCode,
                safeState
        );
    }

    public String renderErrorPage(String language, String title, String message, String error, String errorDescription) {
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
                OAuthPageSupport.escapeHtml(language),
                OAuthPageSupport.escapeHtml(title),
                OAuthPageSupport.escapeHtml(title),
                OAuthPageSupport.escapeHtml(message),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Error", "Erro")),
                OAuthPageSupport.escapeHtml(error == null ? "" : error),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Description", "Descricao")),
                OAuthPageSupport.escapeHtml(errorDescription == null ? "" : errorDescription),
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Return to InboxBridge", "Voltar ao InboxBridge"))
        );
    }
}
