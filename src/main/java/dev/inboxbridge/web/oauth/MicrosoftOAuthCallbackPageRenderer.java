package dev.inboxbridge.web.oauth;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MicrosoftOAuthCallbackPageRenderer {

    public String renderPage(
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
                    .append(OAuthPageSupport.escapeHtml(entry.getKey()))
                    .append("""
                    </div>
                      <pre>""")
                    .append(OAuthPageSupport.escapeHtml(entry.getValue()))
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
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Exchange Code In Browser", "Trocar codigo no browser")),
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Copy Authorization Code", "Copiar codigo de autorizacao")),
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Cancel automatic redirect", "Cancelar redirecionamento automatico")),
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Return to InboxBridge", "Voltar ao InboxBridge")),
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Exchange Result", "Resultado da troca")),
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Env Assignment", "Atribuicao de ambiente")),
                    OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Copy Env Assignment", "Copiar atribuicao de ambiente")),
                    OAuthPageSupport.jsString(sourceId),
                    OAuthPageSupport.jsString(state),
                    OAuthPageSupport.jsString(configKey),
                    OAuthPageSupport.jsString(code),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Redirecting to InboxBridge in ", "A redirecionar para o InboxBridge em ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, " seconds.", " segundos.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Redirecting to InboxBridge now...", "A redirecionar para o InboxBridge agora...")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Automatic redirect canceled. You can stay on this page and inspect the exchange details.", "Redirecionamento automatico cancelado. Pode permanecer nesta pagina e verificar os detalhes da troca.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, " copied to clipboard.", " copiado para a area de transferencia.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Authorization code", "Codigo de autorizacao")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Copy the authorization code manually and press Cmd+C, then Enter.", "Copie manualmente o codigo de autorizacao e prima Cmd+C, depois Enter.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Clipboard access was blocked by the browser. A manual copy dialog was opened with the authorization code.", "O acesso a area de transferencia foi bloqueado pelo browser. Foi aberta uma janela para copiar manualmente o codigo de autorizacao.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "No authorization code is available to copy from this callback URL.", "Nao existe nenhum codigo de autorizacao disponivel para copiar deste URL de retorno.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Env assignment", "Atribuicao de ambiente")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Copy the env assignment manually and press Cmd+C, then Enter.", "Copie manualmente a atribuicao de ambiente e prima Cmd+C, depois Enter.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Clipboard access was blocked by the browser. A manual copy dialog was opened with the env assignment.", "O acesso a area de transferencia foi bloqueado pelo browser. Foi aberta uma janela para copiar manualmente a atribuicao de ambiente.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "No env assignment is available to copy yet.", "Ainda nao existe nenhuma atribuicao de ambiente disponivel para copiar.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Microsoft OAuth is still missing one or more required permissions. Retry the flow and approve every requested mailbox permission, then try again. Details: ", "Ainda falta uma ou mais permissoes obrigatorias no Microsoft OAuth. Repita o processo e aceite todas as permissoes pedidas da caixa de correio. Detalhes: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchange failed with status ", "A troca do codigo falhou com o estado ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, ". Check the server logs and Microsoft app settings.", ". Verifique os logs do servidor e as definicoes da aplicacao Microsoft.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchange failed: ", "A troca do codigo falhou: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Microsoft OAuth returned ", "O Microsoft OAuth devolveu ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "No authorization code was present in the callback URL.", "Nao foi encontrado qualquer codigo de autorizacao no URL de retorno.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "The callback state is missing or expired. Start the Microsoft OAuth flow again from InboxBridge.", "O estado do retorno esta em falta ou expirou. Inicie novamente o fluxo Microsoft OAuth a partir do InboxBridge.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchanging authorization code...", "A trocar o codigo de autorizacao...")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Source ID: ", "ID da conta: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Credential Key: ", "Chave da credencial: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Storage: ", "Armazenamento: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Secure storage", "Armazenamento seguro")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Environment fallback", "Fallback por ambiente")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Scope: ", "Escopo: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Token Type: ", "Tipo de token: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Access Token Expires At: ", "Token de acesso expira em: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Next Step: ", "Proximo passo: ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchange completed, but this env-managed source cannot poll yet. Copy the env assignment into your local .env, restart InboxBridge, and then polling will be able to use the new refresh token.", "Troca concluida, mas esta conta gerida por ambiente ainda nao pode ser verificada. Copie a atribuicao para o seu .env local, reinicie o InboxBridge e a verificacao podera usar o novo refresh token.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchange completed. The refresh token was stored securely and future renewals will be automatic.", "Troca concluida. O refresh token foi guardado de forma segura e as futuras renovacoes serao automaticas.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchange failed. Check the server logs and Microsoft app settings.", "A troca do codigo falhou. Verifique os logs do servidor e as definicoes da aplicacao Microsoft.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Microsoft OAuth returned ", "O Microsoft OAuth devolveu ")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Authorization code received. Attempting automatic exchange...", "Codigo de autorizacao recebido. A tentar a troca automatica...")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Authorization code received, but callback state is missing. Start the Microsoft OAuth flow again if automatic exchange does not work.", "Codigo de autorizacao recebido, mas o estado do retorno esta em falta. Inicie novamente o fluxo Microsoft OAuth se a troca automatica nao funcionar.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Leave this page without exchanging the code? If you continue, you must copy the code and handle the token exchange manually later.", "Sair desta pagina sem trocar o codigo? Se continuar, tera de copiar o codigo e tratar manualmente da troca do token mais tarde.")),
                    OAuthPageSupport.jsString(OAuthPageSupport.localized(language, "Exchange the code in the browser before leaving, or copy it now and complete the token exchange manually later.", "Troque o codigo no browser antes de sair, ou copie-o agora e conclua manualmente a troca do token mais tarde."))
            );
        }

        String auxiliaryActionsHtml = "";
        if (browserExchangeHtml.isBlank()) {
            auxiliaryActionsHtml = """
                    <div class="actions">
                      <a class="button-link" href="/">%s</a>
                    </div>
                    """.formatted(OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "Return to InboxBridge", "Voltar ao InboxBridge")));
        }

        return """
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
                """.formatted(
                OAuthPageSupport.escapeHtml(language),
                OAuthPageSupport.escapeHtml(title),
                accentColor,
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, statusTone, "Success".equals(statusTone) ? "Sucesso" : "Atencao")),
                OAuthPageSupport.escapeHtml(title),
                OAuthPageSupport.escapeHtml(message.strip()),
                fieldsHtml,
                browserExchangeHtml,
                auxiliaryActionsHtml,
                OAuthPageSupport.escapeHtml(OAuthPageSupport.localized(language, "You can close this tab after the exchange succeeds and any remaining setup is complete.", "Pode fechar este separador depois de a troca ser concluida com sucesso e de qualquer configuracao restante ficar concluida."))
        );
    }
}
