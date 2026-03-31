# InboxBridge Context

## Purpose

InboxBridge is a self-hosted mail importer that pulls mail from external IMAP / POP3 accounts and imports it into a destination mailbox. It can target Gmail through the Gmail API or IMAP APPEND destinations such as Outlook and other known providers. It is meant to preserve the “one inbox, many other accounts” workflow without relying on SMTP forwarding.

## Context maintenance

`CONTEXT.md` is the source of truth for cross-chat memory in this repository.
When meaningful behavior, architecture, validation expectations, or known
runtime constraints change, update this file so future chats can resume from it
without depending on older ad-hoc handoff notes.

## Current product shape

InboxBridge now consists of:

- a Quarkus backend
- a separate React admin UI
- PostgreSQL for durable state
- Docker Compose wiring for backend, frontend, database, and local TLS bootstrap

The app supports two source-email-account sources of truth:

- env-managed system source email accounts from `.env`
- user-managed source email accounts stored in PostgreSQL through the admin UI

In the current admin UI, both kinds of source email accounts are shown together in the `My Source Email Accounts` section, but env-managed entries are explicitly marked as read-only `.env` items and are only surfaced to the account named `admin`.
Fallback placeholder values from `application.yaml` are filtered out before that merge, so if the deployment does not actually define any `MAIL_ACCOUNT_*` values then no env-managed source email account is surfaced.
User-scoped source-email-account actions now resolve only DB-managed accounts, while env-managed accounts are handled exclusively through the admin endpoints. New DB-managed source email accounts are also rejected if their ID collides with an env-managed account ID.

Deployment-wide browser callback defaults can now be anchored on `PUBLIC_BASE_URL`, which feeds the default Google and Microsoft OAuth redirect URIs shown in the UI and docs.

## Technical stack

- Java 25
- Quarkus 3.33.1 (LTS)
- React 19 + Vite 7
- PostgreSQL 16
- Flyway
- Hibernate ORM Panache
- Angus Mail
- Java `HttpClient`
- Docker Compose

## Main architectural decisions

### 1. Separate frontend and backend

The React admin UI lives in `admin-ui/` and runs in its own container/server. It communicates with the Quarkus backend through proxied REST endpoints under `/api/...`.

`admin-ui/src/App.jsx` still owns bootstrap data loading and top-level workspace composition, but larger imperative slices are now split into focused controller hooks under `admin-ui/src/lib/`, including auth/security flows, source email accounts, destination mailbox flows, polling flows, and admin user management.

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
- the deployment can run in multi-user mode or single-user mode through `MULTI_USER_ENABLED`
- bootstrap admin is forced to change password
- the login screen no longer exposes live bootstrap-account state to unauthenticated visitors; bootstrap credentials are documented, but runtime bootstrap status is not published through a public endpoint
- users can register WebAuthn passkeys after signing in
- passkey registration now happens in a dedicated modal dialog so the security panel stays compact instead of rendering a tall inline form
- users can sign in later with a passkey from the login screen
- if an account has both a password and at least one passkey, the normal login flow now requires both factors in sequence
- if an account has only a passkey, the normal login button ignores any typed password and starts the passkey ceremony instead of failing
- users can remove their password entirely and keep a passkey-only account
- the last passkey on a passwordless account cannot be removed until another passkey exists or a password is set again
- repeated failed sign-ins are now tracked per client IP address and use an exponential lockout that starts at the configured initial block and doubles until the configured maximum block duration
- those login/self-registration abuse-protection defaults can now also be overridden live from `Administration -> Authentication Security`; `.env` remains the startup default and PostgreSQL stores only the deployment override values
- the `Edit Authentication Security` modal now mirrors the grouped-card style used by the polling editor, splitting login protection, registration protection, Geo-IP provider chain, Geo-IP timing, provider-specific configuration, and effective-value summary into separate visual blocks
- admin-created users are also forced to change password
- self-registered users start as `inactive` and `unapproved`
- self-registration now starts from a focused modal workflow on the unauthenticated screen instead of leaving the request form always visible
- self-registration now also loads a short anti-robot challenge before submission; challenges are stored server-side with a TTL and must be answered correctly before the pending account is created
- when single-user mode is enabled, self-registration and admin user-management endpoints are disabled and the UI hides those controls entirely
- single-user mode still keeps the rest of the admin control plane visible for the bootstrap admin, including destination mailbox setup, source email accounts, security tools, and poller settings
- switching to single-user mode from the admin UI now deactivates every account except the acting admin and records which accounts were disabled by that mode change, so re-enabling multi-user mode can reactivate those accounts later
- admins can approve, suspend, reactivate, promote, or demote users from the admin UI
- admins can reset another user's password to a temporary value and wipe that user's passkeys
- admins can now also delete any other user account, but cannot delete themselves from the admin UI
- the admin password reset workflow now uses a modal dialog instead of an inline form in the user-management panel
- user creation now uses a dedicated modal dialog instead of an inline form inside the admin section
- the create-user UI applies duplicate-username checks and the same password-policy checklist used elsewhere in the application
- the user-management list now uses expandable user entries with a contextual `...` action menu, rather than a separate side-panel inspector
- admins cannot remove their own admin rights
- there can be more than one admin, but the system protects the last approved active admin from being removed accidentally

