# Gmail OAuth setup

## Goal

Create OAuth credentials that let InboxBridge import mail into your Gmail account and optionally manage labels.

## High-level steps

1. Create a Google Cloud project
2. Enable the Gmail API
3. Configure the OAuth consent screen
4. Create an OAuth client ID
5. Add your redirect URI
6. Use the helper endpoints in this app to obtain a refresh token
7. Prefer encrypted token storage in PostgreSQL instead of keeping refresh tokens in `.env`

## Suggested scopes

This starter expects:

- `https://www.googleapis.com/auth/gmail.insert`
- `https://www.googleapis.com/auth/gmail.labels`

## Redirect URI

Default local redirect URI:

```text
http://localhost:8080/api/google-oauth/callback
```

If you deploy elsewhere, update:

- your Google OAuth client redirect URI list
- `GOOGLE_REDIRECT_URI`

## Getting a refresh token

1. Start InboxBridge
2. Open `GET /api/google-oauth/url`
3. Login and consent
4. Copy the code from the callback response
5. Call `POST /api/google-oauth/exchange`
6. If `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` is configured, InboxBridge stores the refresh token encrypted in PostgreSQL
7. Otherwise save the returned `refresh_token` into `.env`

## Important note

Google usually only returns a refresh token reliably when the authorization request includes:

- `access_type=offline`
- `prompt=consent`

This starter already includes those in the authorization URL builder.

## Outlook.com OAuth2 setup

If your source mailbox is a personal Microsoft account such as `@outlook.com`, `@hotmail.com`, or `@live.com`, password-based IMAP/POP login may be blocked even when you use an app password. Microsoft documents OAuth2 support for IMAP, POP, and SMTP for both Microsoft 365 and Outlook.com users.

Important clarification:

- Microsoft Entra is not required as a paid SKU for this scenario
- Microsoft Entra ID Free exists at no cost
- Microsoft says it is free, but a credit card may still be required to verify identity for the free Azure account

## Create the Microsoft cloud account and Entra app

If you do not already have an Azure / Microsoft Entra tenant:

1. Create a free Azure account and sign in to the Microsoft Entra admin center at `https://entra.microsoft.com/`
2. Use at least an `Application Developer` role in the target tenant
3. If you have access to multiple tenants, switch to the correct one before creating the app registration

Official references:

- Register an app in Microsoft Entra ID
  https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app
- Application types for Microsoft identity platform
  https://learn.microsoft.com/en-us/entra/identity-platform/v2-app-types

## Register the Outlook OAuth application

1. Open `Microsoft Entra admin center`
2. Go to `Entra ID > App registrations > New registration`
3. Enter a meaningful name such as `InboxBridge Outlook OAuth`
4. Choose the supported account type:
   - recommended for a personal Outlook / Hotmail / Live mailbox used only by you: `Personal Microsoft accounts only`
   - if you want both personal Microsoft accounts and work/school accounts: `Accounts in any organizational directory and personal Microsoft accounts`
5. Under `Redirect URI`, choose platform `Web`
6. Enter:
   - `http://localhost:8080/api/microsoft-oauth/callback`
7. Select `Register`

Why `Web`:

- InboxBridge handles OAuth on the server side
- the app keeps a client secret
- this is a confidential-client flow, not a SPA or native desktop/mobile app

Official reference:

- Add a redirect URI
  https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-redirect-uri

High-level setup for a free personal Outlook.com OAuth app:

1. Create a free Azure account / Microsoft Entra tenant
2. Register a new app in Microsoft Entra
3. Choose `Personal Microsoft accounts only` as the supported account type if this app is only for your own Outlook.com mailbox
4. Add a `Web` redirect URI for your local callback handler:
   - `http://localhost:8080/api/microsoft-oauth/callback`
5. Add delegated permissions for the protocol you need:
   - `https://outlook.office.com/IMAP.AccessAsUser.All`
   - `https://outlook.office.com/POP.AccessAsUser.All`
   - `offline_access` is requested by the app during authorization so refresh tokens can be issued
