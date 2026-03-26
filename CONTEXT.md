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

- env-managed system mail fetchers from `.env`
- user-managed mail fetchers stored in PostgreSQL through the admin UI

In the current admin UI, both kinds of fetchers are shown together in the `My Email Fetchers` section, but env-managed entries are explicitly marked as read-only `.env` items and are only surfaced to the account named `admin`.
Fallback placeholder values from `application.yaml` are filtered out before that merge, so if the deployment does not actually define any `BRIDGE_SOURCES_*` values then no env-managed fetcher is surfaced.

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
- the deployment can run in multi-user mode or single-user mode through `BRIDGE_MULTI_USER_ENABLED`
- bootstrap admin is forced to change password
- the login screen no longer exposes live bootstrap-account state to unauthenticated visitors; bootstrap credentials are documented, but runtime bootstrap status is not published through a public endpoint
- users can register WebAuthn passkeys after signing in
- users can sign in later with a passkey from the login screen
- if an account has both a password and at least one passkey, the normal login flow now requires both factors in sequence
- if an account has only a passkey, the normal login button ignores any typed password and starts the passkey ceremony instead of failing
- users can remove their password entirely and keep a passkey-only account
- the last passkey on a passwordless account cannot be removed until another passkey exists or a password is set again
- admin-created users are also forced to change password
- self-registered users start as `inactive` and `unapproved`
- self-registration now starts from a focused modal workflow on the unauthenticated screen instead of leaving the request form always visible
- when single-user mode is enabled, self-registration and admin user-management endpoints are disabled and the UI hides those controls entirely
- single-user mode still keeps the rest of the admin control plane visible for the bootstrap admin, including Gmail setup, mail fetchers, security tools, and poller settings
- admins can approve, suspend, reactivate, promote, or demote users from the admin UI
- admins can reset another user's password to a temporary value and wipe that user's passkeys
- the admin password reset workflow now uses a modal dialog instead of an inline form in the user-management panel
- admins cannot remove their own admin rights
- there can be more than one admin, but the system protects the last approved active admin from being removed accidentally

System polling behavior:

- polling defaults still come from env/config
- admins can override poll enablement, poll interval, and fetch window from the admin UI
- each user can also override poll enablement, poll interval, and fetch window for that user's own UI-managed fetchers
- overrides are stored in PostgreSQL and merged at runtime with the env defaults
- clearing an override returns that field to the env default
- the scheduler now checks effective runtime settings dynamically instead of relying only on a static Quarkus cron interval
- env-managed system fetchers use the global effective settings, while DB-managed user fetchers use the owning user's effective settings
- each fetcher now has its own persisted polling state, including next poll time, cooldown-until timestamp, consecutive failure count, and last failure reason
- repeated provider failures now trigger automatic cooldown/backoff so one blocked mailbox does not cause InboxBridge to hammer that provider

### 3. Hybrid env + DB config model

Env-managed system bridges remain supported from `.env`.

DB-managed user config now stores:

- app users
- user sessions
- user passkeys
- passkey ceremonies
- admin-managed per-user Gmail destination overrides
- per-user bridge definitions
- encrypted OAuth credentials
- recent source poll events
- imported-message dedupe state
- per-user admin-ui preferences, including language

Why:

- keeps ops/bootstrap config simple
- allows the admin UI to manage user-owned bridges
- preserves explicit operator control over env-managed values
- lets user Gmail destinations inherit a shared deployment-level Google OAuth client when that is the intended operating model, while keeping the non-admin UI path as a simple connect/reconnect consent flow
- the intended default is one deployment-level Google Cloud OAuth client reused across many users; per-user Gmail client overrides exist only as an advanced admin escape hatch
- keeps the operational fetcher list unified in the UI while still separating writable DB state from read-only env state

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

The number of source messages examined on each cycle now comes from the effective polling settings, which may be overridden from the admin UI instead of always using the env default.

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

### 9. WebAuthn passkeys for admin-ui sign-in

Passkeys are implemented with:

- browser-native WebAuthn APIs in the React admin UI
- server-side ceremony verification in the Quarkus backend
- PostgreSQL-backed storage for registered credentials and short-lived ceremony requests

The admin-ui WebAuthn helper layer normalizes the backend's wrapped response shape of `{"publicKey": {...}}` before calling the browser credential APIs.

Default local relying-party configuration:

- RP ID: `localhost`
- RP name: `InboxBridge`
- allowed origin: `https://localhost:3000`

These can be overridden with:

- `BRIDGE_SECURITY_PASSKEYS_ENABLED`
- `BRIDGE_SECURITY_PASSKEY_RP_ID`
- `BRIDGE_SECURITY_PASSKEY_RP_NAME`
- `BRIDGE_SECURITY_PASSKEY_ORIGINS`
- `BRIDGE_SECURITY_PASSKEY_CHALLENGE_TTL`

Those settings are now part of the main `BridgeConfig` mapping under `bridge.security.passkeys`, so Quarkus validates them during startup like the rest of the bridge configuration.

Only public credential material is stored for passkeys. The private key never leaves the user's authenticator.

The admin UI now also supports a passwordless steady state:

- users can intentionally remove their password from the `Security` panel after at least one passkey is present
- the password panel hides the current-password field when no password is configured and lets the user set a new password again later
- passkey deletion is blocked in both backend and UI when it would remove the final sign-in method from a passwordless account

### 10. Admin-managed polling overrides

Runtime polling is now controlled by a merged settings model:

- `.env` / `application.yaml` provide defaults
- PostgreSQL stores optional system-wide overrides
- the admin UI edits only the override layer

Current override fields:

- poll enabled / disabled
- poll interval
- fetch window

The poll interval parser accepts shorthand values like `30s`, `5m`, `1h`, and `1d`, plus ISO-8601 durations like `PT5M`.

Constraints:

- minimum interval is `5s`
- fetch window must stay between `1` and `500`

This model preserves bootstrap simplicity while making the running system tunable without editing deployment files.

### 11. Per-user polling overrides and source backoff

DB-managed user fetchers now resolve polling behavior from two layers:

- deployment/global defaults and admin overrides
- optional per-user overrides stored in PostgreSQL

That lets one user poll every `2m` while another polls every `15m`, without changing the operator defaults for env-managed fetchers.

Each source also persists scheduler state in PostgreSQL:

- `nextPollAt`
- `cooldownUntil`
- `consecutiveFailures`
- `lastFailureReason`
- `lastFailureAt`
- `lastSuccessAt`

Current backoff behavior is heuristic but practical:

- rate-limit, throttling, quota, or lockout style errors trigger longer cooldowns
- auth and consent failures trigger an even longer cooldown so InboxBridge does not repeatedly hammer a blocked account
- transient network failures trigger a medium cooldown
- repeated failures increase the cooldown window with exponential growth up to a capped maximum

Manual poll requests bypass the normal interval gate, but they still respect explicit poll-disable settings and active cooldown windows.

### 12. Actionable admin-ui notifications

Authenticated notifications below the setup guide now support:

- manual dismissal
- click-to-focus behavior for the related section
- automatic closure after 10 seconds for low-priority success notices

High-importance warnings and errors remain visible until the user dismisses them.

### 13. Lightweight admin-ui internationalization

The admin UI now ships with an internal translation dictionary and a persisted user language preference.

Currently bundled locales:

- English
- French
- German
- Portuguese (Portugal)
- Portuguese (Brazil)
- Spanish

Design choices:

- translations are stored in `admin-ui/src/lib/i18n.js`
- the backend persists the selected language in `user_ui_preference.language`
- the browser also mirrors the last selected language in local storage so the login screen can reuse it before session data is loaded
- visible labels now route through the translation helper instead of mixing translated and raw JSX text
- the structure is intentionally simple so additional languages can be added without bringing in a heavier i18n framework

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

- env-managed Microsoft mail fetchers
- user-managed Microsoft mail fetchers

One Microsoft Entra app registration can be reused across many Outlook.com personal accounts as long as it supports personal Microsoft accounts. Each mailbox still consents separately and receives its own token set.

## Security posture

Implemented now:

- TLS-capable mailbox connections
- hostname verification for mailbox TLS
- HTTPS frontend
- HTTPS frontend-to-backend proxying
- secure HTTP-only same-site cookies
- WebAuthn passkey verification with server-side ceremony tracking
- role-based access control for admin/user API boundaries
- encrypted OAuth tokens at rest
- encrypted user-managed secret storage at rest
- minimal explicit Google scopes
- protocol-specific Microsoft scopes

Still missing for a higher-assurance v1:

- encrypted env-managed mailbox passwords
- KMS-backed key management and rotation
- richer metrics and audit-friendly event logs around poll cooldown/backoff decisions
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

