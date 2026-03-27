# Setup Guide

This document is the clearest path to a working local InboxBridge deployment.

## What You Need Installed

- Docker
- Docker Compose
- OpenSSL

Optional but useful:

- a modern browser with passkey/WebAuthn support
- a Google Cloud account if you want Gmail API access
- a Microsoft Entra / Azure account if you want Microsoft OAuth for Outlook / Hotmail / Live source accounts

## Minimum Local Bootstrap

1. Copy the example environment file.
2. Generate an encryption key.
3. Start Docker Compose.

```bash
cp .env.example .env
openssl rand -base64 32
docker compose up --build
```

At minimum, set these values in `.env`:

```dotenv
JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge
JDBC_USERNAME=inboxbridge
JDBC_PASSWORD=inboxbridge
PUBLIC_BASE_URL=https://localhost:3000
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
BRIDGE_SECURITY_PASSKEY_RP_ID=localhost
BRIDGE_SECURITY_PASSKEY_ORIGINS=https://localhost:3000
```

After startup:

- admin UI: `https://localhost:3000`
- backend HTTP: `http://localhost:8080`
- backend HTTPS: `https://localhost:8443`

Bootstrap admin credentials:

- username: `admin`
- password: `nimda`

## Google Cloud Setup For Gmail API

InboxBridge normally uses one shared Google Cloud OAuth client for the whole deployment.

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
- `BRIDGE_GMAIL_DESTINATION_USER` should usually stay `me`.

## Microsoft Setup For Outlook / Hotmail / Live Source Accounts

If you want Microsoft OAuth for source accounts, you need a Microsoft Entra app registration.

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
- one Microsoft app can usually be reused across many personal mailboxes
- each mailbox still grants its own consent

## Single-User vs Multi-User

Use:

```dotenv
BRIDGE_MULTI_USER_ENABLED=true
```

for the normal multi-user mode.

Use:

```dotenv
BRIDGE_MULTI_USER_ENABLED=false
```

if you want only the bootstrap admin and do not need user registration or user management.

## Adding Source Email Accounts

You can define source email accounts in two ways:

- in `.env` using `BRIDGE_SOURCES_<n>__...`
- in the admin UI

Example env-managed source:

```dotenv
BRIDGE_SOURCES_0__ID=outlook-main
BRIDGE_SOURCES_0__ENABLED=true
BRIDGE_SOURCES_0__PROTOCOL=IMAP
BRIDGE_SOURCES_0__HOST=outlook.office365.com
BRIDGE_SOURCES_0__PORT=993
BRIDGE_SOURCES_0__TLS=true
BRIDGE_SOURCES_0__AUTH_METHOD=OAUTH2
BRIDGE_SOURCES_0__OAUTH_PROVIDER=MICROSOFT
BRIDGE_SOURCES_0__USERNAME=replace-me@example.com
BRIDGE_SOURCES_0__FOLDER=INBOX
BRIDGE_SOURCES_0__CUSTOM_LABEL=Imported/Outlook
```

If no `BRIDGE_SOURCES_*` values are configured, InboxBridge loads no env-managed source accounts.

## Recommended First Run

1. Boot the stack.
2. Sign in as `admin`.
3. Change the bootstrap password.
4. Connect the Gmail account from the admin UI.
5. Add one source email account.
6. Complete provider OAuth if needed.
7. Run a manual poll.

## Related Docs

- [README.md](/Users/tdferreira/Developer/inboxbridge/README.md)
- [docs/OAUTH_SETUP.md](/Users/tdferreira/Developer/inboxbridge/docs/OAUTH_SETUP.md)
