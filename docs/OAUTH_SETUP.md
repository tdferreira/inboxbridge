# OAuth setup

This document covers both:

- Gmail OAuth for Gmail destination accounts
- Microsoft OAuth for Outlook source accounts
- Microsoft OAuth for Outlook destination accounts that use IMAP APPEND

The preferred local callback origin is the HTTPS admin UI:

- Google: `https://localhost:3000/api/google-oauth/callback`
- Microsoft: `https://localhost:3000/api/microsoft-oauth/callback`

If your deployment runs on another host or port, set `PUBLIC_HOSTNAME` and
`PUBLIC_PORT`. InboxBridge will derive the default browser callback URLs from
that pair unless you explicitly override `PUBLIC_BASE_URL`,
`GOOGLE_REDIRECT_URI`, or `MICROSOFT_REDIRECT_URI`.

For self-hosted LAN or Tailscale deployments that still use the generated local certificates:

- make sure the callback hostname is covered by the generated SAN list (`PUBLIC_BASE_URL` or the derived `PUBLIC_HOSTNAME` / `PUBLIC_PORT` base URL, plus optional `TLS_FRONTEND_CERT_HOSTNAMES` / `TLS_BACKEND_CERT_HOSTNAMES`)
- trust `certs/ca.crt` on the browser/device opening the callback page
- prefer one canonical hostname if you also want passkeys/WebAuthn to work on the same deployment

When you expose InboxBridge on a LAN, tailnet, or public host, make the callback origin a real hostname covered by your certificate whenever possible. OAuth callbacks can work on HTTPS IP origins more often than passkeys do, but InboxBridge's WebAuthn/passkey support intentionally assumes `localhost` or a real hostname configured through `SECURITY_PASSKEY_RP_ID` and `SECURITY_PASSKEY_ORIGINS`.

Important runtime rule:

- browser-based OAuth exchange now requires `SECURITY_TOKEN_ENCRYPTION_KEY`
- if secure storage is missing, the callback page stops and tells the operator to configure the key and retry instead of offering a weaker fallback path

## Gmail OAuth

InboxBridge can only use Gmail OAuth after this application has been registered in Google Cloud and issued OAuth client credentials.

### Goal

Create OAuth credentials that let InboxBridge import mail into Gmail and manage labels for that destination mailbox.

### Required scopes

- `https://www.googleapis.com/auth/gmail.insert`
- `https://www.googleapis.com/auth/gmail.labels`

Those are the scopes used for the Gmail destination mailbox flow. If a source
email account later adds Google OAuth as its authentication method, that source
flow may require mailbox-read scopes separately because it is authenticating a
different Google account for reading, not the destination Gmail account for
importing.

### High-level steps

1. Create a Google Cloud project
2. Enable the Gmail API
3. Configure the OAuth consent screen
4. Create an OAuth client
5. Add the redirect URI
6. Start the flow from the InboxBridge admin UI

### Can InboxBridge create the Google OAuth client automatically?

No.

InboxBridge cannot auto-create a Google OAuth `client_id` / `client_secret` just because a user signs in with Gmail. Those credentials are issued for a Google Cloud project and must already exist before the OAuth flow starts.

Supported operator patterns are:

1. one shared Google OAuth client for the deployment
2. one Google OAuth client per user

The admin UI therefore shows setup instructions and expects the user or admin to provide the Google client credentials manually.

When a shared Google OAuth client is configured for the deployment, the user Gmail screen can reuse it automatically. In that case:

- `Destination User` should normally be `me`
- `Redirect URI` should normally stay on the prefilled callback URL
- user-specific `Client ID` / `Client Secret` fields are optional overrides

### Redirect URI

Use:

```text
https://localhost:3000/api/google-oauth/callback
```

Or, more generally:

```text
${PUBLIC_BASE_URL}/api/google-oauth/callback
```

### Browser flow

1. Sign in to `https://localhost:3000`
2. If you are an admin, first configure the shared Google `Client ID` / `Client Secret` in `Administration -> OAuth Apps` using the values from the Google Cloud project
3. Then use the destination-mailbox connect action from `My Destination Mailbox` for the mailbox that should receive imported mail
4. Complete Google consent
5. The backend callback validates the provider response and redirects to the
   frontend-owned `/oauth/google/callback` route
