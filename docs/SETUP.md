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
- a real hostname or additional certificate SANs if you plan to open InboxBridge from other devices on your LAN or tailnet

## Minimum Local Bootstrap

1. Copy the example environment file.
2. Generate an encryption key.
3. Start Docker Compose.

```bash
cp .env.example .env
openssl rand -base64 32
docker compose up --build
```

`docker compose up --build` is the reliable path for rebuilding the stack before manual testing, but the admin UI image no longer runs the full frontend Vitest suite by default during that build because the suite can hang after printing successful output inside containerized builds. Frontend tests still run in CI, and you can opt into in-image frontend tests with `--build-arg RUN_TESTS=true` when you explicitly want that extra validation step.

`.env.example` contains the minimum local bootstrap values uncommented, so
for a first run you normally only need to paste the generated encryption key
into `SECURITY_TOKEN_ENCRYPTION_KEY`.

The minimum local bootstrap values are:

```dotenv
JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge
JDBC_USERNAME=inboxbridge
JDBC_PASSWORD=inboxbridge
PUBLIC_HOSTNAME=localhost
PUBLIC_PORT=3000
SECRET_PROVIDER_MODE=LOCAL
SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
```

When you later rotate the local encryption key, move the previous key into
`SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS` as a comma-separated
`keyId:base64Key` entry list so existing encrypted records remain readable
until they are rewritten under the new active key.

InboxBridge now also supports a safety cooldown for the admin-side
`Re-encrypt stored secrets` workflow:

- `SECURITY_SECRET_REENCRYPTION_COOLDOWN` defines how long a queued
  re-encryption request waits before execution
- the default is `PT12H`
- `PT0S` disables the cooldown and makes the action run immediately
- `SECURITY_SECRET_REENCRYPTION_ALLOW_IMMEDIATE_OVERRIDE=true` exposes a
  testing-only checkbox in the admin dialog that bypasses the cooldown for the
  current request

Leave the immediate override disabled in normal deployments. It exists so
manual testing and local validation do not require waiting through the full
cooldown window.

`SECRET_PROVIDER_MODE` now makes the secret-custody path explicit. `LOCAL`
keeps the existing AES-GCM local-key flow, `OPENBAO_TRANSIT` and
`VAULT_TRANSIT` use a Vault-compatible transit provider for new encrypted
writes and provider-health checks, and `SPLIT_KEY` now combines the local AES
key with a transit provider so stored UI-managed secrets require both trust
domains. Split-key mode currently requires `SECURITY_TOKEN_ENCRYPTION_KEY`
plus `SECRET_PROVIDER_SPLIT_SECONDARY_MODE=OPENBAO_TRANSIT|VAULT_TRANSIT`
and the corresponding transit settings for the selected secondary provider.

By default, InboxBridge derives its canonical browser URL as
`https://${PUBLIC_HOSTNAME}:${PUBLIC_PORT}`. You can still set
`PUBLIC_BASE_URL` explicitly if the public URL needs a different scheme or a
more custom shape than that derived default.

After startup:

- admin UI: `https://localhost:3000`
- remote control page: `https://localhost:3000/remote`
- backend HTTPS: `https://localhost:8443`
- backend and PostgreSQL stay on the internal Docker network by default, so only the HTTPS frontend is published to the host

If you want the generated self-signed certs to cover extra LAN or Tailscale hostnames, set:

```dotenv
TLS_FRONTEND_CERT_HOSTNAMES=inboxbridge.local,inboxbridge.your-tailnet.ts.net
TLS_BACKEND_CERT_HOSTNAMES=inboxbridge.local,inboxbridge.your-tailnet.ts.net
TLS_POSTGRES_CERT_HOSTNAMES=inboxbridge.local,inboxbridge.your-tailnet.ts.net
```