System polling behavior:

- polling defaults still come from env/config
- admins can override poll enablement, poll interval, and fetch window from the admin UI
- each user can also override poll enablement, poll interval, and fetch window for that user's own UI-managed source email accounts
- each individual source email account can now override poll enablement, poll interval, and fetch window on top of those inherited settings
- overrides are stored in PostgreSQL and merged at runtime with the env defaults
- clearing an override returns that field to the env default
- the scheduler now checks effective runtime settings dynamically instead of relying only on a static Quarkus cron interval
- env-managed system source email accounts use the global effective settings, while DB-managed user source email accounts use the owning user's effective settings
- source-level overrides take precedence over both per-user and global polling settings
- the admin UI now splits polling into admin-only `Global Poller Settings` and user-scoped `My Poller Settings`
- `Global Poller Settings` now focuses on effective deployment-wide polling controls, while a separate `Global Statistics` section shows deployment-wide analytics across all users
- `Global Poller Settings` now keeps only the effective summary in-page and opens a dedicated modal dialog for editing the deployment-wide overrides
- that admin polling modal now includes the host/provider hardening settings as overrides too: source-host spacing, source-host concurrency, destination-provider spacing, destination-provider concurrency, throttle lease TTL, adaptive throttle ceiling, success-jitter ratio, and max success jitter
- each of those hardening fields now has an inline help hint, and the modal includes a longer explanation block describing how the scheduler uses them together to avoid hammering shared mailbox hosts and destination providers
- the admin polling modal now groups those controls into human-oriented subsections so scheduler defaults, manual-run limits, source pacing, destination pacing, adaptive recovery, and effective values are easier to scan together
- `My Poller Settings` now focuses on the current user's effective polling overrides, while a separate `My Statistics` section shows analytics scoped only to that user's own imported mail
- the user poller section now presents a compact effective-settings summary in-page and opens a dedicated modal dialog when the user wants to edit overrides
- expanded source-email-account cards now also render analytics scoped only to that single source email account
- expanded admin user cards now also render analytics scoped only to that selected user
- source-email-account statistics intentionally avoid deployment-wide account counters like `healthy accounts` and instead focus on source-scoped values such as imported totals, error polls, and manual-vs-scheduled poll activity
- all polling statistics charts now expose a `Custom` date-time range flow that opens a modal dialog, requires a `from` value, and defaults `to` to the current time when it is omitted
- statistics charts now use multi-series line charts with preset ranges like today, yesterday, past week, past month, past trimester, past semester, and past year, derived from persisted `imported_at` timestamps and recent poll-event history
- polling statistics now also expose provider mix, current health buckets, duplicate trends, error trends, manual-vs-scheduled runs, and average poll duration
- nested statistics sections inside expanded source-email-account cards and expanded admin user cards are independently collapsible, and should default to collapsed when there is no meaningful data to display yet
- the admin UI now separates admin users into `My InboxBridge` and `Administration` workspaces so personal account setup is distinct from deployment-wide controls
- those workspaces are now route-backed in the browser, so `/` stays on the `My InboxBridge` workspace, `/admin` opens the administration workspace directly, translated admin slugs such as `/administracao` remain supported, and older explicit user-workspace slugs are normalized back to `/`
- the movable content sections inside each workspace now support per-account reordering, while the header and workspace switcher stay fixed
- the movable workspace sections can now also be rearranged by drag-and-drop when the user enables layout editing from `Preferences`, and a dotted placeholder shows the drop position
- reconnecting Gmail to the same already-linked account should not revoke that account's Google grant; replacement and revocation only happen when the user actually links a different Gmail account
- the frontend layout now includes explicit responsive behavior for small screens, especially for hero actions, section headers, mail-account and user list rows, modal dialogs, and metric/stat cards
- when Gmail API access is manually revoked outside InboxBridge, a confirmed repeated Gmail `401` now clears the saved Gmail OAuth link for that user and should cause the UI to show the Gmail account as no longer linked
- the preferences model now also stores dismissible quick-setup state, a persisted layout-edit toggle, and separate user/admin workspace section order
- statistics rendering now uses `Recharts 3.x`, giving the polling dashboards shared hover tooltips and more maintainable chart behavior than the previous custom SVG chart
- the frontend package set is intentionally kept on current stable major versions, including React 19, Vite 7, Vitest 3, and Recharts 3.x
- the frontend now also ships a dedicated `/remote` remote-control route with a tiny mobile-first polling UI, its own manifest/service-worker assets, and source-level poll actions without opening the full admin workspace
- the main `My InboxBridge` workspace now also includes a dedicated `Remote control` launch card so the lightweight remote page is discoverable from the normal dashboard
- reusable admin-ui primitives now include a shared `SectionCard` shell for non-collapsible panels, a shared `CollapsibleSection` shell for standard workspace/admin sections with the corner-toggle UX, and a shared `ButtonLink` component for navigational CTA actions, so new sections do not need one-off layout/button styling
- the contextual menu trigger used in user/mail-account lists now renders as a hamburger menu icon instead of a visible `...` text label
- that hamburger icon now uses one shared global style so both lists render the same equal-width menu glyph
- each fetcher now has its own persisted polling state, including next poll time, cooldown-until timestamp, consecutive failure count, and last failure reason
- repeated provider failures now trigger automatic cooldown/backoff so one blocked mailbox does not cause InboxBridge to hammer that provider
- polling now fails early with a clear `Gmail account is not linked` error when a source depends on Gmail import but the current account has unlinked Gmail
- the per-user poller settings card uses the same padded section shell as the global dashboard cards so the form layout stays visually aligned
- the fetcher contextual `...` menu now supports running one specific fetcher immediately and opening a source-specific poller settings dialog
- the fetcher running-state badge now keeps a clearly visible spinner aligned beside the `Running` label
- the add/edit mail-fetcher dialog now has a connection test action that validates the entered IMAP/POP3 settings, including password and Microsoft OAuth2 authentication, and returns structured protocol / endpoint / TLS / authentication / mailbox-reachability diagnostics before save
- those fetcher connection diagnostics are rendered beneath the dialog action row, and the shared modal shell now constrains itself to the viewport with internal scrolling so action buttons remain reachable
- env-managed fetchers route those per-fetcher poller actions through admin-only endpoints, while UI-managed fetchers use user-scoped endpoints
- the new `/api/remote/...` surface exposes the same polling engine through a narrower remote-scoped auth model, with separate session cookies, CSRF protection for browser writes, remote-specific rate limiting, and optional bearer service-token auth mapped to a real InboxBridge account
- IMAP raw-message materialization now retries once after a `FolderClosedException` by reopening the folder and reacquiring the message before the whole fetch attempt is treated as failed
- Microsoft IMAP/POP connects now retry once with a freshly refreshed access token if Outlook rejects the cached token as invalid before its stored expiry time
- busy poll results now include metadata about the currently running trigger/source so the UI does not only show a generic `A poll is already running` message
- floating notifications now use compact icon-only copy actions with tooltip text and increase opacity on hover so long payloads stay readable without taking extra horizontal space
- compact `...` action buttons now use a fixed square footprint, and shared button styling prevents label text from wrapping onto a second line
- the floating notification stack now also uses a stronger default opacity and a subtle backdrop blur so notifications stay readable even over visually busy sections of the dashboard
- mail-fetcher detail data is refreshed automatically after manual poll attempts and when the user expands a fetcher row, so the visible status no longer waits only for the periodic dashboard refresh
- expanding any major collapsible admin-ui section now triggers a fresh reload for that section and shows an inline loading indicator while the refresh is happening
- expanding an individual user entry now refreshes the latest user list/configuration data as part of that expansion flow, while the row shows loading feedback
- destination mailbox preset descriptions, destination test-connection actions, and admin destination-section labels are localized across the supported admin-ui locales instead of falling back to English strings from preset metadata
- expanded admin user cards also defensively render partial configuration payloads so missing sub-objects do not blank the page while the latest data is loading
- IMAP fetch materialization now snapshots UID / Message-ID / timestamp metadata before reading the raw MIME payload so an already-invalidated folder does not fail a message after the bytes were successfully read