6. That frontend callback route automatically tries to exchange the code in the browser as soon as it loads
7. After exchange succeeds, the callback route starts a 5-second countdown and redirects to InboxBridge automatically
8. If the automatic attempt fails, use the callback route button to retry the exchange manually
9. Use `Return to InboxBridge` if you want to go back immediately instead of waiting for the countdown
10. If secure storage is enabled, InboxBridge stores the token securely and renews access automatically
11. After sign-in, the resulting session can be reviewed from `Security -> Sessions`, where InboxBridge now records session type, browser/device hints, Geo-IP, and optional browser-reported location separately

The frontend callback route is fully localized through the admin-ui
translation catalog, while the backend remains the authority for OAuth state
validation and token exchange.

If the deployment already has a shared Google client configured, users can leave the per-user client ID and secret blank and still start the Gmail OAuth flow successfully.

Example manual exchange:

```bash
curl -k -X POST https://localhost:3000/api/google-oauth/exchange \
  -H 'Content-Type: application/json' \
  -d '{"code":"REPLACE_ME","state":"STATE_FROM_CALLBACK"}'
```

### Fix `403: org_internal`

If Google shows:

```text
Error 403: org_internal
```

the OAuth consent screen is limited to a Google Workspace internal audience.

For InboxBridge, the usual fix is:

1. Open `Google Cloud Console > APIs & Services > OAuth consent screen`
2. Change the audience / user type from `Internal` to `External`
3. If the app is still in testing, add the Gmail accounts you want to use as `Test users`
4. Retry the InboxBridge flow

### Important note about refresh tokens

Google reliably returns a refresh token only when the authorization request includes:

- `access_type=offline`
- `prompt=consent`

InboxBridge already includes those.

## Microsoft OAuth for Outlook.com

InboxBridge can only use Microsoft OAuth for Outlook source or destination accounts after this application has been registered in Microsoft Entra and issued app credentials.

Password-based IMAP / POP login is often blocked by Microsoft even when an app password exists. InboxBridge therefore requires OAuth2 + XOAUTH2 for Outlook source email accounts and Outlook destination mailboxes.

### Microsoft account and app registration

You do not need a paid Microsoft Entra SKU just to register an app for a personal Outlook.com mailbox.

Use Microsoft Entra app registration:

1. Sign in to `https://entra.microsoft.com/`
2. Go to `Entra ID > App registrations > New registration`
3. Give the app a meaningful name, such as `InboxBridge Outlook OAuth`
4. Choose supported accounts:
  - recommended for a personal Outlook setup: `Personal Microsoft accounts only`
   - if you want both personal and work/school accounts: `Accounts in any organizational directory and personal Microsoft accounts`
5. Choose redirect URI platform `Web`
6. Add:

```text
https://localhost:3000/api/microsoft-oauth/callback
```

Or, more generally:

```text
${PUBLIC_BASE_URL}/api/microsoft-oauth/callback
```

Official references:

- App registration quickstart
  https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app
- Redirect URI guidance
  https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-redirect-uri

### Add delegated permissions

Add delegated permissions for the protocols you use:

- `https://outlook.office.com/IMAP.AccessAsUser.All`
- `https://outlook.office.com/POP.AccessAsUser.All` if you use POP

InboxBridge also requests `offline_access` so refresh tokens can be issued.

Official reference:

- OAuth for IMAP / POP / SMTP
  https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth

### Find the values for `.env`

From the app registration:

- `Application (client) ID` -> `MICROSOFT_CLIENT_ID`
- `Client secret Value` -> `MICROSOFT_CLIENT_SECRET`

For personal Outlook accounts, prefer:

- `MICROSOFT_TENANT=consumers`

If you intentionally support both personal and work/school accounts:

- `MICROSOFT_TENANT=common`

### Can one Microsoft app be reused for many Outlook accounts?

Yes.

One Microsoft Entra app registration can be reused for multiple Outlook.com / Hotmail / Live accounts, as long as the app registration supports those account types. The app is not tied to the mailbox that created it. Each mailbox still completes consent separately and gets its own refresh token.

### Example `.env` values

```dotenv
MICROSOFT_TENANT=consumers
MICROSOFT_CLIENT_ID=your-microsoft-app-client-id
MICROSOFT_CLIENT_SECRET=your-microsoft-app-client-secret
MICROSOFT_REDIRECT_URI=https://localhost:3000/api/microsoft-oauth/callback
SECURITY_TOKEN_ENCRYPTION_KEY=base64-encoded-32-byte-key
SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
```