6. Run an OAuth authorization code flow against the Microsoft identity platform
7. Store the returned refresh token securely
8. Use that refresh token to obtain short-lived access tokens during polling
9. Authenticate to the mail server with SASL `XOAUTH2` instead of username/password login

## Add delegated API permissions

After registering the app:

1. Open your app registration
2. Go to `API permissions`
3. Select `Add a permission`
4. Add the delegated protocol scopes you need:
   - `https://outlook.office.com/IMAP.AccessAsUser.All`
   - `https://outlook.office.com/POP.AccessAsUser.All` if you want POP support
5. Request `offline_access` in the OAuth authorization flow so Microsoft can issue refresh tokens
6. If your tenant requires it, grant admin consent

Practical note:

- for a personal Outlook.com mailbox, IMAP is the recommended path for InboxBridge
- POP permission is only needed if you actually plan to configure a POP source

Official reference:

- Authenticate an IMAP, POP or SMTP connection using OAuth
  https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth

## Find the values needed for `.env`

Once the app registration exists:

1. Open the app registration `Overview` page
2. Copy:
   - `Application (client) ID` -> `MICROSOFT_CLIENT_ID`
   - `Directory (tenant) ID` if you intentionally want to use a specific tenant
3. For personal Outlook.com / Hotmail / Live accounts, prefer:
   - `MICROSOFT_TENANT=consumers`
   instead of the tenant GUID
4. Go to `Certificates & secrets > Client secrets > New client secret`
5. Create a secret, copy the `Value` immediately, and store it as:
   - `MICROSOFT_CLIENT_SECRET`

Important security note:

- Microsoft recommends certificates over client secrets for stronger production security
- for local development and self-hosted personal use, a client secret is the practical starting point
- the client secret value is shown only once

Official reference:

- Add and manage app credentials in Microsoft Entra ID
  https://learn.microsoft.com/en-my/entra/identity-platform/how-to-add-credentials?tabs=client-secret

## Outlook.com env vars

App-wide Microsoft OAuth settings:

- `MICROSOFT_TENANT`
  - use `consumers` for personal Outlook.com / Hotmail / Live accounts
- `MICROSOFT_CLIENT_ID`
- `MICROSOFT_CLIENT_SECRET`
- `MICROSOFT_REDIRECT_URI`
  - default: `http://localhost:8080/api/microsoft-oauth/callback`
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`
  - base64-encoded 32-byte AES key used for encrypted OAuth token storage in PostgreSQL
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`
  - optional key version label, default `v1`

Per-source Microsoft OAuth settings:

- `BRIDGE_SOURCES_<index>__AUTH_METHOD=OAUTH2`
- `BRIDGE_SOURCES_<index>__OAUTH_PROVIDER=MICROSOFT`
- `BRIDGE_SOURCES_<index>__OAUTH_REFRESH_TOKEN=...`

Example Outlook IMAP source:

```dotenv
MICROSOFT_TENANT=consumers
MICROSOFT_CLIENT_ID=your-microsoft-app-client-id
MICROSOFT_CLIENT_SECRET=your-microsoft-app-client-secret
MICROSOFT_REDIRECT_URI=http://localhost:8080/api/microsoft-oauth/callback
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY=base64-encoded-32-byte-key
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1

BRIDGE_SOURCES_0__ID=outlook-main
BRIDGE_SOURCES_0__ENABLED=true
BRIDGE_SOURCES_0__PROTOCOL=IMAP
BRIDGE_SOURCES_0__HOST=outlook.office365.com
BRIDGE_SOURCES_0__PORT=993
BRIDGE_SOURCES_0__TLS=true
BRIDGE_SOURCES_0__AUTH_METHOD=OAUTH2
BRIDGE_SOURCES_0__OAUTH_PROVIDER=MICROSOFT
BRIDGE_SOURCES_0__USERNAME=you@outlook.com
BRIDGE_SOURCES_0__OAUTH_REFRESH_TOKEN=optional-fallback-if-db-encryption-is-disabled
BRIDGE_SOURCES_0__FOLDER=INBOX
BRIDGE_SOURCES_0__UNREAD_ONLY=false
BRIDGE_SOURCES_0__CUSTOM_LABEL=Imported/Outlook
```

