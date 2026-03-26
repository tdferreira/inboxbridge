# InboxBridge Context

## Purpose

InboxBridge is a self-hosted bridge that pulls mail from external IMAP / POP3 accounts and imports it into Gmail. It is meant to preserve the “one Gmail inbox, many other accounts” workflow without relying on SMTP forwarding.

## Current product shape

InboxBridge now consists of:

- a Quarkus backend
- a separate React admin UI
- PostgreSQL for durable state
- Docker Compose wiring for backend, frontend, database, and local TLS bootstrap

The app supports two bridge sources of truth:

- env-managed system bridges from `.env`
- user-managed bridges stored in PostgreSQL through the admin UI

Deployment-wide browser callback defaults can now be anchored on `PUBLIC_BASE_URL`, which feeds the default Google and Microsoft OAuth redirect URIs shown in the UI and docs.

## Technical stack

- Java 21
- Quarkus 3.32.3
- React 18 + Vite
- PostgreSQL 16
- Flyway
- Hibernate ORM Panache
- Angus Mail
- Java `HttpClient`
- Docker Compose

## Main architectural decisions

### 1. Separate frontend and backend

The React admin UI lives in `admin-ui/` and runs in its own container/server. It communicates with the Quarkus backend through proxied REST endpoints under `/api/...`.

Why:

- lets the UI evolve independently
- keeps the backend as the system of record for auth, OAuth, and secret handling
- makes HTTPS termination and browser flows easier to reason about

### 2. Session-cookie auth instead of JWT

The admin UI authenticates with:

- username/password
- opaque PostgreSQL-backed sessions
- secure HTTP-only same-site cookies

Why:

- same-origin admin UI
- simpler invalidation story
- no JWT signing/rotation complexity for a self-hosted single-deployment app

Bootstrap auth behavior:

- default admin user is `admin`
- default password is `nimda`
- bootstrap admin is forced to change password
- admin-created users are also forced to change password
- self-registered users start as `inactive` and `unapproved`
- admins can approve, suspend, reactivate, promote, or demote users from the admin UI
- there can be more than one admin, but the system protects the last approved active admin from being removed accidentally

### 3. Hybrid env + DB config model

Env-managed system bridges remain supported from `.env`.

DB-managed user config now stores:

- app users
- user sessions
- per-user Gmail destination config
- per-user bridge definitions
- encrypted OAuth credentials
- recent source poll events
- imported-message dedupe state

Why:

- keeps ops/bootstrap config simple
- allows the admin UI to manage user-owned bridges
- preserves explicit operator control over env-managed values
- lets user Gmail destinations inherit a shared deployment-level Google OAuth client when that is the intended operating model

### 4. Gmail `users.messages.import`

The destination side still uses Gmail `users.messages.import`.

Why:

- preserves raw MIME better than SMTP forwarding
- avoids sender reputation issues
- keeps labels and import metadata under app control

### 5. Runtime bridge resolution

`RuntimeBridgeService` combines:

- enabled env-defined system bridges
- enabled DB-defined user bridges with valid Gmail destination config

Polling runs over that combined runtime set.

### 6. Destination-scoped dedupe

Imported messages are now deduped by Gmail destination as well as source identity.

Current checks:

1. `(destinationKey, sourceAccountId, sourceMessageKey)`
2. `(destinationKey, rawSha256)`

Why:

- avoids cross-user dedupe collisions
- allows two different users to import the same raw MIME into two different Gmail accounts

### 7. Encrypted secret storage

When `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` is configured, InboxBridge encrypts:

- Google OAuth tokens
- Microsoft OAuth tokens
- user-managed Gmail client IDs, client secrets, and refresh tokens
- user-managed bridge passwords and refresh tokens

Implementation:

- AES-GCM
- context-bound AAD
- key version recorded alongside ciphertext-bearing records

Important nuance:

- application secrets are encrypted before they reach PostgreSQL
- user passwords are hashed, not decryptable
- non-secret metadata is intentionally left queryable

Not yet covered:

- env-managed mailbox passwords from `.env`
- external KMS / HSM

### 8. HTTPS by default in Docker Compose

Compose now includes a `cert-init` container that generates:

- `certs/ca.crt`
- `certs/backend.crt`
- `certs/backend.key`
- `certs/frontend.crt`
- `certs/frontend.key`

The admin UI serves HTTPS and proxies to the backend over HTTPS while trusting the local CA.

These files can be replaced with operator-supplied certs later.

## OAuth model

### Google

Google OAuth is used for Gmail destinations:

- env-managed system Gmail destination
- per-user Gmail destination stored in PostgreSQL

Per-user Gmail destinations can either:

- use their own Google OAuth client credentials stored securely in PostgreSQL
- or inherit the shared deployment Google client from env/config

The browser-first flow is:

1. start from the admin UI
2. provider redirect
3. callback page
4. in-browser exchange button
5. encrypted DB storage when available

Important current operator note:

- Google `403: org_internal` means the Google OAuth consent screen is set to `Internal`
- for self-hosted personal / multi-user InboxBridge use, it should usually be `External`
- InboxBridge cannot auto-provision a Google OAuth client for a user; the `client_id` / `client_secret` still come from a Google Cloud project created outside the app

