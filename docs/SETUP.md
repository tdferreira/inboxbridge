# Setup Guide

This document is the clearest path to a working local InboxBridge deployment.

## What You Need Installed

- Docker
- Docker Compose
- OpenSSL

Optional but useful:

- a modern browser with passkey/WebAuthn support
- a Google Cloud account if you want Gmail API access
- a Microsoft Entra / Azure account if you want Microsoft OAuth for Outlook source or destination accounts

## Minimum Local Bootstrap

1. Copy the example environment file.
2. Generate an encryption key.
3. Start Docker Compose.

```bash
cp .env.example .env
openssl rand -base64 32
docker compose up --build
```

The admin UI image now skips the full Vitest suite during `docker compose up --build` so the container can be prepared reliably for manual testing even on tighter Docker memory limits. Run the frontend test suite separately from `admin-ui/` before shipping changes, or build the image with `RUN_TESTS=true` if you specifically want the Docker build to enforce it.

At minimum, set these values in `.env`:

```dotenv
JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge
JDBC_USERNAME=inboxbridge
JDBC_PASSWORD=inboxbridge
PUBLIC_BASE_URL=https://localhost:3000
SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
SECURITY_PASSKEY_RP_ID=localhost
SECURITY_PASSKEY_ORIGINS=https://localhost:3000
```

After startup:

- admin UI: `https://localhost:3000`
- backend HTTP: `http://localhost:8080`
- backend HTTPS: `https://localhost:8443`

Bootstrap admin credentials:

- username: `admin`
- password: `nimda`

## Google Cloud Setup For Gmail API

If you want InboxBridge to use the Gmail API, you must first register this application in Google Cloud.

InboxBridge normally uses one shared Google Cloud OAuth client for the whole deployment when Gmail is the destination provider.

That means:

- you do not need one Google Cloud project per Gmail user
- each user still performs their own Gmail OAuth consent
- each user gets their own Gmail refresh token

Create the Google Cloud side like this:

1. Create or choose a Google Cloud project.
2. Enable the Gmail API.
3. Configure the OAuth consent screen.
4. Create a Web OAuth client.
5. Add this redirect URI:

```text
https://localhost:3000/api/google-oauth/callback
```

Or, for a real deployment:

```text
https://<your-domain>/api/google-oauth/callback
```

Then fill these deployment-level values in `.env`:

```dotenv
GOOGLE_CLIENT_ID=replace-me
GOOGLE_CLIENT_SECRET=replace-me
GOOGLE_REDIRECT_URI=https://localhost:3000/api/google-oauth/callback
```

Notes:

- `GOOGLE_REFRESH_TOKEN` is optional in multi-user mode.
- Most users should connect their Gmail account from the admin UI instead of placing a Gmail refresh token in `.env`.
- `GMAIL_DESTINATION_USER` should usually stay `me`.

## Microsoft Setup For Outlook Source And Destination Accounts

If you want InboxBridge to fetch Outlook accounts with OAuth2, or to append imports into an Outlook destination mailbox with Microsoft OAuth2, you must first register this application in Microsoft Entra.

Typical flow:

1. Create or use a Microsoft Entra / Azure account.
2. Register an application.
3. Choose the correct supported account type.
4. Add the web redirect URI:

```text
https://localhost:3000/api/microsoft-oauth/callback
```

5. Create a client secret.
6. Grant the required mailbox OAuth permissions.

Typical `.env` values:

```dotenv
MICROSOFT_TENANT=consumers
MICROSOFT_CLIENT_ID=replace-me
MICROSOFT_CLIENT_SECRET=replace-me
MICROSOFT_REDIRECT_URI=https://localhost:3000/api/microsoft-oauth/callback
```

Notes:

- `consumers` is usually right for Outlook.com / Hotmail / Live accounts.
- one Microsoft app can usually be reused across many personal mailboxes and Outlook destination mailboxes
- each mailbox still grants its own consent
- `SECURITY_TOKEN_ENCRYPTION_KEY` must already be configured before the browser callback page can exchange Google or Microsoft OAuth authorization codes

## Destination Mailbox Options

Users now configure the destination mailbox from `My Destination Mailbox` in the admin UI.

Available choices:

- `Gmail`: Gmail API import with Google OAuth
- `Outlook`: IMAP APPEND with Microsoft OAuth2 only; the UI keeps the Outlook IMAP/XOAUTH2 settings fixed and only asks for the destination folder plus the Microsoft account connection
- `Yahoo Mail`: IMAP APPEND with password or app password
- `Proton Mail Bridge`: IMAP APPEND against the local Proton Bridge endpoint
- `Generic IMAP`: manual IMAP APPEND settings

Rules enforced by the app:

- a source mailbox cannot be the same mailbox as `My Destination Mailbox`
- if changing the destination would make an existing source point to the same mailbox, InboxBridge disables that source automatically until the conflict is resolved
- Gmail destinations are only considered ready after `Save and Authenticate` finishes successfully
- Outlook destinations can save folder-only edits without reconnecting, but changes that affect the connected mailbox identity still require Microsoft OAuth again
- new Outlook source accounts rely on `Save and Connect Microsoft`; the plain `Add` action is hidden until the account is no longer in the first-link flow
- editing an existing source account keeps its provider preset fixed and only shows provider-specific fields such as `Folder` or `Custom Label` when that provider supports them

## Single-User vs Multi-User

Use:

```dotenv
MULTI_USER_ENABLED=true
```

for the normal multi-user mode.

Use:

```dotenv
MULTI_USER_ENABLED=false
```

if you want only the bootstrap admin and do not need user registration or user management.

## Adding Source Email Accounts

You can define source email accounts in two ways:

- in `.env` using `MAIL_ACCOUNT_<n>__...`
- in the admin UI

Example env-managed source:

```dotenv
MAIL_ACCOUNT_0__ID=outlook-main
MAIL_ACCOUNT_0__ENABLED=true
MAIL_ACCOUNT_0__PROTOCOL=IMAP
MAIL_ACCOUNT_0__HOST=outlook.office365.com
MAIL_ACCOUNT_0__PORT=993
MAIL_ACCOUNT_0__TLS=true
MAIL_ACCOUNT_0__AUTH_METHOD=OAUTH2
MAIL_ACCOUNT_0__OAUTH_PROVIDER=MICROSOFT
MAIL_ACCOUNT_0__USERNAME=replace-me@example.com
MAIL_ACCOUNT_0__FOLDER=INBOX
MAIL_ACCOUNT_0__CUSTOM_LABEL=Imported/Outlook
```

If no `MAIL_ACCOUNT_*` values are configured, InboxBridge loads no env-managed source accounts.

## Recommended First Run

1. Boot the stack.
2. Sign in as `admin`.
3. Change the bootstrap password.
4. Connect the destination mailbox from the admin UI.
5. Add one source email account.
6. Complete provider OAuth if needed.
7. Run a manual poll.

## Related Docs

- [`README.md`](../README.md)
- [`OAUTH_SETUP.md`](OAUTH_SETUP.md)
