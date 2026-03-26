# OAuth setup

This document covers both:

- Gmail OAuth for destination accounts
- Microsoft OAuth for Outlook / Hotmail / Live source accounts

The preferred local callback origin is the HTTPS admin UI:

- Google: `https://localhost:3000/api/google-oauth/callback`
- Microsoft: `https://localhost:3000/api/microsoft-oauth/callback`

If your deployment runs on another host, set `PUBLIC_BASE_URL` and InboxBridge will use that host for the default browser callback URLs unless you explicitly override `GOOGLE_REDIRECT_URI` or `MICROSOFT_REDIRECT_URI`.

## Gmail OAuth

### Goal

Create OAuth credentials that let InboxBridge import mail into Gmail and manage labels for that destination mailbox.

### Required scopes

- `https://www.googleapis.com/auth/gmail.insert`
- `https://www.googleapis.com/auth/gmail.labels`

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
2. Use `Connect My Gmail OAuth` for a user-managed Gmail destination
3. Or use `Connect System Gmail OAuth` for the env-managed system destination
4. Complete Google consent
5. Use the callback page button to exchange the code
6. After exchange succeeds, the callback page starts a 10-second countdown and returns to the admin UI automatically
7. Use `Return To Admin UI` if you want to go back immediately instead of waiting for the countdown
8. If you try to leave before exchanging, the callback page warns that you must handle the code or token manually later
9. If secure storage is enabled, InboxBridge stores the token encrypted in PostgreSQL

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

Password-based IMAP / POP login is often blocked by Microsoft even when an app password exists. InboxBridge therefore supports OAuth2 + XOAUTH2 for Microsoft sources.

### Microsoft account and app registration

You do not need a paid Microsoft Entra SKU just to register an app for a personal Outlook.com mailbox.

Use Microsoft Entra app registration:

1. Sign in to `https://entra.microsoft.com/`
2. Go to `Entra ID > App registrations > New registration`
3. Give the app a meaningful name, such as `InboxBridge Outlook OAuth`
4. Choose supported accounts:
   - recommended for a personal Outlook / Hotmail / Live setup: `Personal Microsoft accounts only`
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

For personal Outlook / Hotmail / Live accounts, prefer:

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
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY=base64-encoded-32-byte-key
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
```

### Example Outlook source

```dotenv
BRIDGE_SOURCES_0__ID=outlook-main
BRIDGE_SOURCES_0__ENABLED=true
BRIDGE_SOURCES_0__PROTOCOL=IMAP
BRIDGE_SOURCES_0__HOST=outlook.office365.com
BRIDGE_SOURCES_0__PORT=993
BRIDGE_SOURCES_0__TLS=true
BRIDGE_SOURCES_0__AUTH_METHOD=OAUTH2
BRIDGE_SOURCES_0__OAUTH_PROVIDER=MICROSOFT
BRIDGE_SOURCES_0__USERNAME=you@outlook.com
BRIDGE_SOURCES_0__OAUTH_REFRESH_TOKEN=
BRIDGE_SOURCES_0__FOLDER=INBOX
BRIDGE_SOURCES_0__CUSTOM_LABEL=Imported/Outlook
```

### Browser flow

1. Sign in to `https://localhost:3000`
2. Use the Microsoft OAuth button on the relevant bridge
3. Complete Microsoft consent
4. Use the callback page button to exchange the code
5. After exchange succeeds, the callback page starts a 10-second countdown and returns to the admin UI automatically
6. Use `Return To Admin UI` if you want to go back immediately instead of waiting for the countdown
7. If you try to leave before exchanging, the callback page warns that you must handle the code or token manually later
8. If secure storage is enabled, InboxBridge stores the token encrypted in PostgreSQL

Example manual exchange:

```bash
curl -k -X POST https://localhost:3000/api/microsoft-oauth/exchange \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"outlook-main","code":"REPLACE_ME","state":"STATE_FROM_CALLBACK"}'
```

## Secure token storage

Enable secure storage by setting:

- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`
- optionally `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`

Generate a key with:

```bash
openssl rand -base64 32
```

With secure storage enabled:

- Google OAuth tokens are stored encrypted
- Microsoft OAuth tokens are stored encrypted
- user-managed Gmail client credentials are stored encrypted
- user-managed bridge passwords and refresh tokens are stored encrypted

Important nuance:

- secrets are encrypted in the application before storage
- passwords are hashed
- ordinary metadata stays queryable in PostgreSQL

That tradeoff is deliberate and is the practical secure design for this app.

Without secure storage:

- env-managed flows can still fall back to `.env`
- user-managed secret storage is intentionally rejected
