package dev.connexa.inboxbridge.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.connexa.inboxbridge.dto.MicrosoftOAuthCodeRequest;
import dev.connexa.inboxbridge.dto.MicrosoftOAuthSourceOption;
import dev.connexa.inboxbridge.dto.MicrosoftTokenExchangeResponse;
import dev.connexa.inboxbridge.dto.OAuthUrlResponse;
import dev.connexa.inboxbridge.service.MicrosoftOAuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
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

    @GET
    @Path("/url")
    public OAuthUrlResponse authorizationUrl(@QueryParam("sourceId") String sourceId) {
        try {
            return new OAuthUrlResponse(microsoftOAuthService.buildAuthorizationUrl(sourceId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/start")
    public Response start(@QueryParam("sourceId") String sourceId) {
        try {
            return Response.seeOther(URI.create(microsoftOAuthService.buildAuthorizationUrl(sourceId))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/sources")
    public List<MicrosoftOAuthSourceOption> sources() {
        return microsoftOAuthService.listMicrosoftOAuthSources();
    }

    @POST
    @Path("/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    public MicrosoftTokenExchangeResponse exchange(MicrosoftOAuthCodeRequest request) {
        try {
            return microsoftOAuthService.exchangeAuthorizationCode(request.sourceId(), request.code());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage(), e);
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
        if (error != null && !error.isBlank()) {
            return htmlCallbackPage(
                    "Microsoft OAuth Error",
                    """
                    The Microsoft authorization step failed.
                    """,
                    Map.of(
                            "Error", error,
                            "Description", errorDescription == null ? "" : errorDescription),
                    false,
                    null,
                    null,
                    null);
        }

        MicrosoftOAuthService.CallbackValidation callbackValidation;
        try {
            callbackValidation = microsoftOAuthService.validateCallback(state);
        } catch (IllegalArgumentException e) {
            return htmlCallbackPage(
                    "Invalid OAuth State",
                    """
                    The callback state was missing or expired. Start the Microsoft OAuth flow again from the helper page.
                    """,
                    Map.of(
                            "Code", code == null ? "" : code,
                            "Error", e.getMessage()),
                    false,
                    null,
                    null,
                    null);
        }

        return htmlCallbackPage(
                "Microsoft OAuth Code Received",
                """
                You can exchange this authorization code directly in the browser. If secure token storage is configured, InboxBridge will save it encrypted in PostgreSQL and renew access tokens automatically. Otherwise this page will show the env assignment to copy.
                """,
                orderedFields(
                        "Source ID", callbackValidation.sourceId(),
                        "Config Key", callbackValidation.configKey(),
                        "Authorization Code", code == null ? "" : code,
                        "Exchange Endpoint", "POST /api/microsoft-oauth/exchange"),
                true,
                callbackValidation.sourceId(),
                callbackValidation.configKey(),
                code == null ? "" : code);
    }

    private Response htmlCallbackPage(
            String title,
            String message,
            Map<String, String> fields,
            boolean success,
            String sourceId,
            String configKey,
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
        if (success && sourceId != null && configKey != null && code != null && !code.isBlank()) {
            browserExchangeHtml = """
                    <div class="exchange-card">
                      <div class="actions">
                        <button class="primary" id="exchangeButton" type="button">Exchange Code In Browser</button>
                        <button class="secondary" id="copyCodeButton" type="button">Copy Authorization Code</button>
                      </div>
                      <div class="status" id="exchangeStatus"></div>
                      <div class="field hidden" id="resultField">
                        <div class="label">Exchange Result</div>
                        <pre id="resultValue"></pre>
                      </div>
                      <div class="field hidden" id="envField">
                        <div class="label">Env Assignment</div>
                        <pre id="envAssignmentValue"></pre>
                        <div class="actions compact">
                          <button class="secondary" id="copyEnvAssignmentButton" type="button">Copy Env Assignment</button>
                        </div>
                      </div>
                    </div>
                    <script>
                      const exchangeButton = document.getElementById("exchangeButton");
                      const copyCodeButton = document.getElementById("copyCodeButton");
                      const copyEnvAssignmentButton = document.getElementById("copyEnvAssignmentButton");
                      const exchangeStatus = document.getElementById("exchangeStatus");
                      const resultField = document.getElementById("resultField");
                      const resultValue = document.getElementById("resultValue");
                      const envField = document.getElementById("envField");
                      const envAssignmentValue = document.getElementById("envAssignmentValue");
                      const oauthSourceId = %s;
                      const oauthConfigKey = %s;
                      const oauthCode = %s;

                      async function copyText(text, label) {
                        await navigator.clipboard.writeText(text);
                        exchangeStatus.textContent = label + " copied to clipboard.";
                      }

                      copyCodeButton?.addEventListener("click", async () => {
                        try {
                          await copyText(oauthCode, "Authorization code");
                        } catch (error) {
                          exchangeStatus.textContent = "Unable to copy the authorization code automatically.";
                        }
                      });

                      copyEnvAssignmentButton?.addEventListener("click", async () => {
                        try {
                          await copyText(envAssignmentValue.textContent, "Env assignment");
                        } catch (error) {
                          exchangeStatus.textContent = "Unable to copy the env assignment automatically.";
                        }
                      });

                      exchangeButton?.addEventListener("click", async () => {
                        exchangeButton.disabled = true;
                        exchangeStatus.textContent = "Exchanging authorization code...";
                        try {
                          const response = await fetch("/api/microsoft-oauth/exchange", {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ sourceId: oauthSourceId, code: oauthCode })
                          });

                          const payloadText = await response.text();
                          if (!response.ok) {
                            exchangeStatus.textContent = "Exchange failed: " + payloadText;
                            return;
                          }

                          const payload = JSON.parse(payloadText);
                          const details = [
                            "Source ID: " + (payload.sourceId || oauthSourceId),
                            "Credential Key: " + (payload.credentialKey || ""),
                            "Storage: " + (payload.storedInDatabase ? "Encrypted database" : "Environment fallback"),
                            "Scope: " + (payload.scope || ""),
                            "Token Type: " + (payload.tokenType || ""),
                            "Access Token Expires At: " + (payload.accessTokenExpiresAt || ""),
                            "Next Step: " + (payload.nextStep || "")
                          ].join("\\n");
                          resultValue.textContent = details;
                          resultField.classList.remove("hidden");

                          if (payload.usingEnvironmentFallback && payload.refreshToken) {
                            envAssignmentValue.textContent = oauthConfigKey + "=" + payload.refreshToken;
                            envField.classList.remove("hidden");
                            exchangeStatus.textContent = "Exchange completed. Copy the env assignment into your local .env and restart InboxBridge.";
                          } else {
                            envField.classList.add("hidden");
                            envAssignmentValue.textContent = "";
                            exchangeStatus.textContent = "Exchange completed. The refresh token was stored encrypted in PostgreSQL and future renewals will be automatic.";
                          }
                        } catch (error) {
                          exchangeStatus.textContent = "Exchange failed. Check the server logs and Microsoft app settings.";
                        } finally {
                          exchangeButton.disabled = false;
                        }
                      });
                    </script>
                    """.formatted(jsString(sourceId), jsString(configKey), jsString(code));
        }

        String html = """
                <!doctype html>
                <html lang="en">
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
                    button {
                      border: none;
                      border-radius: 999px;
                      padding: 13px 18px;
                      font: inherit;
                      font-weight: 700;
                      cursor: pointer;
                    }
                    button.primary {
                      color: white;
                      background: linear-gradient(135deg, var(--accent), #155f40);
                    }
                    button.secondary {
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
                      <div class="hint">You can close this tab after the exchange succeeds and any remaining setup is complete.</div>
                    </section>
                  </main>
                </body>
                </html>
                """
                .formatted(
                        escapeHtml(title),
                        accentColor,
                        escapeHtml(statusTone),
                        escapeHtml(title),
                        escapeHtml(message.strip()),
                        fieldsHtml,
                        browserExchangeHtml);

        return Response.ok(html, MediaType.TEXT_HTML).build();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String jsString(String value) {
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
}