### 3. Hybrid env + DB config model

Env-managed system email accounts remain supported from `.env`.

DB-managed user config now stores:

- app users
- user sessions
- user passkeys
- passkey ceremonies
- admin-managed per-user Gmail account overrides
- per-user email account definitions
- encrypted OAuth credentials
- recent source poll events
- imported-message dedupe state
- per-user admin-ui preferences, including language

Why:

- keeps ops/bootstrap config simple
- allows the admin UI to manage user-owned email accounts
- preserves explicit operator control over env-managed values
- lets user Gmail accounts inherit a shared deployment-level Google OAuth client when that is the intended operating model, while keeping the non-admin UI path as a simple connect/reconnect consent flow
- users can now also unlink their Gmail account from the admin UI, which removes InboxBridge's stored Gmail OAuth tokens and attempts a Google-side token revocation when the token is available
- reconnecting Gmail now warns that the currently linked Gmail account will be replaced, and a successful Google OAuth exchange reports when the previous linked account was automatically replaced and whether its older Google grant was revoked
- if that Google-side revocation fails, the admin UI now gives the user the manual cleanup path: `myaccount.google.com -> Security -> Manage third-party access -> InboxBridge -> Delete All Connections`
- the intended default is one deployment-level Google Cloud OAuth client reused across many users; per-user Gmail client overrides exist only as an advanced admin escape hatch
- keeps the operational fetcher list unified in the UI while still separating writable DB state from read-only env state

