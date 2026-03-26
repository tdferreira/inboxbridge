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

## What it still does not do

- encrypt env-managed mailbox passwords from `.env`
- support non-Google / non-Microsoft provider OAuth flows
- provide IMAP IDLE or durable mailbox cursors
- provide production-grade metrics, backoff, or circuit breakers
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
- `JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge`
- `JDBC_USERNAME=inboxbridge`
- `JDBC_PASSWORD=inboxbridge`

With only that configuration, you can:

- open `https://localhost:3000`
- sign in with `admin` / `nimda`
- change the bootstrap password
- create users
- configure Gmail and source bridges from the admin UI
- access password changes from the top header via `Change Password`

You do not need to prefill `GOOGLE_*` or `MICROSOFT_*` just to bring the stack up.

## Minimum to import mail

To actually import mail, you need the bootstrap config above plus:

- a Gmail destination configured either through shared `GOOGLE_*` env vars or per-user Gmail settings in the admin UI
- provider OAuth app credentials for any OAuth-based source or destination flow
- at least one source bridge, either from `.env` or from the admin UI

The fastest operator-managed path is:

```bash
cp .env.example .env
```

Important values when you want a shared, env-managed deployment setup:

- `PUBLIC_BASE_URL` for browser callback defaults and UI links
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`
- `BRIDGE_SOURCES_<index>__...` for env-managed system bridges
- `GOOGLE_*` only if you want an env-managed system Gmail destination
- `MICROSOFT_*` for Microsoft OAuth sources

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

## Admin UI capabilities

The React admin UI lives in `admin-ui/` and runs in its own container/server.

Current features:

- secure sign-in using HTTP-only same-site cookies
- self-registration followed by admin approval
- admin-managed user creation
- multiple admin users, with admin rights managed from the UI
- per-user Gmail destination config
- per-user Gmail config can reuse the deployment’s shared Google OAuth client when one is configured
- per-user bridge create/update/delete
- per-user bridge passwords and OAuth refresh tokens are stored encrypted in PostgreSQL by default when saved from the admin UI
- password changes are available from the top header and enforce confirmation, minimum length, uppercase, lowercase, number, special character, and “must differ from current password” rules
- env-managed system bridge visibility
- manual poll trigger for admins
- Google OAuth launch for the system Gmail destination and for the current user
- Microsoft OAuth launch for visible Microsoft bridges
- import totals and latest poll outcome per bridge
- reusable component-based frontend sections with local CSS files
- frontend unit tests for key auth, Gmail, bridge-card, and utility behavior
- one-click copy actions for API error banners and bridge error payloads

Security model:

- admin APIs require `ADMIN`
- user config APIs require an authenticated session and are scoped to the current user
- admins can inspect other users’ configuration summaries without seeing raw client secrets or refresh tokens
- users cannot access other users’ bridge or Gmail configuration through the authenticated user APIs

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

Recommended flow:

1. Sign in to `https://localhost:3000`
2. Use the relevant OAuth button in the UI
3. Complete provider consent
4. Use the callback page button to exchange the code
5. The callback page starts a 10-second countdown and returns to the admin UI automatically after a successful in-browser exchange
6. You can still use the callback page return button to navigate back immediately
7. If secure storage is enabled, InboxBridge stores the token encrypted in PostgreSQL automatically

OAuth callback usability notes:

- the callback page includes a `Copy Code` button
- the callback page includes a `Return To Admin UI` button
- returning to the admin UI before exchange asks for confirmation
- after a successful in-browser exchange, the callback page shows a 10-second auto-return countdown
- if you leave without exchanging, you must add the code or resulting token manually later

Admin UI loading feedback notes:

- buttons that trigger backend calls now show an inline loading spinner while the request is in progress
- this includes sign-in, registration, password changes, Gmail settings saves, bridge saves/deletes, user management actions, poll runs, refresh, and OAuth start actions

### Fix Google `403: org_internal`

If Google shows `Error 403: org_internal`, your Google OAuth consent screen is restricted to an internal Google Workspace audience.

Fix it in Google Cloud:

1. Open `APIs & Services > OAuth consent screen`
2. Change the audience / user type from `Internal` to `External`
3. If the app is still in testing, add your Gmail accounts as `Test users`
4. Retry the InboxBridge flow

### Can InboxBridge auto-create Google OAuth client credentials?

No.

Google OAuth client IDs and client secrets belong to a Google Cloud project, not to a Gmail mailbox. InboxBridge can guide the user through setup and store the provided credentials securely, but it cannot automatically provision a Google OAuth client from the admin UI.

In practice, each deployment must choose one of these patterns:

1. reuse one shared Google OAuth client for many users
2. let each user create a Google Cloud OAuth client and paste the values into the UI

The admin UI now explains those setup steps next to the Gmail settings form, and if a shared Google OAuth client is configured it can be reused automatically for each user’s Gmail destination.

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
- per-user `Client ID` / `Client Secret` are optional when the deployment already has a shared Google OAuth client configured
- the Gmail destination panel now shows the effective Gmail OAuth state for the user, including shared deployment client availability and refresh-token storage from the encrypted OAuth credential store

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
- provider backoff / lockout protection is not implemented yet
- metrics and audit-friendly structured event logs are still limited

## Tests

Verified on 2026-03-26:

- `mvn test` passes with 27 backend tests
- admin UI Docker build runs 6 frontend Vitest unit tests successfully
- Docker Compose builds successfully
- the HTTPS admin UI serves correctly in the container
- unauthenticated `GET /api/auth/me` returns `401` through the HTTPS proxy
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`

## More docs

- Microsoft and Gmail provider setup: [docs/OAUTH_SETUP.md](/Users/tdferreira/Developer/inboxbridge/docs/OAUTH_SETUP.md)
- architectural summary and code structure: [CONTEXT.md](/Users/tdferreira/Developer/inboxbridge/CONTEXT.md)
- frontend component structure and frontend tests: [admin-ui/README.md](/Users/tdferreira/Developer/inboxbridge/admin-ui/README.md)
