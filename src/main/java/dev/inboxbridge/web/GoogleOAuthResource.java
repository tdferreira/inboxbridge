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
    public Response startSystem() {
        return Response.seeOther(java.net.URI.create(
                googleOAuthService.buildAuthorizationUrlWithState(
                        googleOAuthService.systemProfileForCallbacks(),
                        "Shared Gmail account"))).build();
    }

    @GET
    @Path("/start/self")
    @RequireAuth
    public Response startSelf() {
        GoogleOAuthService.GoogleOAuthProfile profile = userGmailConfigService.googleProfileForUser(currentUserContext.user().id)
                .orElseThrow(() -> new jakarta.ws.rs.BadRequestException(
                        "Configure your Gmail redirect URI or a shared Google OAuth client before starting Google OAuth."));
        return Response.seeOther(java.net.URI.create(
                googleOAuthService.buildAuthorizationUrlWithState(profile, "User Gmail account"))).build();
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
        if (error != null && !error.isBlank()) {
            return errorPage(
                    googleErrorTitle(error),
                    googleErrorMessage(error, errorDescription),
                    error,
                    errorDescription);
        }
        GoogleOAuthService.CallbackValidation callbackValidation = null;
        String statusMessage = "Use the button below to exchange the code. If secure token storage is configured, InboxBridge will store the token encrypted in PostgreSQL.";
        if (state != null && !state.isBlank()) {
            try {
                callbackValidation = googleOAuthService.validateCallback(state);
                statusMessage = "Use the button below to exchange the code for " + callbackValidation.targetLabel() + ". If secure token storage is configured, InboxBridge will store the token encrypted in PostgreSQL.";
            } catch (IllegalArgumentException e) {
                statusMessage = "The Google OAuth state is missing or expired. Start the flow again from the admin UI.";
            }
        }
        String safeCode = code == null ? "" : code.replace("\\", "\\\\").replace("'", "\\'");
        String safeState = state == null ? "" : state.replace("\\", "\\\\").replace("'", "\\'");
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Google OAuth Callback</title>
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
                    <h1>Google OAuth Code Received</h1>
                    <p>%s</p>
                    <pre id="codeValue"></pre>
                    <div class="actions">
                      <button id="copyCodeButton" type="button">Copy Code</button>
                      <button id="exchangeButton" type="button">Exchange Code In Browser</button>
                      <a class="button-link" id="returnLink" href="/">Return To Admin UI</a>
                    </div>
                    <pre id="resultValue" hidden></pre>
                    <div class="status" id="status"></div>
                    <div class="status" id="redirectStatus"></div>
                  </main>
                  <script>
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
                    document.getElementById('codeValue').textContent = oauthCode || '(no authorization code received)';
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
                      let secondsRemaining = 10;
                      const updateCountdown = () => {
                        redirectStatus.textContent = 'Returning to the admin UI in ' + secondsRemaining + ' seconds.';
                      };
                      updateCountdown();
                      countdownIntervalId = window.setInterval(() => {
                        secondsRemaining -= 1;
                        if (secondsRemaining <= 0) {
                          window.clearInterval(countdownIntervalId);
                          countdownIntervalId = null;
                          redirectStatus.textContent = 'Returning to the admin UI now...';
                          return;
                        }
                        updateCountdown();
                      }, 1000);
                      redirectTimerId = window.setTimeout(() => {
                        window.location.assign('/');
                      }, 10000);
                    }
                    document.getElementById('copyCodeButton').addEventListener('click', async () => {
                      if (!oauthCode) {
                        status.textContent = 'No authorization code is available to copy from this callback URL.';
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
                        status.textContent = 'Authorization code copied to clipboard.';
                      } catch (error) {
                        status.textContent = 'Unable to copy automatically. Copy the code manually before leaving this page.';
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
                        return 'Google OAuth is still missing one or more required permissions. Retry the flow and approve every requested Gmail permission, then try again. Details: ' + payloadText;
                      }
                      return 'Exchange failed: ' + payloadText;
                    }
                    async function exchangeCode() {
                      if (!oauthCode) {
                        status.textContent = oauthError
                          ? 'OAuth returned ' + oauthError + (oauthErrorDescription ? ': ' + oauthErrorDescription : '.')
                          : 'No authorization code was present in the callback URL.';
                        return;
                      }
                      if (exchangeAttempted) {
                        return;
                      }
                      exchangeAttempted = true;
                      status.textContent = 'Exchanging authorization code...';
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
                          'Storage: ' + (payload.storedInDatabase ? 'Encrypted database' : 'Environment fallback'),
                          'Credential Key: ' + (payload.credentialKey || ''),
                          'Scope: ' + (payload.scope || ''),
                          'Token Type: ' + (payload.tokenType || ''),
                          'Access Token Expires At: ' + (payload.accessTokenExpiresAt || ''),
                          'Next Step: ' + (payload.nextStep || ''),
                          payload.refreshToken ? 'Refresh Token: ' + payload.refreshToken : ''
                        ].filter(Boolean).join('\\n');
                        exchanged = true;
                        startAutoReturn();
                        status.textContent = payload.storedInDatabase
                          ? 'Exchange completed. Token stored encrypted in PostgreSQL.'
                          : 'Exchange completed. Copy the refresh token into your local .env if you are using env fallback.';
                      } catch (error) {
                        exchangeAttempted = false;
                        status.textContent = 'Exchange failed. Check the server logs and OAuth client settings.';
                      }
                    }
                    document.getElementById('exchangeButton').addEventListener('click', exchangeCode);
                    if (oauthError) {
                      status.textContent = 'Google OAuth returned ' + oauthError + (oauthErrorDescription ? ': ' + oauthErrorDescription : '.');
                    } else if (oauthCode && oauthState) {
                      status.textContent = 'Authorization code received. Attempting automatic exchange...';
                      window.setTimeout(() => {
                        exchangeCode();
                      }, 0);
                    } else if (oauthCode) {
                      status.textContent = 'Authorization code received, but callback state is missing. Use the exchange button if you are completing a manual flow.';
                    }
                    window.addEventListener('beforeunload', (event) => {
                      if (exchanged || allowLeave) {
                        return;
                      }
                      event.preventDefault();
                      event.returnValue = 'Leave this page without exchanging the code? You will need to add it manually later.';
                    });
                    returnLink.addEventListener('click', (event) => {
                      if (exchanged) {
                        return;
                      }
                      const confirmed = window.confirm('Leave this page without exchanging the code? If you continue, you will need to add it manually from the admin UI later.');
                      if (confirmed) {
                        allowLeave = true;
                      }
                      if (!confirmed) {
                        event.preventDefault();
                        status.textContent = 'Exchange the code here, or copy it before leaving so you can add it manually later.';
                      }
                    });
                  </script>
                </body>
                </html>
                """.formatted(
                statusMessage.replace("%", "%%"),
                safeCode,
                safeState);
    }

    private String errorPage(String title, String message, String error, String errorDescription) {
        return """
                <!doctype html>
                <html lang="en">
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
                    <pre>Error: %s\nDescription: %s</pre>
                    <a class="button-link" href="/">Return To Admin UI</a>
                  </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(title),
                escapeHtml(message),
                escapeHtml(error == null ? "" : error),
                escapeHtml(errorDescription == null ? "" : errorDescription));
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
}
