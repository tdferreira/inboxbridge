# InboxBridge

InboxBridge is a self-hosted mail bridge that polls external IMAP / POP3 mailboxes and imports their messages into Gmail through the Gmail API.

The project now runs as two cooperating applications:

- a Quarkus backend
- a separate React admin UI

By default, Docker Compose starts both over HTTPS with locally generated self-signed certificates.

InboxBridge supports both operator-managed system configuration from `.env` and per-user configuration stored securely in PostgreSQL through the admin UI.

## What it already does

1. Polls env-managed system bridges and DB-managed user bridges.
2. Imports messages into Gmail with `users.messages.import`.
3. Deduplicates imports in PostgreSQL.
4. Stores OAuth tokens encrypted in PostgreSQL when secure storage is enabled.
5. Stores user-managed Gmail and bridge secrets encrypted in PostgreSQL.
6. Provides a separate React admin UI with login, self-registration, approval workflow, user management, Gmail config, and bridge config.
7. Supports Google OAuth for Gmail destinations and Microsoft OAuth for Outlook / Hotmail / Live sources.
8. Organizes the admin UI into reusable React components with component-scoped styles and frontend unit tests.
9. Supports WebAuthn passkeys for browser sign-in after a user enrolls one from the security panel.
10. Supports per-user poller overrides plus automatic per-source cooldown/backoff when providers start rejecting or throttling requests.
11. Can run either in single-user mode or multi-user mode, controlled by `.env`.

## What it still does not do

- encrypt env-managed mailbox passwords from `.env`
- support non-Google / non-Microsoft provider OAuth flows
- provide IMAP IDLE or durable mailbox cursors
- provide production-grade metrics or circuit breakers
- integrate with an external secret vault or KMS

## Stack

- Java 21
- Quarkus 3.32.3
- React 18 + Vite
- PostgreSQL 16
- Flyway
- Hibernate ORM Panache
- Angus Mail
- Docker Compose
- Maven group/package namespace: `dev.inboxbridge`

## Minimum bootstrap

If you only want to boot the stack, sign in to the admin UI, and start configuring users and OAuth from the browser, the minimum local setup is:

1. Copy the env template.
2. Generate an encryption key.
3. Start Docker Compose.

```bash
cp .env.example .env
openssl rand -base64 32
docker compose up --build
```

Then set at least these values in `.env` before starting:

- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY=<base64 32-byte key>`
- `PUBLIC_BASE_URL=https://localhost:3000`
- `BRIDGE_SECURITY_PASSKEY_RP_ID=localhost`
- `BRIDGE_SECURITY_PASSKEY_ORIGINS=https://localhost:3000`
- `JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge`
- `JDBC_USERNAME=inboxbridge`
- `JDBC_PASSWORD=inboxbridge`

With only that configuration, you can:

- open `https://localhost:3000`
- sign in with `admin` / `nimda`
- change the bootstrap password
- register passkeys from the `Security` panel if the browser supports them
- create users
- configure Gmail and mail fetchers from the admin UI
- access password changes from the top header via `Change Password`

You do not need to prefill `GOOGLE_*` or `MICROSOFT_*` just to bring the stack up.

## Minimum to import mail

To actually import mail, you need the bootstrap config above plus:

- a Gmail destination configured either through shared `GOOGLE_*` env vars or per-user Gmail settings in the admin UI
- provider OAuth app credentials for any OAuth-based source or destination flow
- at least one mail fetcher, either from `.env` or from the admin UI

The fastest operator-managed path is:

```bash
cp .env.example .env
```

Important values when you want a shared, env-managed deployment setup:

- `PUBLIC_BASE_URL` for browser callback defaults and UI links
- `BRIDGE_MULTI_USER_ENABLED` to choose single-user vs multi-user operation
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`
- `BRIDGE_SOURCES_<index>__...` for env-managed system bridges
- `GOOGLE_*` only if you want an env-managed system Gmail destination
- `MICROSOFT_*` for Microsoft OAuth sources
- `BRIDGE_SECURITY_PASSKEY_*` if you need to override the default local WebAuthn relying-party settings

Generate a local encryption key with:

```bash
openssl rand -base64 32
```

## Quick start

```bash
docker compose up --build
```

Services:

- admin UI: `https://localhost:3000`
- backend HTTP: `http://localhost:8080`
- backend HTTPS: `https://localhost:8443`
- PostgreSQL: `localhost:5432`

The first compose run generates local certificates into `./certs`. You can later replace those files with your own certificates.

After the stack is up:

1. Open `https://localhost:3000`
2. Sign in with the bootstrap admin
3. Change the bootstrap password
4. Follow the `Quick Setup Guide` panel in the admin UI
5. Configure Gmail destination OAuth
6. Add at least one bridge
7. Run a poll and verify the dashboard counters

The `Quick Setup Guide` cards in the admin UI are clickable and jump to the section where each action is performed. They also reflect live state:

- green when the step is complete
- red when InboxBridge has a recorded error associated with that step
- neutral when the step has not been completed yet
- the guide auto-collapses when all tracked steps are complete
- users can opt into persisting collapsed/expanded section state across sign-ins on their own account

## Admin UI login

Initial bootstrap credentials:

- username: `admin`
- password: `nimda`

The bootstrap admin is marked `mustChangePassword=true`, so change it immediately after first login.
The running unauthenticated login screen does not expose whether those bootstrap credentials are still active, so setup operators should rely on this documentation rather than a public status endpoint.
After that, the user can enroll one or more passkeys from the `Security` panel and use `Sign in with passkey` on later visits.
If `BRIDGE_MULTI_USER_ENABLED=false`, the login screen hides self-registration entirely and the admin UI does not expose user-management features.
Single-user mode still keeps the rest of the control plane visible for the bootstrap admin, including Gmail setup, mail fetchers, poller settings, and dashboard views.
Current login rules:

- password only: the normal `Sign in` flow uses only the password
- passkey only: the normal `Sign in` flow ignores any typed password and starts passkey authentication
- password + passkey: the normal `Sign in` flow validates the password first and then requires the passkey as a second factor
- the dedicated `Sign in with passkey` button is mainly for passkey-only accounts or discoverable-credential sign-in
- users can intentionally remove their password and stay passkey-only
- self-registration is opened from a dedicated `Register for access` button and uses a focused modal instead of permanently rendering the request form on the login card

The admin UI also supports these languages, with the user preference stored per account and reused across sessions:

- English
- French
- German
- Portuguese (Portugal)
- Portuguese (Brazil)
- Spanish

## Admin UI capabilities

The React admin UI lives in `admin-ui/` and runs in its own container/server.

Current features:

- secure sign-in using HTTP-only same-site cookies
- optional passkey sign-in using WebAuthn
- self-registration followed by admin approval
- self-registration opens through a dedicated unauthenticated modal flow instead of occupying the main login screen full time
- admin-managed user creation
- single-user deployments can disable all self-registration and user-management surfaces with `BRIDGE_MULTI_USER_ENABLED=false`
- multiple admin users, with admin rights managed from the UI
- admins can reset another user’s password to a temporary value and wipe that user’s passkeys
- admin password reset now opens a dedicated dialog instead of an always-visible inline form
- the admin reset-password dialog shows the temporary-password rules inline so the operator can see when the new password satisfies the policy before submitting
- admin actions that can suspend a user, force a password change, wipe passkeys, or remove stored mail-fetcher data now require an explicit confirmation modal before execution
- admins cannot remove their own admin rights
- admin-managed per-user Gmail destination overrides when advanced customization is really needed
- non-admin users get a simplified Gmail destination status panel with connect/reconnect OAuth, while shared Google OAuth client reuse still lets each user grant consent for their own Gmail mailbox refresh token
- per-user email fetcher create/update/delete through a dedicated modal dialog
- common provider presets for Outlook / Hotmail / Live, Gmail, Yahoo Mail, and Proton Mail Bridge when creating a fetcher
- auth-aware fetcher forms that hide password-only or OAuth-only fields when they are not relevant
- inline help tooltips for fetcher and poller fields so each control explains what it does
- env-managed fetchers shown in the same operational list with a read-only `.env` badge, but only for the account named `admin`
- placeholder fallback values from `application.yaml` are now filtered out, so if no `BRIDGE_SOURCES_*` values are configured then no env-managed fetcher appears in the UI or runtime
- per-user mail-fetcher passwords and OAuth refresh tokens are stored encrypted in PostgreSQL by default when saved from the admin UI
- the add/edit mail fetcher dialog is wider, rejects duplicate IDs before submit, and only shows the `.env` badge for environment-managed entries
- password changes are available from the top header security panel and enforce confirmation, minimum length, uppercase, lowercase, number, special character, and “must differ from current password” rules
- users can remove their password entirely and operate in passkey-only mode
- passkeys can be registered and removed from that same top-header security panel
- the last passkey on a passwordless account cannot be removed until a password is set again or another passkey is added
- self-service password removal and passkey deletion now require an explicit confirmation modal before the backend call is made
- admin-managed runtime overrides for polling enablement, poll interval, and fetch window while still showing the `.env` defaults
- a dedicated `Poller Settings` section for global polling controls and health metrics instead of rendering env-managed fetchers there
- a dedicated `My Poller Settings` section so each user can override polling enablement, interval, and fetch window for their own UI-managed fetchers
- manual poll trigger for admins
- per-source cooldown/backoff state that pauses only the affected fetcher after repeated auth, quota, or transient provider failures
- cooldown visibility in the UI, including next poll time, cooldown-until, failure count, and the last failure reason for each fetcher
- Google OAuth launch for the system Gmail destination and for the current user
- Microsoft OAuth launch for visible Microsoft bridges
- import totals and latest poll outcome per bridge
- reusable component-based frontend sections with local CSS files
- frontend unit tests for key auth, Gmail, bridge-card, and utility behavior
- one-click copy actions for API error banners and bridge error payloads
- dismissable notifications that can focus the related section, with non-critical notices auto-closing after 10 seconds
- per-user admin-ui language selection persisted in PostgreSQL and mirrored to the browser for future visits

Security model:

- admin APIs require `ADMIN`
- user config APIs require an authenticated session and are scoped to the current user
- admins can inspect other users’ configuration summaries without seeing raw client secrets or refresh tokens
- users cannot access other users’ bridge or Gmail configuration through the authenticated user APIs
- users cannot access or delete other users’ passkeys

## OAuth flows

Preferred local redirect URIs:

- Google: `https://localhost:3000/api/google-oauth/callback`
- Microsoft: `https://localhost:3000/api/microsoft-oauth/callback`

If you deploy InboxBridge on another hostname, set `PUBLIC_BASE_URL` and the default callback URIs will follow that host automatically unless you explicitly override `GOOGLE_REDIRECT_URI` or `MICROSOFT_REDIRECT_URI`.

Useful endpoints:

- `GET /api/google-oauth/start/self`
- `GET /api/google-oauth/start/system`
- `POST /api/google-oauth/exchange`
- `GET /api/google-oauth/callback`
- `GET /api/microsoft-oauth/start?sourceId=<bridge-id>`
- `POST /api/microsoft-oauth/exchange`
- `GET /api/microsoft-oauth/callback`
- `POST /api/auth/passkey/options`
- `POST /api/auth/passkey/verify`
- `GET /api/account/passkeys`
- `POST /api/account/passkeys/options`
- `POST /api/account/passkeys/verify`

Recommended flow:

1. Sign in to `https://localhost:3000`
2. Use the relevant OAuth button in the UI
3. Complete provider consent
4. The callback page automatically tries to exchange the code in the browser as soon as it loads
5. The callback page starts a 10-second countdown and returns to the admin UI automatically after a successful in-browser exchange
6. You can still use the callback page exchange button to retry manually if the automatic attempt fails
7. You can still use the callback page return button to navigate back immediately
8. If secure storage is enabled, InboxBridge stores the token encrypted in PostgreSQL automatically

OAuth callback usability notes:

- the callback page includes a `Copy Code` button
- the callback page automatically attempts the code exchange when it loads
- the Google callback page now also re-reads the browser query string directly, so it can recover if the reverse proxy or callback rendering path did not populate the code into the initial HTML
- both Google and Microsoft callback pages now detect consent denial and tell the user to retry the OAuth flow while approving every requested permission
- the callback page includes a `Return To Admin UI` button
- returning to the admin UI before exchange asks for confirmation
- after a successful in-browser exchange, the callback page shows a 10-second auto-return countdown
- if you leave without exchanging, you must add the code or resulting token manually later

Admin UI loading feedback notes:

- buttons that trigger backend calls now show an inline loading spinner while the request is in progress
- this includes sign-in, registration, password changes, Gmail settings saves, bridge saves/deletes, user management actions, poll runs, refresh, and OAuth start actions

## Passkeys

InboxBridge can use browser passkeys for admin-ui sign-in after a user first signs in with their password and enrolls a passkey from the `Security` panel.

Default local settings:

- `BRIDGE_SECURITY_PASSKEYS_ENABLED=true`
- `BRIDGE_SECURITY_PASSKEY_RP_ID=localhost`
- `BRIDGE_SECURITY_PASSKEY_RP_NAME=InboxBridge`
- `BRIDGE_SECURITY_PASSKEY_ORIGINS=https://localhost:3000`
- `BRIDGE_SECURITY_PASSKEY_CHALLENGE_TTL=PT5M`

These values are loaded through the same `bridge.security.passkeys` config tree as the rest of the backend settings, so invalid mapping changes will fail startup immediately.

For a deployed hostname, set these to your public origin:

- `PUBLIC_BASE_URL=https://your-domain.example`
- `BRIDGE_SECURITY_PASSKEY_RP_ID=your-domain.example`
- `BRIDGE_SECURITY_PASSKEY_ORIGINS=https://your-domain.example`

Notes:

- passkeys require HTTPS or localhost
- passkeys require a browser with WebAuthn support
- only public credential material is stored for passkeys; the private key stays with the authenticator
- admins can wipe all passkeys for a user, after which that user must enroll a new passkey to sign in passwordlessly again
- when both a password and a passkey exist, sign-in becomes password + passkey instead of passkey-only
- when only a passkey exists, typed passwords are ignored and the browser is guided into passkey sign-in
- passwordless accounts are supported, but InboxBridge will not allow a user to remove their final remaining passkey

## Polling controls

Polling still starts from `.env`, but admins can now override the live runtime behavior from the admin UI system dashboard.

What can be overridden:

- whether scheduled polling is enabled
- the scheduled poll interval
- the mailbox fetch window

Behavior:

- `.env` remains the default source of truth on startup
- the admin UI stores only overrides in PostgreSQL
- clearing an override falls back to the `.env` default again
- the scheduler checks the effective interval dynamically, so changes apply without editing `.env`

Accepted poll interval formats:

- shorthand: `30s`, `5m`, `1h`, `1d`
- ISO-8601: `PT30S`, `PT5M`, `PT1H`

Current limits:

- minimum interval: `5s`
- fetch window range: `1` to `500`

### Fix Google `403: org_internal`

If Google shows `Error 403: org_internal`, your Google OAuth consent screen is restricted to an internal Google Workspace audience.

Fix it in Google Cloud:

1. Open `APIs & Services > OAuth consent screen`
2. Change the audience / user type from `Internal` to `External`
3. If the app is still in testing, add your Gmail accounts as `Test users`
4. Retry the InboxBridge flow

### Can InboxBridge auto-create Google OAuth client credentials?

No.

## Env Variable Reference

Core runtime:

- `JDBC_URL`, `JDBC_USERNAME`, `JDBC_PASSWORD`: PostgreSQL connection used by Quarkus and Flyway.
- `PUBLIC_BASE_URL`: public HTTPS base URL used to derive OAuth callback defaults.
- `HTTP_PORT`, `HTTPS_PORT`: backend listener ports.
- `HTTPS_CERT_FILE`, `HTTPS_KEY_FILE`: backend TLS certificate and private key paths.

Shared Google destination:

- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`: deployment-shared Google Cloud OAuth client for the Gmail API. In the normal deployment model, one Google Cloud project/client is enough for many users. Each user still needs their own Gmail OAuth consent and refresh token for their own destination mailbox. These values belong to the deployment, not to one specific end user.
- `GOOGLE_REFRESH_TOKEN`: refresh token for the system Gmail destination.
- `GOOGLE_REDIRECT_URI`: optional explicit Google callback override.
- `BRIDGE_GMAIL_DESTINATION_USER`: Gmail API target user for the shared/system destination. In most cases this should stay `me`, which tells Gmail to import into the mailbox that granted the token.
- `BRIDGE_GMAIL_CREATE_MISSING_LABELS`: create configured Gmail labels automatically if they do not exist yet.
- `BRIDGE_GMAIL_NEVER_MARK_SPAM`: asks Gmail import to avoid spam classification where supported.
- `BRIDGE_GMAIL_PROCESS_FOR_CALENDAR`: lets Gmail process imported messages for calendar extraction.

Shared Microsoft OAuth app:

- `MICROSOFT_TENANT`: usually `consumers` for Outlook.com / Hotmail / Live accounts.
- `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`: Microsoft Entra app credentials reused across Outlook bridges.
- `MICROSOFT_REDIRECT_URI`: optional explicit Microsoft callback override.

Security:

- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`: base64-encoded 32-byte key used to encrypt tokens and user-managed secrets at rest.
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`: version label stored beside encrypted values.
- `BRIDGE_SECURITY_PASSKEYS_ENABLED`: enables or disables WebAuthn passkeys.
- `BRIDGE_SECURITY_PASSKEY_RP_ID`, `BRIDGE_SECURITY_PASSKEY_RP_NAME`, `BRIDGE_SECURITY_PASSKEY_ORIGINS`: passkey relying-party identity settings.
- `BRIDGE_SECURITY_PASSKEY_CHALLENGE_TTL`: lifetime for passkey registration/authentication ceremonies.

Polling defaults:

- `BRIDGE_POLL_ENABLED`: default scheduled polling state before any admin override.
- `BRIDGE_POLL_INTERVAL`: default polling interval before any admin override.
- `BRIDGE_FETCH_WINDOW`: default number of most recent source messages scanned on each poll before any admin override.

Env-managed mail fetchers:

- `BRIDGE_SOURCES_<n>__ID`: stable mail-fetcher identifier.
- `BRIDGE_SOURCES_<n>__ENABLED`: enables or disables that source.
- `BRIDGE_SOURCES_<n>__PROTOCOL`: `IMAP` or `POP3`.
- `BRIDGE_SOURCES_<n>__HOST`, `BRIDGE_SOURCES_<n>__PORT`: source mailbox server location.
- `BRIDGE_SOURCES_<n>__TLS`: whether to require TLS for the source connection.
- `BRIDGE_SOURCES_<n>__AUTH_METHOD`: `PASSWORD` or `OAUTH2`.
- `BRIDGE_SOURCES_<n>__OAUTH_PROVIDER`: currently `NONE` or `MICROSOFT`.
- `BRIDGE_SOURCES_<n>__USERNAME`: source mailbox username.
- `BRIDGE_SOURCES_<n>__PASSWORD`: source mailbox password or app password for password auth.
- `BRIDGE_SOURCES_<n>__OAUTH_REFRESH_TOKEN`: optional manual refresh token for env-managed OAuth2 sources.
- `BRIDGE_SOURCES_<n>__FOLDER`: IMAP folder to scan.
- `BRIDGE_SOURCES_<n>__UNREAD_ONLY`: whether to import only unread messages.
- `BRIDGE_SOURCES_<n>__CUSTOM_LABEL`: Gmail label to apply after import.

Google OAuth client IDs and client secrets belong to a Google Cloud project, not to a Gmail mailbox. InboxBridge can guide the user through setup and store the provided credentials securely, but it cannot automatically provision a Google OAuth client from the admin UI.

In practice, each deployment must choose one of these patterns:

1. reuse one shared Google OAuth client for many users
2. let each user create a Google Cloud OAuth client and paste the values into the UI

Pattern `1` is the intended default for most InboxBridge deployments.

The admin UI now explains those setup steps next to the Gmail destination area. For regular users the UI is simplified to Gmail status plus connect/reconnect OAuth, while admins can still access the advanced override form when that is actually needed.

## Secure token storage

Secure storage is enabled by setting:

- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`
- optionally `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`