Gmail API integration now retries once after a `401` by clearing the cached Google access token and refreshing it again before surfacing the error, so transient stale-token failures during label lookup or message import do not force an unnecessary reconnect.

### 4. Gmail `users.messages.import`

The destination side still uses Gmail `users.messages.import`.

Why:

- preserves raw MIME better than SMTP forwarding
- avoids sender reputation issues
- keeps labels and import metadata under app control

### 5. Runtime email-account resolution

`RuntimeEmailAccountService` combines:

- enabled env-defined system email accounts
- enabled DB-defined user email accounts with valid destination mailbox config

Polling runs over that combined runtime set.

The number of source messages examined on each cycle now comes from the effective polling settings, which may be overridden from the admin UI instead of always using the env default.

### 6. Destination-scoped dedupe

Imported messages are now deduped by Gmail account as well as source identity.

Current checks:

1. `(destinationKey, sourceAccountId, sourceMessageKey)`
2. `(destinationKey, rawSha256)`

Why:

- avoids cross-user dedupe collisions
- allows two different users to import the same raw MIME into two different Gmail accounts

### 7. Encrypted secret storage

When `SECURITY_TOKEN_ENCRYPTION_KEY` is configured, InboxBridge encrypts:

- Google OAuth tokens
- Microsoft OAuth tokens
- user-managed Gmail client IDs, client secrets, and refresh tokens
- user-managed source-email-account passwords and refresh tokens

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

- `SECURITY_PASSKEYS_ENABLED`
- `SECURITY_PASSKEY_RP_ID`
- `SECURITY_PASSKEY_RP_NAME`
- `SECURITY_PASSKEY_ORIGINS`
- `SECURITY_PASSKEY_CHALLENGE_TTL`

Those settings are now part of the main `InboxBridgeConfig` mapping under `inboxbridge.security.passkeys`, so Quarkus validates them during startup like the rest of the application configuration.

Only public credential material is stored for passkeys. The private key never leaves the user's authenticator.

The admin UI now also supports a passwordless steady state:

- users can intentionally remove their password from the `Security` panel after at least one passkey is present
- the password panel hides the current-password field when no password is configured and lets the user set a new password again later
- passkey deletion is blocked in both backend and UI when it would remove the final sign-in method from a passwordless account
- the password-visibility toggle now overrides the shared button hover transform so the eye icon stays visually fixed inside the input while hovering or focusing it

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
- successful polls also add deterministic per-source jitter to the next run so large fleets do not naturally re-align on the same exact boundary again after succeeding

Manual poll requests now split into two behaviors:

- single-source manual runs bypass the normal interval gate and cooldown window for that one selected mail account
- broader manual runs (`My Polling Settings` for one user, or `Global Polling Settings` for all users) also bypass cooldown and next-window checks, but they are still protected by an admin-configurable manual rate limit that defaults to 5 runs per 60 seconds per signed-in user

Polling-scale hardening now adds a persisted protection layer:

- polls against the same source host are spaced apart by a configurable minimum gap (`SOURCE_HOST_MIN_SPACING`, default `PT1S`)
- polls against the same source host are also capped by a configurable concurrency limit (`SOURCE_HOST_MAX_CONCURRENCY`, default `2`)
- destination deliveries for the same provider/mode are also spaced apart by a configurable minimum gap (`DESTINATION_PROVIDER_MIN_SPACING`, default `PT0.25S`)
- destination deliveries for the same provider/host are also capped by a configurable concurrency limit (`DESTINATION_PROVIDER_MAX_CONCURRENCY`, default `1`)
- short-lived throttle leases are persisted in PostgreSQL so in-flight work can be bounded even if the app restarts unexpectedly, with a configurable lease TTL (`THROTTLE_LEASE_TTL`, default `PT2M`)
- persisted adaptive multipliers now widen spacing after host/provider contention and after throttling-style provider failures, up to a configurable ceiling (`ADAPTIVE_THROTTLE_MAX_MULTIPLIER`, default `6`)
- admins can now override all of those hardening defaults live from the `Global Poller Settings` modal without editing `.env`, and the UI shows default, override, and effective values together
- this is still one shared-database coordination layer, not a full distributed quota system; true cluster-wide provider budgeting and smarter queueing are still future work

### 12. Actionable admin-ui notifications

Authenticated notifications below the setup guide now support:

- manual dismissal
- click-to-focus behavior for the related section
- automatic closure after 10 seconds for low-priority success notices
- floating viewport-level rendering so feedback remains visible while the user is scrolled away from the related section

### 13. Remote control surface

InboxBridge now includes a dedicated quick-access polling surface at `/remote`.

Key behavior:

- it uses the same InboxBridge identity and passkey/password login rules as the main app, but it mints a separate remote-only session instead of reusing the broad admin-ui session
- remote browser writes require both the remote session cookie and a matching CSRF header/cookie pair
- remote endpoints can also accept an optional bearer service token when `SECURITY_REMOTE_SERVICE_TOKEN` and `SECURITY_REMOTE_SERVICE_USERNAME` are configured
- service-token auth is intentionally mapped onto a real InboxBridge user account so source visibility and admin powers still follow the existing role model
- remote source lists are intentionally action-oriented: they focus on sources the current actor can actually poll right now rather than duplicating the full admin editing experience
- the remote page is installable as a small PWA and is meant for quick mobile or secondary-device access
- remote summaries, poll intervals, cooldown windows, and run timestamps should be rendered with user-friendly localized date/time and duration formatting instead of exposing raw ISO-8601 values
- the remote page must also verify the signed-in account has a ready personal destination mailbox plus at least one personal source email account; otherwise it should replace poll actions with a setup prompt that links back to `My InboxBridge`
- remote source cards should start collapsed by default, keep the per-source poll button visible in the collapsed state, and stay responsive across narrow mobile widths, tablets, and desktop browsers
- when a revoked or expired remote session forces `/remote` back to login, the login card should explain that the user was signed out instead of appearing without context
- remote-trigger requests have their own rate limiter on top of the existing manual poll, cooldown/backoff, and host/provider throttle protections in `PollingService`

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
- expanded user-management entries are grouped into explicit subsections for user configuration, Gmail account, poller settings, passkeys, and source email accounts
- the most prominent labels inside those subsection bodies now follow the selected language too instead of remaining in English
- quick-setup guidance, Gmail account controls, poller-setting forms, and source-email-account forms/lists now have broader locale coverage so changing language updates section bodies as well as headings
- translation regression coverage now includes localized rendering tests for the major admin-ui surfaces plus a critical-key catalog test in `admin-ui/src/lib/i18n.test.js`
- password-policy checklists and normalized passkey failure/cancellation messages are now translated too, instead of depending on raw browser English
- expandable list rows rely on row click plus hover affordance for expansion, so contextual `...` menus focus only on actions and no longer repeat expand/collapse controls
- the `...` contextual menus in both the mail-fetcher and user-management lists measure the real floating panel and use viewport-aware placement so they stay attached to the trigger button while scrolling without extending the page layout off-screen; if the trigger leaves the viewport, the open menu closes instead of lingering detached
- the structure is intentionally simple so additional languages can be added without bringing in a heavier i18n framework
- shared button styling now includes clearer hover, focus, and pressed states so actions read as interactive without needing a component framework

## OAuth model

### Google

Google OAuth is used for Gmail accounts:

- env-managed shared Gmail account
- per-user Gmail account stored in PostgreSQL

Per-user Gmail accounts can either:

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

Microsoft OAuth is used for Outlook source mailboxes.

Supported now:

- env-managed Microsoft source email accounts
- user-managed Microsoft source email accounts

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
- processes email accounts sequentially

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
- per-email-account import totals
- last poll result
- OAuth launch buttons
- self-registration on the login screen
- user list and user config summaries for admins
- admin approval / suspension / role-management controls
- a top-level setup guide panel that explains the first-run sequence in the admin UI
- setup guide entries are clickable links that focus the relevant section where the user must take action
- setup guide entries now use neutral / green / red visual state based on pending work, successful completion, and recorded email-account/provider errors
- when all tracked setup steps are complete, the guide auto-collapses by default
- the major admin-ui sections can be collapsed, and users can opt into per-account persisted section state across login sessions
- pane collapse controls now use compact `+` / `-` icon buttons that match the existing visual language instead of text labels
- password changes are now accessed from the top hero/header controls, not from within the Gmail account sidebar
- password changes now enforce confirmation, minimum length, mixed case, number, special character, and “different from current password” validation in both UI and backend
- self-service password removal and passkey deletion now require confirmation modals before any destructive account-security request is sent
- an `Add Email Fetcher` / `Edit Email Fetcher` modal instead of an always-visible inline email-account form
- that mail-fetcher modal is intentionally wider now, hides the `.env` badge for UI-managed entries, and rejects duplicate IDs both in the browser and in the backend service
- provider presets for Outlook, Gmail, Yahoo Mail, and Proton Mail Bridge
- fetcher forms that hide OAuth-only fields during password auth and hide password fields during OAuth setup
- inline help-tooltips on fetcher and poller fields so the purpose of each control is visible in the UI
- prefilled Gmail redirect URIs and shared-client guidance on the user Gmail settings page
- saved-credential status for the user Gmail account, including a clean split between deployment-shared Google client availability, user-specific client overrides, and OAuth refresh tokens stored in the encrypted credential table
- non-admin users now only see a simplified Gmail account status panel and cannot edit advanced Gmail account overrides from the UI or backend API
- copy-to-clipboard actions on UI error surfaces that show API payloads
- a header-level security area for password changes and passkey registration/removal
- a passkey sign-in button on the login screen
- admin password-reset and passkey-wipe controls in the selected-user panel
- the admin password-reset dialog now displays the temporary-password policy checklist inline instead of only validating silently on submit
- admin actions that suspend/reactivate users or force password changes now also require confirmation modals so high-impact identity changes are never one-click
- the Google and Microsoft OAuth callback pages now attempt the in-browser token exchange automatically on load, while still leaving the manual exchange button available for retry
- the Google and Microsoft callback pages now also fall back to parsing `window.location.search` directly for `code` and `state`, which makes the browser flow more resilient when the callback HTML was rendered without those values
- the Microsoft callback exchange endpoint now returns a structured JSON error body, so the callback page can display the real backend failure reason instead of a blank generic `Exchange failed:` message
- Microsoft OAuth mailbox-scope validation now treats the protocol mailbox scope plus the returned refresh token as the real success signal, rather than requiring `offline_access` to appear in the echoed scope string
- when secure token storage is not configured, a successful Microsoft OAuth exchange for an env-managed source still requires copying the returned `MAIL_ACCOUNT_<n>__OAUTH_REFRESH_TOKEN` value into `.env` and restarting before the poller can use it
- when secure token storage is configured and a newer Microsoft refresh token has been stored successfully, the dashboard suppresses older stale `has no refresh token` source errors for that same source email account
- UI-managed Microsoft source email accounts also reuse the encrypted OAuth credential store by email account ID, so their runtime token lookup no longer depends on a duplicated refresh token copy being present on the `user_email_account` row
- both provider callback flows now surface consent-denied and missing-scope cases with retry guidance instead of leaving the user with a generic exchange failure
- once the user confirms a `Return to InboxBridge` leave action before exchange, the callback page suppresses the browser's second generic unsaved-changes prompt so the user is not asked twice
- the old env-managed email-account dashboard section has been reframed as admin-only `Global Poller Settings`, which now focuses on runtime polling controls plus health metrics instead of listing env-managed fetchers there

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
- `service/UserMailDestinationConfigService.java`
- `service/UserGmailConfigService.java`
- `service/UserEmailAccountService.java`
- `service/RuntimeEmailAccountService.java`
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
    │   ├── emailAccounts
    │   ├── common
    │   ├── destination
    │   ├── emailAccounts
    │   ├── gmail
    │   ├── layout
    │   ├── polling
    │   └── stats
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
├── V12__user_polling_and_source_backoff.sql
├── V13__source_polling_setting.sql
├── V14__user_ui_preference_stats_sections.sql
├── V15__user_ui_preference_layout_order.sql
├── V16__user_ui_preference_layout_edit_enabled.sql
├── V17__system_oauth_app_settings.sql
├── V18__user_ui_preference_quick_setup_pinned_visible.sql
├── V19__system_oauth_app_settings_google_fields.sql
├── V20__user_ui_preference_oauth_apps_collapsed.sql
├── V21__system_oauth_app_settings_multi_user_override.sql
├── V22__user_ui_preference_admin_quick_setup_collapsed.sql
├── V23__system_polling_setting_manual_trigger_rate_limit.sql
├── V24__app_user_single_user_mode_flag.sql
├── V25__user_mail_destination_config.sql
├── V26__rename_user_bridge_to_user_email_account.sql
├── V27__rename_ui_preference_email_account_columns.sql
├── V28__user_gmail_linked_mailbox_address.sql
└── V29__poll_throttle_state.sql
```

## Current validation status

Validated on 2026-03-30:

- targeted backend polling tests pass, including `PollingServiceTest`, `SourcePollingStateServiceTest`, and `PollThrottleServiceTest`
- targeted frontend notification tests pass, including `src/lib/notifications.test.js`
- admin-ui Docker build succeeds with the Vitest suite enabled in-container
- `docker compose up --build -d` succeeds
- backend starts on both HTTP `8080` and HTTPS `8443`
- admin UI serves correctly over HTTPS in the container
- unauthenticated `GET /api/auth/me` returns `401`
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`
- Flyway migrations `V1` through `V29` apply successfully
- direct backend startup works with the current polling pacing defaults because `DESTINATION_PROVIDER_MIN_SPACING` now uses the valid ISO-8601 duration `PT0.25S`