### Example Outlook source

```dotenv
MAIL_ACCOUNT_0__ID=outlook-main
MAIL_ACCOUNT_0__ENABLED=true
MAIL_ACCOUNT_0__PROTOCOL=IMAP
MAIL_ACCOUNT_0__HOST=outlook.office365.com
MAIL_ACCOUNT_0__PORT=993
MAIL_ACCOUNT_0__TLS=true
MAIL_ACCOUNT_0__AUTH_METHOD=OAUTH2
MAIL_ACCOUNT_0__OAUTH_PROVIDER=MICROSOFT
MAIL_ACCOUNT_0__USERNAME=you@outlook.com
MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN=
MAIL_ACCOUNT_0__FOLDER=INBOX
MAIL_ACCOUNT_0__CUSTOM_LABEL=Imported/Outlook
```

### Browser flow

For Outlook destination mailboxes configured from `My Destination Mailbox` in the admin UI, the same Microsoft app registration is reused. The user chooses `Outlook` as the destination provider, can save folder-only edits without reconnecting, and uses the Microsoft OAuth action whenever the connected mailbox identity needs to change.

For Outlook source email accounts configured from `My Source Email Accounts`,
the current add/edit dialog can also keep the account in a `save and connect`
flow so the operator finishes the mailbox definition and OAuth consent together.

Regardless of provider, each OAuth-backed source remains bound to its owning
InboxBridge user and destination mailbox. Polling coverage now includes
multi-user integration tests that verify one user's Google/Microsoft-linked
source messages are never imported into another user's destination mailbox.

1. Sign in to `https://localhost:3000`
2. Use the Microsoft OAuth button on the relevant source email account or destination mailbox
3. Complete Microsoft consent
4. The backend callback validates the provider response and redirects to the
   frontend-owned `/oauth/microsoft/callback` route
5. That frontend callback route automatically tries to exchange the code in the browser as soon as it loads
6. After exchange succeeds, the callback route starts a 5-second countdown and redirects to InboxBridge automatically
7. If the automatic attempt fails, use the callback route button to retry the exchange manually
8. Use `Return to InboxBridge` if you want to go back immediately instead of waiting for the countdown
9. If secure storage is missing, the callback route stops the exchange and tells you to configure `SECURITY_TOKEN_ENCRYPTION_KEY`, restart InboxBridge, and retry the OAuth flow
10. When the exchange succeeds, InboxBridge stores the token securely and renews access automatically
11. If you later unlink or replace a Microsoft destination connection, InboxBridge removes its stored tokens but you may still need to remove `InboxBridge` manually from your Microsoft account permissions or My Apps page
12. Session history for the browser sign-in remains visible from `Security -> Sessions`, including session type, browser/device hints, Geo-IP, and any browser-reported location sample the user explicitly shares

The frontend callback route is fully localized through the admin-ui
translation catalog, while the backend remains the authority for OAuth state
validation and token exchange.

Example manual exchange:

```bash
curl -k -X POST https://localhost:3000/api/microsoft-oauth/exchange \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"outlook-main","code":"REPLACE_ME","state":"STATE_FROM_CALLBACK"}'
```

## Secure token storage

Enable secure storage by setting:

- `SECURITY_TOKEN_ENCRYPTION_KEY`
- optionally `SECURITY_TOKEN_ENCRYPTION_KEY_ID`

Generate a key with:

```bash
openssl rand -base64 32
```

With secure storage enabled:

- Google OAuth tokens are stored encrypted
- Microsoft OAuth tokens are stored encrypted
- user-managed Gmail client credentials are stored encrypted
- user-managed source-email-account passwords and refresh tokens are stored encrypted
- browser-exchanged OAuth flows fail closed if secure token storage is not configured, rather than offering a weaker manual fallback for UI-managed secrets

Important nuance:

- secrets are encrypted in the application before storage
- passwords are hashed
- ordinary metadata stays queryable in PostgreSQL

That tradeoff is deliberate and is the practical secure design for this app.

Without secure storage:

- env-managed flows can still fall back to `.env`
- user-managed secret storage is intentionally rejected