When enabled:

- Google refresh/access tokens are stored encrypted
- Microsoft refresh/access tokens are stored encrypted
- user-managed Gmail client credentials are stored encrypted
- user-managed bridge passwords and refresh tokens are stored encrypted by default whenever they are saved from the admin UI

Important nuance:

- secrets are encrypted at the application layer before they reach PostgreSQL
- passwords are hashed, not reversibly encrypted
- non-secret metadata remains queryable in the database so the app can function

For the user Gmail screen specifically:

- `Destination User` should usually stay `me`
- `Redirect URI` now defaults to the deployment callback URL and is prefilled in the UI
- the actual Gmail destination mailbox is the Google account that completed OAuth consent; `Destination User=me` just tells the Gmail API to use that authenticated mailbox
- per-user `Client ID` / `Client Secret` are optional admin-only overrides when the deployment already has a shared Google OAuth client configured; most deployments should not need these overrides
- the Gmail destination panel now shows deployment-shared Google client availability separately from user-specific client overrides and refresh-token storage, but non-admin users only see the simplified connection status they actually need

When disabled:

- env-managed flows can still fall back to `.env`
- user-managed secret storage is intentionally blocked

## HTTPS certificates

Compose uses a `cert-init` container that generates:

- `certs/ca.crt`
- `certs/backend.crt`
- `certs/backend.key`
- `certs/frontend.crt`
- `certs/frontend.key`

The frontend proxies to the backend over HTTPS and validates the backend certificate against `ca.crt`.

To replace the generated certs with your own:

1. stop the stack
2. replace the files in `./certs`
3. start the stack again

## Important current limitations

- env-managed mailbox passwords are still plaintext in `.env`
- polling still scans the most recent `bridge.fetch-window` messages rather than using durable mailbox cursors
- metrics and audit-friendly structured event logs are still limited

## Tests

Verified on 2026-03-26:

- `mvn test` passes
- admin UI Docker build runs the Vitest suite successfully
- Docker Compose builds successfully
- the HTTPS admin UI serves correctly in the container
- unauthenticated `GET /api/auth/me` returns `401` through the HTTPS proxy
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`

## More docs

- Microsoft and Gmail provider setup: [docs/OAUTH_SETUP.md](/Users/tdferreira/Developer/inboxbridge/docs/OAUTH_SETUP.md)
- architectural summary and code structure: [CONTEXT.md](/Users/tdferreira/Developer/inboxbridge/CONTEXT.md)
- frontend component structure and frontend tests: [admin-ui/README.md](/Users/tdferreira/Developer/inboxbridge/admin-ui/README.md)