### Microsoft

Microsoft OAuth is used for Outlook / Hotmail / Live source mailboxes.

Supported now:

- env-managed Microsoft source bridges
- user-managed Microsoft source bridges

One Microsoft Entra app registration can be reused across many Outlook.com personal accounts as long as it supports personal Microsoft accounts. Each mailbox still consents separately and receives its own token set.

## Security posture

Implemented now:

- TLS-capable mailbox connections
- hostname verification for mailbox TLS
- HTTPS frontend
- HTTPS frontend-to-backend proxying
- secure HTTP-only same-site cookies
- role-based access control for admin/user API boundaries
- encrypted OAuth tokens at rest
- encrypted user-managed secret storage at rest
- minimal explicit Google scopes
- protocol-specific Microsoft scopes

Still missing for a higher-assurance v1:

- encrypted env-managed mailbox passwords
- KMS-backed key management and rotation
- durable backoff / lockout protection
- richer metrics and alerting
- more structured audit logging

## Runtime model

### Polling

`PollingService`:

- runs on the scheduler when enabled
- can be triggered manually by admins
- prevents overlapping runs with an `AtomicBoolean`
- processes bridges sequentially

### Source status

`SourcePollEvent` records:

- source id
- trigger
- status
- fetched/imported/duplicate counts
- error text

### UI visibility

The React admin UI shows:

- env-managed system bridges
- user-managed bridges
- per-bridge import totals
- last poll result
- OAuth launch buttons
- self-registration on the login screen
- user list and user config summaries for admins
- admin approval / suspension / role-management controls
- a top-level setup guide panel that explains the first-run sequence in the admin UI
- setup guide entries are clickable links that focus the relevant section where the user must take action
- setup guide entries now use neutral / green / red visual state based on pending work, successful completion, and recorded bridge/provider errors
- when all tracked setup steps are complete, the guide auto-collapses by default
- the major admin-ui sections can be collapsed, and users can opt into per-account persisted section state across login sessions
- password changes are now accessed from the top hero/header controls, not from within the Gmail destination sidebar
- password changes now enforce confirmation, minimum length, mixed case, number, special character, and “different from current password” validation in both UI and backend
- prefilled Gmail redirect URIs and shared-client guidance on the user Gmail settings page
- saved-credential status for the user Gmail destination, including OAuth refresh tokens stored in the encrypted credential table
- copy-to-clipboard actions on UI error surfaces that show API payloads

## Code structure

Root package:

```text
dev.inboxbridge
```

Current package layout:

```text
src/main/java/dev/inboxbridge
├── config
├── domain
├── dto
├── persistence
├── security
├── service
└── web
```

Notable backend areas:

- `service/AuthService.java`
- `service/AppUserService.java`
- `service/UserGmailConfigService.java`
- `service/UserBridgeService.java`
- `service/RuntimeBridgeService.java`
- `service/GoogleOAuthService.java`
- `service/MicrosoftOAuthService.java`
- `service/PollingService.java`
- `web/AuthResource.java`
- `web/UserConfigResource.java`
- `web/UserManagementResource.java`
- `web/AdminResource.java`

Frontend:

```text
admin-ui
├── README.md
├── Dockerfile
├── nginx.conf
├── package.json
└── src
    ├── App.jsx
    ├── components
    │   ├── account
    │   ├── admin
    │   ├── auth
    │   ├── bridges
    │   ├── common
    │   ├── gmail
    │   └── layout
    ├── lib
    ├── main.jsx
    ├── styles.css
    └── test
```

Migrations:

```text
src/main/resources/db/migration
├── V1__init.sql
├── V2__oauth_credential.sql
├── V3__source_poll_event.sql
├── V4__identity_and_user_bridge.sql
├── V5__user_secret_keys_and_destination_scope.sql
└── V6__user_approval.sql
```

## Current validation status

Validated on 2026-03-26:

- `mvn test` passes with 27 backend tests
- admin-ui Vitest suite passes with 6 frontend tests during the Docker build
- Docker Compose build succeeds
- backend starts on both HTTP `8080` and HTTPS `8443`
- admin UI serves correctly over HTTPS in the container
- unauthenticated `GET /api/auth/me` returns `401`
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`
- Flyway migrations `V1` through `V6` apply successfully

Admin UI frontend structure now follows a controller-and-components split:

- `App.jsx` owns session state, data loading, and submit handlers
- `src/components/...` contains reusable UI sections with independent CSS files
- `src/lib/...` contains formatting and API helper utilities
- `admin-ui/README.md` documents the frontend layout and test workflow
- the Google and Microsoft OAuth callback pages now support navigating back to the admin UI after in-browser code exchange
- the Google and Microsoft OAuth callback pages support copying the raw code, warn before navigating away without exchange, and auto-return to the admin UI after a 10-second countdown once exchange succeeds
- admin-ui buttons that trigger backend work now show inline loading spinners so the user gets immediate feedback during authentication, saves, polling, refresh, and OAuth start flows

Current live config issue in this workspace:

- the configured Outlook bridge still fails token refresh with Microsoft `AADSTS65001 consent_required`
- that is a provider consent/config issue, not a startup/runtime wiring failure