Generate a local encryption key with:

```bash
openssl rand -base64 32
```

## Enable secure token storage

Secure token storage is enabled when InboxBridge can read a valid 32-byte base64 AES key from:

- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`

Optional:

- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`
  - defaults to `v1`

Practical steps:

1. Generate a key:

```bash
openssl rand -base64 32
```

2. Put it in your local `.env`:

```dotenv
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY=PASTE_THE_GENERATED_VALUE_HERE
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
```

3. Restart InboxBridge
4. Run the OAuth flow again
5. After the code exchange, InboxBridge will store the refresh token encrypted in PostgreSQL instead of asking you to keep it in `.env`

How to tell it is working:

- the Microsoft exchange response will report database storage
- the helper UI will say the token was stored encrypted in PostgreSQL
- the `oauth_credential` table will contain the encrypted token record

Important limitation:

- this currently secures OAuth tokens only
- mailbox passwords are still env-based unless you extend the project further

Security note:

- authorization codes should not be stored after exchange
- refresh tokens should be stored encrypted at rest
- access tokens are short-lived and can also be stored encrypted together with their expiry metadata to avoid unnecessary refreshes

## Outlook.com helper endpoints

InboxBridge now exposes helper endpoints similar to the Gmail flow:

- `GET /api/microsoft-oauth/url?sourceId=<source-id>`
- `GET /api/microsoft-oauth/start?sourceId=<source-id>`
- `POST /api/microsoft-oauth/exchange`
- `GET /api/microsoft-oauth/callback`
- `GET /oauth/microsoft/`

Suggested flow for a configured Microsoft OAuth source:

1. Start InboxBridge
2. Open `GET /oauth/microsoft/`
3. Sign in with the Outlook.com account and consent
4. After the callback, use the browser button or call `POST /api/microsoft-oauth/exchange`
5. If encrypted token storage is enabled, InboxBridge stores the refresh token in PostgreSQL and no manual token copy is needed
6. Otherwise save the returned `refreshToken` into the suggested `configKey` in `.env`
7. Restart InboxBridge if you are using env fallback

Example exchange request:

```bash
curl -X POST http://localhost:8080/api/microsoft-oauth/exchange \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"outlook-main","code":"REPLACE_ME"}'
```

Implementation status in this repository:

- Microsoft OAuth helper endpoints are implemented
- Microsoft refresh-token exchange is implemented
- source-specific access-token refresh is implemented
- Outlook sources can now authenticate with `XOAUTH2` for IMAP and POP
- encrypted database storage is supported when `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` is configured
- env-based refresh-token fallback is still supported for local setups that are not ready for DB-backed secret storage

Practical recommendation:

- for personal Outlook.com mailboxes, prefer IMAP + OAuth2
- do not switch to POP expecting it to bypass the auth policy
- Microsoft documents the same OAuth model for both IMAP and POP

Official references:

- Microsoft Learn: Authenticate an IMAP, POP or SMTP connection using OAuth
  https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth
- Microsoft Learn: Register an app in Microsoft Entra ID
  https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app
- Microsoft Learn: Add a redirect URI to your application
  https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-redirect-uri
- Microsoft Learn: Add and manage app credentials in Microsoft Entra ID
  https://learn.microsoft.com/en-my/entra/identity-platform/how-to-add-credentials?tabs=client-secret
- Microsoft Learn: Microsoft Entra ID Free
  https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/microsoft-entra-id-free
- Microsoft Learn: Identity and account types for single- and multitenant apps
  https://learn.microsoft.com/en-us/security/zero-trust/develop/identity-supported-account-types