- env-managed fetchers inside the unified email-fetcher list, clearly marked as read-only `.env` items and visible only to the account named `admin`
- user-managed fetchers
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
- pane collapse controls now use compact `+` / `-` icon buttons that match the existing visual language instead of text labels
- password changes are now accessed from the top hero/header controls, not from within the Gmail destination sidebar
- password changes now enforce confirmation, minimum length, mixed case, number, special character, and “different from current password” validation in both UI and backend
- self-service password removal and passkey deletion now require confirmation modals before any destructive account-security request is sent
- an `Add Email Fetcher` / `Edit Email Fetcher` modal instead of an always-visible inline bridge form
- that mail-fetcher modal is intentionally wider now, hides the `.env` badge for UI-managed entries, and rejects duplicate IDs both in the browser and in the backend service
- provider presets for Outlook / Hotmail / Live, Gmail, Yahoo Mail, and Proton Mail Bridge
- fetcher forms that hide OAuth-only fields during password auth and hide password fields during OAuth setup
- inline help-tooltips on fetcher and poller fields so the purpose of each control is visible in the UI
- prefilled Gmail redirect URIs and shared-client guidance on the user Gmail settings page
- saved-credential status for the user Gmail destination, including a clean split between deployment-shared Google client availability, user-specific client overrides, and OAuth refresh tokens stored in the encrypted credential table
- non-admin users now only see a simplified Gmail destination status panel and cannot edit advanced Gmail destination overrides from the UI or backend API
- copy-to-clipboard actions on UI error surfaces that show API payloads
- a header-level security area for password changes and passkey registration/removal
- a passkey sign-in button on the login screen
- admin password-reset and passkey-wipe controls in the selected-user panel
- the admin password-reset dialog now displays the temporary-password policy checklist inline instead of only validating silently on submit
- admin actions that suspend/reactivate users or force password changes now also require confirmation modals so high-impact identity changes are never one-click
- the Google and Microsoft OAuth callback pages now attempt the in-browser token exchange automatically on load, while still leaving the manual exchange button available for retry
- the Google callback page now also falls back to parsing `window.location.search` directly for `code` and `state`, which makes the browser flow more resilient when the callback HTML was rendered without those values
- both provider callback flows now surface consent-denied and missing-scope cases with retry guidance instead of leaving the user with a generic exchange failure
- the old env-bridge dashboard section has been reframed as `Poller Settings`, which now focuses on runtime polling controls plus health metrics instead of listing env-managed fetchers there

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
- `service/PasskeyService.java`
- `service/UserGmailConfigService.java`
- `service/UserBridgeService.java`
- `service/RuntimeBridgeService.java`
- `service/GoogleOAuthService.java`
- `service/MicrosoftOAuthService.java`
- `service/PollingService.java`
- `web/AuthResource.java`
- `web/AccountResource.java`
- `web/UserConfigResource.java`
- `web/UserManagementResource.java`
- `web/AdminResource.java`
- `persistence/UserPasskey.java`
- `persistence/PasskeyCeremony.java`

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
├── V6__user_approval.sql
├── V7__user_ui_preference.sql
├── V8__passkeys.sql
├── V9__optional_password.sql
├── V10__system_polling_setting.sql
├── V11__passkey_login_and_language.sql
└── V12__user_polling_and_source_backoff.sql
```

## Current validation status

Validated on 2026-03-26:

- `mvn test` passes
- admin-ui Vitest suite passes during the Docker build
- Docker Compose build succeeds
- backend starts on both HTTP `8080` and HTTPS `8443`
- admin UI serves correctly over HTTPS in the container
- unauthenticated `GET /api/auth/me` returns `401`
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`
- Flyway migrations `V1` through `V12` apply successfully

Admin UI frontend structure now follows a controller-and-components split:

- `App.jsx` owns session state, data loading, and submit handlers
- `src/components/...` contains reusable UI sections with independent CSS files
- `src/lib/...` contains formatting and API helper utilities
- `admin-ui/README.md` documents the frontend layout and test workflow
- the Google and Microsoft OAuth callback pages now support navigating back to the admin UI after in-browser code exchange
- the Google and Microsoft OAuth callback pages support copying the raw code, automatically attempt the exchange on load, warn before navigating away without exchange, and auto-return to the admin UI after a 10-second countdown once exchange succeeds
- admin-ui buttons that trigger backend work now show inline loading spinners so the user gets immediate feedback during authentication, saves, polling, refresh, and OAuth start flows

Current live config issue in this workspace:

- the configured Outlook bridge still fails token refresh with Microsoft `AADSTS65001 consent_required`
- that is a provider consent/config issue, not a startup/runtime wiring failure