`cert-init` always includes `localhost`, the internal Docker service names, and
the hostname from `PUBLIC_BASE_URL` or the derived `PUBLIC_HOSTNAME` /
`PUBLIC_PORT` base URL; these variables add more SAN entries on top. If the
expected SAN list changes later, `cert-init` regenerates the self-signed
frontend, backend, and PostgreSQL certs automatically.

For passkeys/WebAuthn, prefer one canonical hostname everywhere instead of mixing unrelated hostnames such as `raspberrypi.local` and `something.ts.net`. One `SECURITY_PASSKEY_RP_ID` cannot span unrelated domains.

After the stack creates or refreshes the generated certs, trust `certs/ca.crt` on every browser/device that will open InboxBridge. Otherwise the browser will still treat the site as having a TLS error even if the hostname is present in the SAN list.

## Secret Rotation Safety

Secret rotation is now intentionally high-friction:

1. Configure the new active key or secret provider.
2. Keep all legacy decrypting keys available.
3. Open `Administration -> Secret management`.
4. Start `Re-encrypt stored secrets`.
5. Review the backend-verified prerequisites shown in the dialog.
6. If the cooldown is enabled, wait until the scheduled execution time.
7. After completion, save the verification details shown in the dialog before
   retiring any legacy keys.

The dialog now validates actual backend readiness, not just operator
acknowledgements. It also returns post-run verification messages and a list of
items the operator should save in recovery notes, such as the active key id
and any still-required legacy keys.

Important host/access notes:

- if you want to sign in from another device such as a phone, tablet, or another laptop, prefer a hostname covered by the generated or custom certificate
- passkeys/WebAuthn do not work reliably on raw IP hosts such as `https://192.168.50.6:3000`
- the `/remote` PWA install prompt also depends on the browser treating the HTTPS origin as trusted
- browser geolocation prompts can fail or behave inconsistently on self-signed or otherwise untrusted HTTPS origins

If you already have your own certificates, replace:

- `certs/backend.crt` and `certs/backend.key` for the backend
- `certs/frontend.crt` and `certs/frontend.key` for the admin UI
- `certs/ca.crt` too if the frontend proxy must trust a private CA

For Let's Encrypt, the usual mapping is:

- `fullchain.pem` -> `*.crt`
- `privkey.pem` -> `*.key`

Bootstrap admin credentials:

- username: `admin`
- password: `nimda`

The login screen only prefills those bootstrap credentials while the original bootstrap admin is still untouched and still required to change that default password. Once the password changes or a passkey is added, the UI stops prefilling them.

The `Security -> Sessions` view now shows:

- normal browser sessions and `/remote` sessions together
- best-effort browser/device labels
- approximate Geo-IP location when configured
- optional device-reported location captured from the browser, including an `Open in Maps` action when coordinates are available

## Google Cloud Setup For Gmail API

If you want InboxBridge to use the Gmail API, you must first register this application in Google Cloud.

InboxBridge normally uses one shared Google Cloud OAuth client for the whole deployment when Gmail is the destination provider.

That means:

- you do not need one Google Cloud project per Gmail user
- each user still performs their own Gmail OAuth consent
- each user gets their own Gmail refresh token

Create the Google Cloud side like this:

1. Open [Google Cloud project creation](https://console.cloud.google.com/projectcreate) and create or choose a project.
2. Open the [Gmail API library page](https://console.cloud.google.com/apis/library/gmail.googleapis.com) and enable the Gmail API for that project.
3. Open [Google Auth Platform branding](https://console.cloud.google.com/auth/branding) and fill in the app name, support email, and developer contact details.
4. Open [Google Auth Platform audience](https://console.cloud.google.com/auth/audience), keep the app in testing while you are setting up, and add the Gmail accounts that should be allowed to consent as test users.
5. Open [Google Auth Platform clients](https://console.cloud.google.com/auth/clients), create an OAuth client, and choose `Web application`.
6. Add this authorized redirect URI:

```text
https://localhost:3000/api/google-oauth/callback
```

Or, for a real deployment:

```text
https://<your-domain>/api/google-oauth/callback
```

7. Copy the generated `Client ID` and newly visible `Client secret`.
8. Paste them into `Administration -> OAuth Apps -> Google OAuth`, or fill
   these deployment-level values in `.env` if you intentionally manage shared
   OAuth app settings there:

```dotenv
GOOGLE_CLIENT_ID=replace-me
GOOGLE_CLIENT_SECRET=replace-me
GOOGLE_REDIRECT_URI=https://localhost:3000/api/google-oauth/callback
```

Notes:

- `GOOGLE_REFRESH_TOKEN` is optional in multi-user mode.
- Most users should connect their Gmail account from the admin UI instead of placing a Gmail refresh token in `.env`.
- `GMAIL_DESTINATION_USER` should usually stay `me`.
- the destination Gmail flow asks only for the Gmail import/label scopes it actually needs; reading Gmail mailboxes through OAuth applies only to source email accounts that explicitly use Google OAuth

Official Google references:

- [Create access credentials](https://developers.google.com/workspace/guides/create-credentials)
- [Using OAuth 2.0 for web server applications](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Manage OAuth clients](https://support.google.com/cloud/answer/15549257)

## Config-file-only IMAP source recipe

The admin UI is the recommended setup path because it can encrypt UI-managed
mailbox passwords and OAuth refresh tokens. For a personal deployment where you
prefer one auditable config file and accept plaintext operator-managed mailbox
secrets, define an env-managed source in `.env`:

```dotenv
MAIL_ACCOUNT_0__ID=outlook-main
MAIL_ACCOUNT_0__ENABLED=true
MAIL_ACCOUNT_0__PROTOCOL=IMAP
MAIL_ACCOUNT_0__HOST=outlook.office365.com
MAIL_ACCOUNT_0__PORT=993
MAIL_ACCOUNT_0__TLS=true
MAIL_ACCOUNT_0__AUTH_METHOD=PASSWORD
MAIL_ACCOUNT_0__OAUTH_PROVIDER=NONE
MAIL_ACCOUNT_0__USERNAME=person@example.com
MAIL_ACCOUNT_0__PASSWORD=replace-me-app-password
MAIL_ACCOUNT_0__FOLDER=INBOX,Archive
MAIL_ACCOUNT_0__FETCH_MODE=IDLE
MAIL_ACCOUNT_0__CUSTOM_LABEL=Imported/Outlook
MAIL_ACCOUNT_0__FOLDER_LABEL_MAPPINGS=INBOX=Imported/Inbox;Archive=Imported/Archive
MAIL_ACCOUNT_0__SPAM_JUNK_STRATEGY=IMPORT_AND_ROUTE
MAIL_ACCOUNT_0__SPAM_JUNK_SOURCE_FOLDER=Junk
```

`MAIL_ACCOUNT_0__CUSTOM_LABEL` is the default Gmail label for that source.
`MAIL_ACCOUNT_0__FOLDER_LABEL_MAPPINGS` is optional and only affects Gmail API
destinations. Use `folder=Gmail/Label` entries separated by semicolons; the
folder side is matched against the actual source IMAP folder of each imported
message.
`MAIL_ACCOUNT_0__SPAM_JUNK_STRATEGY` is optional. The default `IGNORE` leaves
spam/junk folders alone. `IMPORT_NORMAL` imports the configured
`MAIL_ACCOUNT_0__SPAM_JUNK_SOURCE_FOLDER` as normal mail. `IMPORT_AND_ROUTE`
imports that folder and routes Gmail destinations to `SPAM`; IMAP destinations
append those messages to the destination spam/junk folder configured in the UI.

## Configuration Backups

InboxBridge has two admin-only backup export tiers.

Use the safe tier for reviews, support handoff, and change tracking:

The endpoint is intended for an authenticated admin session. If you call it
with `curl`, include the session cookie from an active admin login.

```bash
curl -sk https://localhost:8443/api/admin/config-backups/safe
```

That response redacts mailbox passwords, OAuth refresh tokens, client secrets,
and other secret material. It is useful for understanding what is configured,
but it cannot restore mailbox access by itself.

Use the encrypted secret tier only for controlled recovery storage. It requires:

- an authenticated admin browser session
- fresh passkey or password reauthentication through the existing secret-management step-up flow
- explicit acknowledgement that the export grants mailbox access to anyone who can decrypt it
- an operator-owned RSA public key in PEM format

The server encrypts the full snapshot to that public key with
`RSA-OAEP-SHA256 + AES-256-GCM`, returns the encrypted payload directly, and
does not store the secret-bearing export server-side. InboxBridge audit-logs the
export metadata and public-key fingerprint only.

Treat the decrypted file like mailbox credentials. Store it offline, restrict
access tightly, and rotate mailbox passwords or OAuth grants if it is exposed.

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
- `SECURITY_TOKEN_ENCRYPTION_KEY` must already be configured before the browser callback route can exchange Google or Microsoft OAuth authorization codes securely
- the Microsoft frontend callback route now surfaces the backend's structured exchange errors directly and returns to InboxBridge automatically after a successful in-browser exchange

## Registration CAPTCHA And Authentication Security

InboxBridge now protects self-registration with a real CAPTCHA flow instead of
the older arithmetic challenge.

Current providers:

- `ALTCHA`: default, self-hosted, privacy-friendlier, no external registration or token; InboxBridge now uses ALTCHA's v2 signed proof-of-work challenge format and defaults the browser-side cost to a lighter `250` so the custom registration-modal solver stays responsive
- `TURNSTILE`: optional Cloudflare Turnstile provider
- `HCAPTCHA`: optional hCaptcha provider

Recommended operator path:

- leave the default `ALTCHA` provider in place for a new deployment
- switch to `TURNSTILE` or `HCAPTCHA` only if you intentionally want an external CAPTCHA provider
- configure and override those settings from `Administration -> Authentication Security`

That same admin section also controls:

- login lockout thresholds and durations
- registration challenge TTL
- the backend registration lockout, which reuses the same per-client-IP threshold and exponential block timings as login attempts
- Geo-IP provider order and timing
- provider-specific credentials such as Turnstile, hCaptcha, and optional IPinfo Lite secrets
- new-session notifications that now differentiate `Admin UI` vs `Remote control` sign-ins and can include approximate location details when available

On the admin UI itself, the unauthenticated login and registration forms also
apply a short client-side submit cooldown after each attempt so repeated button
presses do not rapidly hammer the backend between those server-side lockout
windows.

## Destination Mailbox Options

Users now configure the destination mailbox from `My Destination Mailbox` in the admin UI.

Available choices:

- `Gmail`: Gmail API import with Google OAuth
- `Outlook`: IMAP APPEND with Microsoft OAuth2 only; the UI keeps the Outlook IMAP/XOAUTH2 settings fixed and only asks for the destination folder plus the Microsoft account connection
- `Yahoo Mail`: IMAP APPEND with password or app password
- `Proton Mail Bridge`: IMAP APPEND against the local Proton Bridge endpoint
- `Generic IMAP`: manual IMAP APPEND settings

Rules enforced by the app:

- InboxBridge now requires TLS for every source and destination mailbox connection; insecure IMAP/POP3 mailbox settings are rejected instead of being saved
- a source mailbox cannot be the same mailbox as `My Destination Mailbox`
- if changing the destination would make an existing source point to the same mailbox, InboxBridge disables that source automatically until the conflict is resolved
- InboxBridge also treats cross-user mailbox mixing as a hard privacy boundary: polling is expected to keep every source tied to its owning destination mailbox, and the backend regression suite now includes multi-user GreenMail isolation coverage for that rule
- Gmail destinations are only considered ready after `Save and Authenticate` finishes successfully
- Outlook destinations can save folder-only edits without reconnecting, but changes that affect the connected mailbox identity still require Microsoft OAuth again
- new Outlook source accounts rely on `Save and Connect Microsoft`; the plain `Add` action is hidden until the account is no longer in the first-link flow
- editing an existing source account keeps its provider preset fixed and only shows provider-specific fields such as `Folder` or `Custom Label` when that provider supports them

After the destination mailbox and at least one personal source email account are configured, the user can also use the lightweight `/remote` page to trigger polling without opening the full admin workspace. That page now also mirrors the live polling progress model, so it can show the currently running source during a batch poll and expose pause/resume/stop plus per-source `Move Next` / `Retry` controls when the signed-in remote viewer is allowed to manage that run. Pause/stop requests are now honored during the current source too, because the backend checks the live-control state between fetched messages instead of waiting for the entire source to finish.

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

For stronger secret management, deployments can disable env-managed mailbox
secrets entirely with:

```dotenv
SECURITY_ALLOW_ENV_MANAGED_MAILBOX_SECRETS=false
```

When that policy is disabled, InboxBridge ignores `MAIL_ACCOUNT_*` source
definitions and the shared `GMAIL_REFRESH_TOKEN` fallback from `.env`. Generic
bootstrap settings such as database, public URL, TLS, and scheduler defaults
still remain in `.env`.

For stronger deployment-level secret custody and rotation planning after
bootstrap, use `Administration -> Authentication Security -> Secret management`
in the admin UI. That area shows provider health, migration targets for
`LOCAL`, `OPENBAO_TRANSIT`, `VAULT_TRANSIT`, and `SPLIT_KEY`, plus guided
re-encryption and recovery workflows.

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
MAIL_ACCOUNT_0__FETCH_MODE=POLLING
MAIL_ACCOUNT_0__CUSTOM_LABEL=Imported/Outlook
```

If no `MAIL_ACCOUNT_*` values are configured, InboxBridge loads no env-managed source accounts.
If `SECURITY_ALLOW_ENV_MANAGED_MAILBOX_SECRETS=false`, InboxBridge also ignores
configured `MAIL_ACCOUNT_*` values even if they are present.

IMAP sources can now also choose a fetch mode:

- `POLLING`: normal scheduled fetches using the effective poll interval and fetch window
- `IDLE`: a long-lived IMAP IDLE watcher for that one folder, with scheduled batch polling skipped for that source while the watcher stays healthy; if the watcher remains unhealthy long enough, InboxBridge temporarily falls back to scheduler polling until the watcher reconnects

`IDLE` is only available for IMAP accounts. POP3 sources are always forced back to `POLLING`. Saving a source after switching it to `IDLE` refreshes the watcher registry immediately, so the new watcher starts right away instead of waiting for the periodic refresh loop.

For UI-managed IMAP source accounts, the admin UI can also store optional post-poll source-side actions:

- leave handled mail untouched
- mark handled mail as read
- mark handled mail as forwarded
- delete handled mail
- move handled mail into another source folder

Those actions run after either a successful import or a duplicate match. POP3 accounts do not expose them because the protocol does not support equivalent source-side folder or flag operations. The forwarded option sets the IMAP `$Forwarded` flag when the source server supports it.

## Recommended First Run

1. Boot the stack.
2. Sign in as `admin`.
3. Change the bootstrap password.
4. Connect the destination mailbox from the admin UI.
5. Add one source email account.
6. Complete provider OAuth if needed.
7. Run a manual poll.
8. Optionally open `https://localhost:3000/remote` after setup if you want the lightweight remote-control page on phones or quick-access devices.

Once signed in, users can optionally:

- install `/remote` as a PWA shortcut on supported devices
- share the current session's device location from either the main app or `/remote`
- verify recent sign-ins, device/browser labels, and session types from `Security -> Sessions`
- control an active poll from the live progress panel in `My Poller Settings` or `Global Poller Settings`, including pause/resume/stop plus per-source `Move Next` / `Retry` actions when the current viewer is allowed to manage that run

## Related Docs

- [`README.md`](../README.md)
- [`OAUTH_SETUP.md`](OAUTH_SETUP.md)