Admin UI frontend structure now follows a controller-and-components split:

- `App.jsx` owns session state and shared data loading
- `src/lib/useEmailAccountsController.js` owns source email account orchestration
- `src/lib/useAuthSecurityController.js` owns session auth, password, and passkey flows
- `src/lib/useDestinationController.js` owns destination mailbox actions
- `src/lib/usePollingControllers.js` owns user/global polling dialog and run flows
- `src/components/...` contains reusable UI sections with independent CSS files
- `src/lib/...` contains formatting and API helper utilities
- `admin-ui/README.md` documents the frontend layout and test workflow
- the Google and Microsoft OAuth callback pages now support navigating back to InboxBridge after in-browser code exchange
- the Google and Microsoft OAuth callback pages support copying the raw code, automatically attempt the exchange on load, warn before navigating away without exchange, and auto-redirect to InboxBridge after a 5-second countdown once exchange succeeds unless the user cancels that automatic redirect
- admin-ui buttons that trigger backend work now show inline loading spinners so the user gets immediate feedback during authentication, saves, polling, refresh, and OAuth start flows
- source-email-account notifications are now stored as structured descriptors and resolved at render time, so changing the UI language also re-translates existing notifications instead of leaving them in the previous language
- source-specific notification links can now focus the matching source email account card in the user workspace, including OAuth-related error notifications such as a missing refresh token
- the add/edit source email account dialog now supports IMAP folder discovery after a successful test connection and while editing an existing IMAP account, mirroring the destination mailbox folder-selection flow
- the frontend production bundle now uses explicit manual chunking for React/vendor, router, i18n, and chart code, which removed the earlier Vite large-chunk build warning
- polling now includes a first scaling-hardening layer: deterministic per-source success jitter, per-instance minimum spacing between polls to the same source host, and per-instance minimum spacing between deliveries to the same destination provider/host
- those scaling-hardening knobs are configured through `SOURCE_HOST_MIN_SPACING`, `SOURCE_HOST_MAX_CONCURRENCY`, `DESTINATION_PROVIDER_MIN_SPACING`, `DESTINATION_PROVIDER_MAX_CONCURRENCY`, `THROTTLE_LEASE_TTL`, `ADAPTIVE_THROTTLE_MAX_MULTIPLIER`, `SUCCESS_JITTER_RATIO`, and `MAX_SUCCESS_JITTER`
- the current throttling/jitter protection is per app instance only; true cluster-wide coordination, global provider quotas, and distributed locking are still future work for multi-node deployments

Current live config issue in this workspace:

- the configured Outlook email account still fails token refresh with Microsoft `AADSTS65001 consent_required`
- that is a provider consent/config issue, not a startup/runtime wiring failure
- Password removal is a confirmed passkey-only transition: the admin UI requires the current password before enabling removal, and the backend verifies that password again before clearing the stored hash.
- The Quick Setup Guide now auto-collapses immediately once all tracked setup steps are complete.
- Once all setup steps are complete, the guide can also be hidden entirely; if any step later becomes invalid again, the guide is shown again automatically.
- The Quick Setup Guide now says `Add at least one email account`, and its provider OAuth step is only rendered when at least one configured source account actually uses OAuth.
- The Quick Setup Guide now renumbers visible steps dynamically, so conditional steps never leave numbering gaps.
- The `Administration` workspace now keeps its own admin-specific quick setup guide centered on shared Google OAuth, user creation in multi-user mode, and verifying the first successful import.
- In the `Administration` workspace, the Google OAuth app editor is now limited to configuring the shared Google Cloud OAuth client registration; mailbox Gmail OAuth consent still happens per user from `My Destination Mailbox`.
- Language selection, layout persistence, and reset-layout controls now live in a dedicated preferences modal opened from the header instead of an always-visible inline selector.
- The admin UI now enforces HTTPS both through nginx (`80 -> 443`) and with a frontend-side upgrade guard if the app is ever served over plain HTTP by another deployment path.
- The header `Security` action now opens the password, passkey, and session tools in a dedicated modal with separate tabs, instead of rendering both tools inline in the page.
- The new `Sessions` tab shows recent successful sign-ins, active sessions, the login method used, and sign-out actions for one other session or all other sessions for the current account.
- The `Sessions` tab now also includes `/remote` remote-control sessions beside normal admin-ui sessions, labels the session type explicitly, and treats revoke-all-others as applying to both session stores.
- Client IP capture for auth and remote-session activity should prefer the direct socket address unless the request came through a local/private trusted proxy hop; in that trusted-proxy case the app may use `CF-Connecting-IP`, `True-Client-IP`, `X-Real-IP`, or forwarded headers from the proxy chain instead of blindly trusting arbitrary client-supplied `X-Forwarded-For` values.
- When a signed-in account detects a newer sign-in from another session during the normal background refresh cycle, the UI now raises a warning notification that links directly to the `Sessions` tab.
- The header `Refresh` action now runs the same session-activity poll as the background refresh cycle, so clicking it can also surface new-sign-in notifications instead of only reloading the main dashboard data.
- If one browser session is revoked from another session, the revoked browser now detects the next `401` response centrally and immediately returns to the login screen instead of staying in a broken authenticated shell.
- `user_session` rows now also persist client IP, login method, user-agent, and revocation timestamp so session history can be shown without guessing from cookies.
- Approximate session location is now resolved only on new sign-ins, never on every request. The current backend strategy is: one primary Geo-IP provider, aggressive cache-by-IP in PostgreSQL, and ordered fallback providers only when the primary is down or rate-limited. The default chain is `IPWHOIS -> IPAPI_CO -> IP_API`, with optional `IPINFO_LITE` when a token is configured. Those Geo-IP settings are now also overridable from `Administration -> Authentication Security`. If Geo-IP is not configured, the UI still shows the explicit “location unavailable” notice instead of inventing a location.
- the admin Geo-IP editor now uses a primary-provider dropdown plus a chip/tag fallback input, and each provider is represented by a readiness card that can show provider-specific configuration requirements, docs, and terms links; providers that need credentials (currently `IPINFO_LITE`) remain disabled until those secrets are configured through secure storage
- self-registration no longer relies on the old arithmetic challenge; it now uses a provider-driven CAPTCHA flow. The default provider is `ALTCHA`, which is self-hosted and works without external registration or tokens. `Cloudflare Turnstile` and `hCaptcha` are also supported and can be selected from `Administration -> Authentication Security` once their required credentials are configured.
- the `Edit Authentication Security` modal is intentionally wider than the standard settings dialogs and now mirrors the grouped-card structure used by the polling editor, including dedicated registration CAPTCHA selection/configuration sections plus the translated effective-value summary block
- The Google setup help panel is fully localized across the supported admin-ui languages.
- The Gmail account layout now collapses to a single full-width column whenever the admin-only setup sidebar is not being shown.
- The admin Gmail account form now includes inline help hints for all editable fields.
- The shared modal shell now closes only the front-most dialog on `Escape`; dialogs with dirty forms use a confirmation prompt before discarding in-progress changes.
- The Security dialog uses that dirty-dialog confirmation when the password form has in-progress input.
- Raw duration values shown in the admin polling/authentication-security summaries keep their config form visible and now expose hover hints that explain ISO-8601 values like `PT5M`, `PT1H`, and `PT0.25S` in human-readable units.
- Admin user inspection now shows the configured Gmail API user value. This is the Gmail API `userId` setting and is often `me`, so it should not be interpreted as a guaranteed literal mailbox address.
- Floating notifications now wrap long text inside the card instead of overflowing past the viewport edges.
- Per-fetcher polling now exposes a running spinner state in the fetcher status pill, the fetcher poller dialog title uses the fetcher ID directly, and OAuth2 fetchers display whether their provider connection is already established.
