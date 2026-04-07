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
UI-managed IMAP source email accounts now also persist post-poll source-message actions: the default is no source-side change, but users can opt to mark handled messages as read, mark them with the IMAP `$Forwarded` flag when the source server accepts it, delete them, or move them into another source folder after either a successful import or a duplicate match. Those actions are rejected for POP3 accounts and for move configurations without a target folder.
IMAP source email accounts now also persist a fetch mode. `POLLING` keeps the existing scheduled fetch behavior, while `IDLE` starts one long-lived IMAP IDLE watcher per configured source folder and uses persisted per-folder `UIDVALIDITY` / last-seen UID checkpoints so reconnects can resume from newer mail instead of always re-scanning only the latest fetch window. UI-managed and env-managed IMAP sources now interpret the existing folder field as a comma-separated folder list, defaulting to `INBOX` when blank, while POP3 remains effectively `INBOX`-only and is still forced to `POLLING`. After a successful save or delete of a source mailbox configuration, the watcher refreshes immediately instead of waiting for the periodic sweep, and application startup also refreshes the watcher registry right away so eligible IDLE sources reconnect during boot instead of waiting for the next 30-second scan. POP3 accounts also persist the last seen POP UIDL so later scheduled polls can resume from newer POP mail when that mailbox still advertises the prior checkpoint. Those IMAP and POP checkpoints are now scoped to a derived destination-mailbox identity instead of the source alone, so switching a user's destination mailbox does not reuse the previous destination's checkpoint and can replay already-seen source mail into the new target under the normal fetch-window rules. Import dedupe now treats protocol-native identifiers as authoritative: IMAP source keys prefer `folder + UIDVALIDITY + UID`, POP3 source keys prefer `UIDL`, raw MIME SHA-256 remains the main content-based fallback, and normalized `Message-ID` is only a final same-source same-destination heuristic fallback. Source-side IMAP post-poll actions now also reopen the fetched message's actual folder instead of assuming the source's primary folder, which keeps mark/move/delete behavior correct for multi-folder IMAP sources. Startup now also includes a legacy destination-identity reconciliation pass that backfills pre-destination-aware imported-message rows and per-source checkpoint destination keys to the current resolved mailbox identity for upgrade continuity.
The admin UI source-email-account dialog now exposes that IMAP multi-folder behavior through a reusable pillbox-style `Folder` control rather than a dialog-local widget. Users can type folder names directly into the pillbox before validation, use arrow keys plus Enter to pick detected folders from inline autocomplete suggestions after a successful connection test, and keep several selected folders visible as status-pill-style chips instead of switching between separate manual-input and dropdown modes. The floating suggestion panel is viewport-aware: it flips above or below the field depending on available space, caps its visible option count with scrolling, and closes if the anchored pillbox leaves the viewport so it never drifts detached during scrolling. On touch/mobile devices, once the dialog knows the server-backed folder list it should always keep a single visible pillbox shell and route taps through an overlaid native multi-select picker instead of rendering a second visible field below it; typed free-text pills remain available only before the server folders are known. The edit-source dialog also auto-loads remote folders when an existing IMAP source already has enough saved connection details, and the reusable pillbox now emits both focus and typing activity so the source-account controller can follow a `focus first, first-letter fallback` folder-refresh policy: fetch once per successful dialog/server signature, retry on later focus if folders are still unknown, and only use typing as a first-letter fallback when the dialog still has no folder list. Once the dialog knows the server folders, it annotates each selected folder in green or red depending on whether the server reported that folder and stops accepting arbitrary folder names so only server-known folders can be added from that point onward. Saving a source without a fresh successful connection test now raises a confirmation modal that offers either `Test Connection Now` or `Save Disabled Without Testing`, so unvalidated source settings are not left enabled silently.
Expanded source-account details in `My Source Email Accounts` and `Administration` now also surface runtime diagnostics directly in the UI: current destination identity key, POP UIDL checkpoint, per-folder IMAP checkpoints, persisted source/destination throttle state, IMAP IDLE watcher health per folder, and the latest persisted poll-decision audit details such as failure category, cooldown backoff, and throttle wait times. Those diagnostics are meant to replace most direct DB inspection when debugging source behavior.
That pillbox should keep the same neutral border in both idle and successful validated states. The border only changes when the dialog is actively surfacing a field error, using a soft red idle/focus treatment after a failed save/validation attempt, and that error styling clears again as soon as the user edits the field or the value becomes valid.
Dialog-style overlays in the admin UI should keep working as scrollable surfaces without showing the browser's default scrollbar chrome. That hidden-scrollbar treatment applies to modal editors like source/destination dialogs, Notifications, Preferences, and security/settings dialogs, while the main workspace surfaces such as `My InboxBridge`, `Administration`, and `InboxBridge Go` still keep their normal page scrollbars.
UI-managed OAuth2 source accounts now also save disabled first when they do not yet have a working refresh token, while persisting the user's intent to enable them later. Once the OAuth browser flow finishes and InboxBridge can immediately validate the source mailbox with the stored provider credential, the source enables automatically; if that post-OAuth validation still fails, the source remains disabled instead of being left enabled with invalid credentials.
The repository now also includes a standalone static GitHub Pages site under `site/`, with a plain browser-side `.env` generator aimed at env-managed/operator setup rather than replacing the normal UI-managed browser workflow. That helper can also generate the required 32-byte base64 token-encryption key directly in the browser when Web Crypto is available, and its base64 conversion must stay browser-safe instead of depending only on Node `Buffer` globals. The token-encryption-key control should stay compact on smaller screens: the key input remains the dominant field, the key ID sits beside it in a narrower column, and the generate action is an inline icon button inside the key field rather than a large text button that consumes horizontal space. The public site is now also expected to support the same locale set as the admin UI (`en`, `fr`, `de`, `pt-PT`, `pt-BR`, `es`) through a top-level flag-based language selector, with fully translated navigation, landing-page copy, architecture explanations, FAQ content, generator labels, hints, buttons, summaries, and footer links rather than leaving long-form sections to fall back to English. That language selector should keep the same compact topbar-action look and feel as the other header controls, stay visually grouped with the nav actions when the header wraps, and remain visible even when `index.html` is opened directly from disk before the JS-driven picker replaces the static fallback markup. On desktop widths, the brand/tagline block should be the part that wraps first; the nav actions and language picker should stay on one row whenever the actions themselves still fit, instead of dropping early just because the translated site tagline became two lines tall. When the tagline does wrap, both the app icon and the top menu should stay top-aligned instead of vertically centering against the taller brand block, so translated three-line taglines do not push the visual baseline of the header controls downward. On narrow mobile widths, the public-site language picker should stay pinned to the top-right corner of the header while the nav drops below it, and long translated hero titles must wrap safely without causing horizontal overflow or making the page background look shifted to one side. The public site should also advertise the runtime features that materially differentiate InboxBridge today, including POP3 UIDL checkpoints plus multi-folder IMAP polling and per-folder IMAP IDLE/checkpoint support, instead of describing the product only in older single-folder terms. The public site also includes a top-level architecture section with a lightweight animated mailflow diagram so operators can understand the source-to-core-to-destination flow without reading the full setup docs first. That animation should use moving mail icons, not anonymous pulses, and the small `Animated view` note should stay visually subordinate to the main diagram cards through smaller typography rather than a narrower container. The public site should also keep a clearly separated expandable lower-level runtime view that stays collapsed until someone wants more implementation detail; that expanded area should be a real diagram with coherent provider/runtime/storage boundaries, not just another row of plain text cards. The current intended shape is a centered vertical flow from browser surfaces to runtime coordinator to external providers to durable state, with explicit transport and storage labels between each step, so the same flow reads correctly on desktop and mobile without a special snake layout. The public site copy should make the security model explicit: InboxBridge is designed around encrypted IMAP, POP3, HTTPS, and provider-API communication, and PostgreSQL should be described as storing operational metadata, dedupe identifiers, checkpoints, notifications, settings, and encrypted secrets rather than a searchable shadow archive of mailbox content. Be careful not to overclaim that InboxBridge `never reads` message content; it processes messages in transit to import them, but should be described as not retaining its own mailbox-content archive. The public site should also highlight the self-hosting advantage in concrete deployment terms such as personal computers, homelab servers, Raspberry Pi systems, VPS instances, and dedicated hosts, always framing the advantage as keeping the runtime boundary and mailbox credentials under the operator's control instead of handing them to a third-party mailbox-forwarding SaaS. The public site also includes a production-ready FAQ section with InboxBridge-specific wording about self-hosted privacy, protocol security, import-vs-forwarding behavior, provider compatibility, reply behavior, IMAP IDLE, POP3 limits, header preservation, `$Forwarded`, and the operator-facing `.env` helper. The `$Forwarded` FAQ copy should explain what the marker is in plain language: InboxBridge can try to add it to a source message after import as a mailbox-side handled hint, but it remains a non-standard best-effort IMAP keyword rather than a universal built-in system flag. The `Worth remembering` site copy must carry that same assumption and should not mention `$Forwarded` as if the reader already knows what it is. That FAQ leads with the security/privacy positioning: the main advantage over third-party mailbox-forwarding services is that InboxBridge is self-hosted, so mailbox credentials and imported-message history stay under the owner's control. The FAQ grid should not rely on stretched equal-height rows because expanded cards must not visually drag neighboring questions taller without opening their content. The public site should also steer people toward the recommended setup path: keep `.env` minimal for bootstrap settings so mailbox passwords are not left in plain text there, then finish source and destination mailbox configuration in the application web interface unless env-managed accounts are specifically desired. The `.env` configurator now exposes its guidance through compact info-icon hints for each major field so operators get admin-ui-style help without overwhelming the page with visible explanatory text or exposing developer/debug messaging. Its optional shared OAuth and env-managed mailbox sections stay collapsed by default and should make the whole rounded summary bar feel clickable when collapsed, instead of leaving dead zones inside the bordered pill. Its IMAP folder field should now use the same pillbox-style mental model as the admin UI even though the public site stays free-text only: operators can add one or more folders there, remove individual pills, and still get the comma-separated env value that InboxBridge expects. Its source-auth conditional fields should also keep working when the page is opened directly from disk, so the basic PASSWORD vs OAUTH2 field visibility logic cannot depend only on the ES-module generator bootstrap. The site now also reuses the `/remote` PWA icon asset so the public landing page and installable remote surface share the same visual identity. The local Docker Compose stack does not serve that standalone GitHub Pages site; locally, `https://localhost:3000/` is the admin UI, while the GitHub Pages site is only published from `site/` through Pages or by opening/serving those static files separately. The repository README should advertise that public site near the top so developers landing on GitHub discover the product page immediately. GitHub Actions can deploy that site once GitHub Pages has been enabled manually for the repository and set to use GitHub Actions; the workflow should not assume the default `GITHUB_TOKEN` can create the Pages site from scratch.
Authenticated notifications are now also persisted in each user's UI-preferences record, so recent poll/security notices survive refreshes and normal sign-out/sign-in cycles instead of living only in React memory.
Authenticated workspaces now also expose an SSE-backed live-poll snapshot/event model with pause/resume/stop controls plus per-source `Move Next` and `Retry` actions while a run is active. The polling engine now executes eligible sources through a bounded virtual-thread worker pool, so unrelated mailboxes can progress concurrently while the live model tracks several `RUNNING` sources at once. Live `Stop` requests also register cancellation actions that close active mailbox sessions and interrupt worker threads, which makes stop behavior materially faster than the older checkpoint-only model.
Because those workers no longer inherit a request context from the original REST thread, any repository-backed async polling lookup now needs its own narrow `@Transactional` boundary instead of depending on ambient CDI request scope.

Deployment-wide browser callback defaults can now be driven from `PUBLIC_HOSTNAME` and `PUBLIC_PORT`, which together derive the canonical `https://${PUBLIC_HOSTNAME}:${PUBLIC_PORT}` browser URL by default. `PUBLIC_BASE_URL` still exists as an override when operators need a different scheme or public URL shape, and the same derived/overridden URL continues to feed the default Google and Microsoft OAuth redirect URIs shown in the UI and docs.
The local `cert-init` flow now derives frontend/backend SAN coverage from `PUBLIC_BASE_URL` or the derived `PUBLIC_HOSTNAME` / `PUBLIC_PORT` browser URL, plus optional `TLS_FRONTEND_CERT_HOSTNAMES` / `TLS_BACKEND_CERT_HOSTNAMES`, and it automatically reissues the generated leaf certs when the expected hostname set changes. Docker Compose also publishes the admin UI on `${PUBLIC_PORT}` instead of always hardcoding host port `3000`, so the declared public port and the exposed local port stay aligned by default.
The repository now also includes a GitHub Actions `Build` workflow at `.github/workflows/build.yml` that runs backend tests plus frontend install/test/build, so the README can show a real GitHub build-status badge instead of a placeholder.
The repository now declares Apache License 2.0 in `LICENSE`, and the root README also includes an explicit `use at your own risk` disclaimer plus a note that the project was built with AI-assisted tooling under human direction and review.
The repository now also includes standard open-source community files: `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/CODEOWNERS`, and issue templates for bug reports and feature requests, with blank public issues disabled in favor of structured templates and private security reporting.
The repository now also includes `.github/dependabot.yml` for weekly dependency update PRs across GitHub Actions, Maven, npm, and Dockerfiles, plus a `.github/workflows/release.yml` workflow that builds release assets and publishes GitHub Releases from tags matching `v*` or from a manual dispatch with a provided tag name.
The repository now also includes project-local Codex skills under `.codex/skills/` for common InboxBridge work: repo guardrails, backend changes, admin-ui changes, mailflow-safety work, and validation/release handoff. Those skills intentionally point back to `AGENTS.md`, `CONTEXT.md`, the repo docs, and the admin-ui README instead of duplicating long project references inside the skill bodies. `AGENTS.md` now also includes a portable AI-agent routing section so non-Codex agents can still follow the same repo workflow just by reading that file.

## Technical stack

- Java 25
- Quarkus 3.33.1 (LTS)
- React 19 + Vite 8
- PostgreSQL 16
- Flyway
- Hibernate ORM Panache
- Angus Mail
- Java `HttpClient`
- Docker Compose

## Main architectural decisions

### 1. Separate frontend and backend

The React admin UI lives in `admin-ui/` and runs in its own container/server. It communicates with the Quarkus backend through proxied REST endpoints under `/api/...`.

The frontend now follows a feature-first layout: `admin-ui/src/app` owns the main and remote app shells, `admin-ui/src/features/<feature>` owns feature components plus feature-local hooks/tests/styles, `admin-ui/src/shared` owns reusable UI primitives and shared hooks, `admin-ui/src/lib` is reserved for cross-feature pure utilities and API helpers, and `admin-ui/src/theme/global.css` holds the small global style layer.

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
- the unauthenticated login form should only prefill `admin` / `nimda` while the untouched bootstrap admin is still in its original first-login state; once that admin changes the password, enrolls a passkey, is removed, or otherwise leaves the bootstrap state, the form should fall back to empty credentials
- the login screen no longer exposes live bootstrap-account state to unauthenticated visitors; bootstrap credentials are documented, but runtime bootstrap status is not published through a public endpoint
- the main login screen and `/remote` now start with a username-only step, then move into a generic continue-sign-in step so the UI does not reveal whether the account is password-only, passkey-only, or password-plus-passkey before the user actually continues
- the main unauthenticated login card title is intentionally just `InboxBridge`, and the eyebrow is suppressed when it would only duplicate that same product name
- users can register WebAuthn passkeys after signing in
- passkey registration now happens in a dedicated modal dialog so the security panel stays compact instead of rendering a tall inline form
- users can sign in later with a passkey from the login screen
- if an account has both a password and at least one passkey, the normal login flow now requires both factors in sequence
- if an account has only a passkey, the normal login button ignores any typed password and starts the passkey ceremony instead of failing
- the dedicated unauthenticated `Sign in with passkey` action now appears on that generic continue-sign-in step, starts a discoverable-credential ceremony without using the typed username as an account-state probe, and if the login form already contains a password it follows the same password + passkey flow as the main `Sign in` action
- users can remove their password entirely and keep a passkey-only account
- the last passkey on a passwordless account cannot be removed until another passkey exists or a password is set again
- repeated failed sign-ins are now tracked per client IP address and use an exponential lockout that starts at the configured initial block and doubles until the configured maximum block duration
- those login/self-registration abuse-protection defaults can now also be overridden live from `Administration -> Authentication Security`; `.env` remains the startup default and PostgreSQL stores only the deployment override values
- the `Edit Authentication Security` modal now mirrors the grouped-card style used by the polling editor, splitting login protection, registration protection, Geo-IP provider chain, Geo-IP timing, provider-specific configuration, and effective-value summary into separate visual blocks
- admin-created users are also forced to change password
- self-registered users start as `inactive` and `unapproved`
- self-registration now starts from a focused modal workflow on the unauthenticated screen instead of leaving the request form always visible
- self-registration now also loads a short anti-robot challenge before submission; challenges are stored server-side with a TTL and must be answered correctly before the pending account is created
- the self-registration modal keeps that anti-robot wording generic for end users and avoids surfacing provider-specific implementation details in the normal registration copy
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
- each IMAP source account now also chooses its own fetch mode: `POLLING` or `IDLE`
- scheduler-triggered batch runs skip `IDLE` sources, and a separate background watcher queues those sources back into the normal import pipeline when new mail arrives
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
- source-email-account statistics intentionally avoid deployment-wide account counters like `healthy accounts` and instead focus on source-scoped values such as imported totals, error polls, and run activity
- for IMAP IDLE sources, source-scoped statistics now keep `idle-source` events in their own run bucket and rename the chart to `Source activity over time`, so realtime imports are no longer mislabeled as manual polling; the UI labels that lane as `Realtime source activations` and clarifies that it counts initial/new-mail activations rather than connection time
- the source mail account dialog now shows a best-effort IMAP `$Forwarded` capability result during connection tests by inspecting folder permanent flags, but InboxBridge still treats the real post-poll `$Forwarded` write as optional and non-fatal if the server rejects it later
- those source connection-test diagnostics now render their explanations behind translated inline info icons, so details like the `$Forwarded` capability caveat stay available without adding a long standalone note below the result grid
- the per-source `Run Poll Now` action still triggers an immediate one-off sync even when the source uses `IMAP IDLE`; scheduler polling remains skipped for that source while the watcher is active
- when a realtime IMAP IDLE activation arrives while a scheduler batch is already running, InboxBridge can now run that single-source `idle-source` import alongside the scheduler batch instead of re-queueing it until the whole scheduler run finishes; other busy states still keep the normal `poll_busy` behavior
- IMAP IDLE sources now also maintain watcher-health state so the scheduler can temporarily fall back to batch polling if an IDLE watcher stays disconnected beyond the normal reconnect window; once the watcher reconnects, the source automatically returns to pure IDLE-driven ingestion
- all polling statistics charts now expose a `Custom` date-time range flow that opens a modal dialog, requires a `from` value, and defaults `to` to the current time when it is omitted
- polling statistics now bucket and label hourly/daily timelines in the requesting browser's IANA timezone via the `X-InboxBridge-Timezone` header instead of forcing UTC, so the same shared instance can show truthful local-hour charts to admins and users in different regions
- that stats timezone header is now sourced from the signed-in user's effective timezone preference rather than blindly from the raw browser timezone; auto mode follows the current browser zone, while manual mode pins chart buckets to the account's chosen IANA timezone across devices
- when that effective timezone changes, the frontend now immediately reloads the stats payloads that depend on server-side bucketing and keeps using the new timezone for later manual/background refreshes instead of letting older refresh closures drift back to the previous zone
- statistics charts now use multi-series line charts with preset ranges like past day, yesterday, past week, past month, past trimester, past semester, and past year, derived from persisted `imported_at` timestamps and recent poll-event history
- polling statistics now also expose provider mix, current health buckets, duplicate trends, error trends, run categories, and average poll duration
- nested statistics sections inside expanded source-email-account cards and expanded admin user cards are independently collapsible, and should default to collapsed when there is no meaningful data to display yet
- the admin UI now separates admin users into `My InboxBridge` and `Administration` workspaces so personal account setup is distinct from deployment-wide controls
- those workspaces are now route-backed in the browser, so `/` stays on the `My InboxBridge` workspace, `/admin` opens the administration workspace directly, translated admin slugs such as `/administracao` remain supported, and older explicit user-workspace slugs are normalized back to `/`
- workspace-route canonicalization should follow the current UI language immediately for signed-in sessions and should not wait on delayed UI-preferences hydration before translating an explicit admin path like `/admin` into its localized slug
- the movable content sections inside each workspace now support per-account reordering, while the header and workspace switcher stay fixed
- the movable workspace sections can now also be rearranged by drag-and-drop when the user enables layout editing from `Preferences`, and a dotted placeholder shows the drop position
- the default `My InboxBridge` section order is now `Quick Setup Guide`, `My Destination Mailbox`, `My Source Email Accounts`, `My Polling Settings`, `InboxBridge Go`, `My Statistics`
- the default `Administration` section order is now `Quick Setup Guide`, `Global Polling Settings`, `OAuth Apps`, `Users`, `Authentication Security`, `Global Statistics`
- moving sections with the arrow controls or drag-and-drop should keep layout-edit mode active until the user explicitly exits or discards it; the persisted UI-preferences payload must never silently clear that transient edit state during a reorder
- the user-workspace section order now treats `remoteControl` as a first-class movable section, even for older saved layouts that predate it, and move buttons should be enabled or disabled based on the visible rendered section list rather than hidden entries in the stored order
- drag-and-drop now resolves the final drop target from the pointer-up location using midpoint-based insertion slots between cards and reorders against the actual visible section list, so dragging the first card downward or the last card upward lands in the expected adjacent slot, visible sections can be moved into the final slot, and the active layout-edit session should also stay alive even if preferences are reloaded during the edit
- reconnecting Gmail to the same already-linked account should not revoke that account's Google grant; replacement and revocation only happen when the user actually links a different Gmail account
- the frontend layout now includes explicit responsive behavior for small screens, especially for hero actions, section headers, mail-account and user list rows, modal dialogs, and metric/stat cards
- the `My Source Email Accounts` cards and nested `My Statistics` grids should stay inside their section width on phones; the current CSS relies on `min-width: 0`, content wrapping, and clamped auto-fit grid columns so expanded source rows and stat cards do not bleed off the right edge on mobile
- when Gmail API access is manually revoked outside InboxBridge, a confirmed repeated Gmail `401` now clears the saved Gmail OAuth link for that user and should cause the UI to show the Gmail account as no longer linked
- the preferences model now also stores dismissible quick-setup state, a persisted layout-edit toggle, separate user/admin workspace section order, and a per-user timezone mode plus optional manual timezone override
- the same preferences model now also stores a per-user date-format choice; `AUTO` follows the selected language's default pattern while the effective timezone still controls which local time/day is shown
- manual date formats now support the built-in preset list `DMY_24`, `DMY_12`, `MDY_24`, `MDY_12`, `YMD_24`, and `YMD_12`, plus validated custom patterns stored directly in the persisted `dateFormat` value
- the custom date-format dialog now validates a richer token set (`YYYY`, `YY`, `MMMM`, `MMM`, `MM`, `DD`, `dddd`, `ddd`, `HH`, `H`, `hh`, `h`, `mm`, `M`, `ss`, `S`, `A`), shows a live example in a nested Preferences dialog, and then applies that custom pattern consistently across `My InboxBridge`, `Administration`, Notifications Center timestamps, session/security date-times, charts, and InboxBridge Go
- opening the custom date-format dialog now seeds the editor from the user’s currently selected format, including built-in presets and locale-derived automatic mode, so users can refine the active format instead of starting from a generic placeholder
- the custom date-format dialog now keeps the supported-token helper text visible below the example preview and uses an inline contextual token autocomplete menu while typing, or immediately on focus using the token fragment around the caret; after separators it shows all tokens, inside a token fragment it filters from that fragment, and localized aliases such as Portuguese `AAAA` / `AA` are normalized back to the canonical stored syntax before save
- the `Automatic` date-format option in Preferences now displays the effective locale-derived pattern inline so users can see which default format their selected language currently maps to before saving
- that timezone preference still applies consistently across those same surfaces, so each account can see the same local-time view even if different users on the same instance live in different regions
- statistics rendering now uses `Recharts 3.x`, giving the polling dashboards shared hover tooltips and more maintainable chart behavior than the previous custom SVG chart
- the frontend package set is intentionally kept on current stable major versions, including React 19, Vite 8, Vitest 4, jsdom 29, and Recharts 3.x
- the frontend Docker build and GitHub Actions workflows now use Node 24 LTS as the baseline instead of Node 22 or local Node 25 Current, so local test/runtime quirks from Node 25 should not define the supported admin-ui environment
- admin-ui test commands now run through `admin-ui/scripts/run-vitest.mjs`, which adds `--no-webstorage` only when the local runtime is Node 25+ so Node's built-in Web Storage does not override jsdom's browser-like storage during Vitest runs; Docker and CI stay on Node 24 LTS without that flag, and the workaround should be revisited when the Node 25 + Vitest/jsdom interaction is resolved upstream
- the normal `cd admin-ui && npm run test:run` frontend suite now uses forked Vitest workers with a default `50%` worker cap instead of forcing one worker, while CI, Docker image builds, and coverage runs stay single-worker to remain more memory-stable and avoid flaky cross-test interference on stricter environments
- the top-level `App` and `/remote` `RemoteApp` now define their production polling/watchdog/anomaly timing defaults in explicit timing maps so tests can inject much shorter intervals without changing the shipped runtime behavior; this keeps the slowest app-level live-poll and anomaly pulse regressions in the sub-second range instead of waiting on the real multi-second timers
- `.github/dependabot.yml` now documents the repository policy of usually staying on approved LTS baselines for Node and Quarkus, but Dependabot itself cannot automatically follow "latest LTS" semantics for future releases; Node and Quarkus line changes should therefore be reviewed manually and only adopted intentionally
- the frontend now also ships a dedicated `/remote` remote-control route with a tiny mobile-first polling UI, its own manifest/service-worker assets, and source-level poll actions without opening the full admin workspace
- the `/remote` route now also hydrates the current live poll snapshot on load, subscribes to a remote-scoped SSE stream for per-source progress, and surfaces pause/resume/stop plus per-source `Move Next` / `Retry` controls whenever the remote viewer is allowed to manage the active run
- disabled source accounts are excluded from the `/remote` source list entirely so that quick-access surface only shows mailboxes that can actually be triggered there
- collapsed `/remote` source cards now keep a denser layout with always-visible summary pills for owner, folder, effective polling cadence, and latest result, so the quick-access list consumes less vertical space before the user opens full details
- `/remote` now folds live polling state and actions into the existing source cards instead of rendering a second live-progress source list, so each source row stays the single place for status, controls, and expanded error details
- expanded `/remote` source details now also keep the latest completed fetched/imported/duplicate counts under `Last result`, so operators can still inspect the final per-source totals after the live progress row disappears
- the live progress copy inside `/remote` source cards no longer repeats `Owner ...`; ownership stays in the summary/details UI, while the live line is reserved for queue position and result counters
- the `/remote` login screen now exposes its own unauthenticated language selector, but the remote UI should switch to the signed-in user's saved `UserUiPreference.language` immediately after auth so the quick-access surface stays aligned with the main app Preferences
- all `/remote` labels, install-card copy, live-control buttons, state pills, and helper-generated progress/session strings should resolve through translation keys instead of falling back to raw English helper text
- the main `My InboxBridge` workspace now also includes a dedicated `InboxBridge Go` launch card so the lightweight remote page is discoverable from the normal dashboard
- the `Preferences` dialog now applies the `Show Quick Setup Guide` toggle to the currently selected workspace only, because the `My InboxBridge` and `Administration` guides are distinct and keep separate persisted visibility state
- the main app and `/remote` now reuse the same `SectionCard`-based utility prompt pattern for optional browser-location capture, while only `/remote` exposes the installability prompt
- reusable admin-ui primitives now include a shared `SectionCard` shell for non-collapsible panels, a shared `CollapsibleSection` shell for standard workspace/admin sections with the corner-toggle UX, a shared `ButtonLink` component for navigational CTA actions, a shared `FormField` wrapper for labeled inputs with help hints, a shared `FloatingActionMenu` primitive for anchored contextual menus, and a shared `AutocompleteInput` primitive for pillbox-style floating text suggestions that keeps the field editable on coarse-pointer devices and renders an attached dropdown directly below the input instead of a native picker, so new sections do not need one-off layout/button/menu/form styling
- the top-level movable workspace sections in both `My InboxBridge` and `Administration` are expected to render through `CollapsibleSection`, while non-collapsible launch/utility cards should use `SectionCard`; dedicated unit tests now audit those section-shell expectations directly
- the contextual menu trigger used in user/mail-account lists now renders as a hamburger menu icon instead of a visible `...` text label
- that hamburger icon now uses one shared global style so both lists render the same equal-width menu glyph
- each fetcher now has its own persisted polling state, including next poll time, cooldown-until timestamp, consecutive failure count, and last failure reason
- IMAP polling state now also persists the watched folder plus `UIDVALIDITY` / last-seen UID checkpoints for checkpoint-based resume after reconnects
- POP3 polling state now also persists the last seen UIDL checkpoint for checkpoint-based resume when the server still exposes the prior message
- repeated provider failures now trigger automatic cooldown/backoff so one blocked mailbox does not cause InboxBridge to hammer that provider
- polling now fails early with a clear `Gmail account is not linked` error when a source depends on Gmail import but the current account has unlinked Gmail
- the per-user poller settings card uses the same padded section shell as the global dashboard cards so the form layout stays visually aligned
- the fetcher contextual `...` menu now supports running one specific fetcher immediately and opening a source-specific poller settings dialog
- the fetcher running-state badge now keeps a clearly visible spinner aligned beside the `Running` label
- disabled source email accounts now show a `Disabled` status badge in the UI even if their most recent saved poll result was `Success` or `Error`, so the visible badge reflects the current source state before stale last-run history
- disabled source email accounts also keep their per-row `Run Poll Now` actions disabled, and explicit single-source manual poll requests now short-circuit as `source_disabled` instead of trying to poll anyway
- the add/edit mail-fetcher dialog now has a connection test action that validates the entered IMAP/POP3 settings, including password and Microsoft OAuth2 authentication, and returns structured protocol / endpoint / TLS / authentication / mailbox-reachability diagnostics before save
- those fetcher connection diagnostics are rendered beneath the dialog action row, and the shared modal shell now constrains itself to the viewport with internal scrolling so action buttons remain reachable
- the GitHub Pages site generator shares pure helper logic in `site/config-generator.mjs`, and CI validates that generator with a lightweight Node script so the public starter `.env` output stays aligned with the documented env-managed setup path
- env-managed fetchers route those per-fetcher poller actions through admin-only endpoints, while UI-managed fetchers use user-scoped endpoints
- the new `/api/remote/...` surface exposes the same polling engine through a narrower remote-scoped auth model, with separate session cookies, CSRF protection for browser writes, remote-specific rate limiting, and optional bearer service-token auth mapped to a real InboxBridge account
- the `/remote` page now injects its own PWA manifest/theme metadata at boot so Chromium browsers can actually see that route as installable, while the authenticated remote UI keeps its install card visible with manual browser-specific instructions for Safari and Firefox even when `beforeinstallprompt` never fires
- the `/remote` PWA service worker now prefers fresh network responses for the remote shell and other same-origin GET assets, falling back to cache only when offline, so login copy and bundle updates do not get stuck behind an old cache-first app shell
- when single-user mode is active, the `/remote` source cards now hide the owner pill and owner details entirely because every visible source belongs to the only active account
- the main authenticated admin-ui browser session now mirrors that hardening model for unsafe `/api/...` writes, using a separate non-HTTP-only CSRF cookie plus matching `X-InboxBridge-CSRF` header validation and same-origin checks in the auth/admin request filters instead of relying only on `SameSite=Strict`
- API responses now also add stricter browser-facing headers such as `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `no-store` cache control, HTTPS-only HSTS, and `X-Accel-Buffering: no` for SSE polling endpoints so live streams are less likely to leak through caches or be buffered by reverse proxies
- IMAP raw-message materialization now retries once after a `FolderClosedException` by reopening the folder and reacquiring the message before the whole fetch attempt is treated as failed
- Microsoft IMAP/POP connects now retry once with a freshly refreshed access token if Outlook rejects the cached token as invalid before its stored expiry time
- busy poll results now include metadata about the currently running trigger/source so the UI does not only show a generic `A poll is already running` message
- floating notifications now use compact icon-only copy actions with tooltip text and increase opacity on hover so long payloads stay readable without taking extra horizontal space
- compact `...` action buttons now use a fixed square footprint, and shared button styling prevents label text from wrapping onto a second line
- the floating notification stack now also uses a stronger default opacity and a subtle backdrop blur so notifications stay readable even over visually busy sections of the dashboard
- the floating notification stack is now capped to the number of banners that fits inside the current viewport, so newer notices do not push older ones off-screen; the overflow remains available in the persisted Notifications Center history instead
- admin-ui notifications are now persisted in the authenticated user's UI-preferences record, with a bounded per-account history that survives browser refreshes and normal sign-out/sign-in cycles instead of being lost with the React runtime
- repeated identical error or warning notifications now aggregate centrally in the main app notification stack, so transient offline/proxy loops such as repeated `Load failed` or `502 Bad Gateway` refresh failures become one banner/history entry with a visible repeat count instead of flooding the screen with dozens of copies; the grouping window is intentionally short so quick bursts are folded together while later separate incidents can still surface again
- mail-fetcher detail data is refreshed automatically after manual poll attempts and when the user expands a fetcher row, so the visible status no longer waits only for the periodic dashboard refresh
- expanding any major collapsible admin-ui section now triggers a fresh reload for that section and shows an inline loading indicator while the refresh is happening
- expanding an individual user entry now refreshes the latest user list/configuration data as part of that expansion flow, while the row shows loading feedback
- user-facing storage labels in the admin UI and OAuth callback pages now stay technology-neutral, using wording like `Encrypted storage` instead of exposing backend implementation details such as PostgreSQL
- broad manual polling runs now expose the backend's currently active source through `/api/poll/status`, so the admin UI can move the per-row running spinner from source to source as the batch progresses instead of lighting every eligible row at once
- the live polling snapshot is now the authoritative source for row-level running state, because several sources may be `RUNNING` concurrently while bounded virtual-thread workers drain the queue
- the admin UI installs a same-origin fetch wrapper at boot so unsafe browser writes automatically carry the current browser-session CSRF header, while cross-origin requests are left untouched
- authenticated workspaces now also load an immediate live-poll snapshot from `/api/poll/live` or `/api/admin/poll/live` after sign-in, so Pause/Resume/Stop controls and per-source progress remain visible even if the user lands mid-run before the next SSE event arrives
- the authenticated SSE endpoints that back `/api/poll/events`, `/api/admin/poll/events`, and `/api/remote/poll/events` now need explicit blocking execution because the auth/session filters for those streams hit the database; leaving them on the Vert.x IO thread causes Quarkus to reject the request with `Cannot start a JTA transaction from the IO thread`, which can leave the UI stuck showing stale `RUNNING` state even after the poll itself finished
- the `My Polling Settings` and `Global Polling Settings` section headers now surface Pause/Resume/Stop buttons whenever the signed-in viewer can control the active live run, instead of hiding those controls only inside the live-progress panel body
- `My Polling Settings` no longer renders its own `Live Poll Progress` card; user-facing per-source live progress stays only in `My Source Email Accounts`, while the polling settings section keeps the broad controls and effective summary
- the `My Source Email Accounts` contextual menu now exposes a quick `Enable` / `Disable` action for database-backed fetchers, so users can pause or restore a source without reopening the full editor
- each persisted source `lastEvent` now also stores the Spam/Junk mailbox count observed during that poll, and the expanded source-account `Last result` details surface that same count so the information remains visible after the completion notification disappears
- InboxBridge Go now also shows the persisted Spam/Junk mailbox count in both the expanded remote source `Last result` pills and the latest remote run summary card, so the lightweight `/remote` surface stays aligned with the richer main dashboard result details
- the InboxBridge Go per-source live area stays intentionally sparse: it only shows queue position for still-queued sources plus the `Move Next` control when applicable, and no longer renders the per-card fetched/imported/duplicates sentence or a dedicated `Retry` button because `Poll This Source` already covers reruns
- the InboxBridge Go source-card divider is now tied to the expanded details block rather than the live-progress area, so a running poll no longer shows an extra horizontal separator unless that card is expanded
- InboxBridge Go remote source cards now only render the live-progress container when queue-position text or a `Move Next` action actually exists, which prevents `RUNNING` cards from growing slightly due to empty live-state spacing
- poll-throttle lease release is now best-effort during cleanup: if PostgreSQL drops the connection while deleting a `poll_throttle_lease` row, InboxBridge logs a warning and relies on the lease TTL to expire naturally instead of failing the poll solely because cleanup could not remove the lease row immediately
- persisted `source_poll_event` writes are also now best-effort: if PostgreSQL drops during the final insert for source run history, InboxBridge logs a warning and keeps the poll result itself intact instead of surfacing that database write failure as the poll outcome
- InboxBridge Go latest-run result banners now treat `STOPPED` as its own outcome instead of falling through to the generic success copy, and they use the returned stop-request username to distinguish self-stopped runs from runs stopped by another administrator
- persisted per-source `source_poll_event` rows now classify the intentional `Stopped by user.` message as `STOPPED` instead of `ERROR`, so InboxBridge Go source pills stay aligned with intentional stop behavior and do not show an `ERROR` badge without a real failure
- persisted source `lastEvent` records now also carry optional actor username and execution surface metadata, so the user workspace can render readable summaries like `Executed at ... via My InboxBridge`, `... by admin via Administration`, or automatic scheduler wording instead of exposing raw trigger codes such as `user-ui`
- the admin-ui live snapshot fallback now continues while a single-source manual poll is in flight or while an already-running live poll remains active, which clears stale source rows if the final SSE update is missed
- the live poll source payload now also carries a determinate `totalMessages` and `processedMessages` pair once the backend knows the fetched batch size, so the user source rows, admin live panel, and InboxBridge Go can render progress like `Processing 3 / 50 emails (1.5 MB / 6 MB)` during long-running imports instead of showing only a generic `RUNNING` state; `processedMessages` now tracks the actual number of source messages examined in that run and therefore stays aligned with `fetched` instead of double-counting duplicates
- the `My InboxBridge` source-row running badge should stay the same compact height as the normal `SUCCESS` / `ERROR` / `DISABLED` status pill, and the InboxBridge Go source-card summary pill should mirror the determinate `Processing X / Y emails (size / size)` copy directly in that top status area whenever a source is actively running
- once InboxBridge Go mirrors that determinate progress in the source-card summary pill, the card should not also repeat the same processing label, progress bar, or transient fetched/imported/duplicates sentence in a second live block below; the lower live area is reserved for queue-related copy and controls such as `Move Next`
- once a source has fully examined its fetched batch but the live run is still writing final state, the frontend now switches that determinate copy to `Finalizing ...` so a row does not sit for an extended period on an already-complete `Processing 50 / 50 ...` label
- the main admin UI and `/remote` now also reconcile `/api/poll/live` or `/api/remote/poll/live` every few seconds during an active run even when the authenticated SSE stream remains connected, so a single missed `poll-run-finished` event cannot leave one surface stale while another surface has already converged to idle
- the admin `Global Statistics` section now computes a client-side anomaly warning from the hourly scheduled-run timeline plus the current effective polling interval and enabled-source count; if the observed scheduled runs in an hour are dramatically higher than that interval would allow, the section emits a linked warning notification only for administrators and only while the suspicious bucket is still within the last 24 hours, pulses red only temporarily to draw attention, keeps the static section warning only for up to one week so stale incidents eventually age out instead of looking permanently active, and also annotates the suspicious run-activity chart point directly so the spike is visible in-context on the line chart with the translated anomaly explanation available on hover
- the shared timeline chart component now suppresses sticky active-dot/crosshair selection, exposes anomaly-marker explanations through a brief hover-or-click callout, keeps the toolbar intentionally limited to `Range` plus `Resolution`, and preserves that selected view through ordinary stats refreshes/remounts and same-session page refreshes instead of resetting around an interactive brush
- anomaly warning markers on those charts should be anchored by the underlying bucket key for the current resolution, not by the rendered tick label alone, so an hourly anomaly still appears on the correct aggregated day or week slot after the user changes resolution
- anomaly alerts also need to carry their originating stats range, not just a reused hour label, so a warning detected in `yesterday` does not appear on the `today` chart when both ranges contain the same `07:00` bucket; contiguous suspicious hourly buckets should collapse into one anomaly window, the warning copy should describe that full window, the marker should sit at the start of the detected window, the admin warning affordance should be able to switch the run chart to the matching range on demand without making that focus sticky on later refreshes, should focus the run chart card when `Show on chart` is clicked, and should fall back to localized bucket-label formatting instead of ever rendering `Unavailable` or raw ISO timestamps if a valid datetime window cannot be resolved; the scheduled-runs line should visually highlight the anomalous window itself in red
- chart range selection is now resilient to sparse stats refresh payloads: if a later polling-stats response temporarily omits timeline keys that were available before, the chart keeps the user’s current range instead of treating it as invalid and snapping back to the first default range
- the backend stats service now returns a rolling 24-hour hourly timeline for the first preset range (`today` key, labeled `Past day` in the UI), real hourly buckets for `pastWeek`, daily buckets for `pastMonth`, weekly buckets for `pastTrimester`, `pastSemester`, and `pastYear`, and span-adaptive custom buckets. The frontend resolution picker follows that model: `Past day`/`Yesterday` stay hourly, `Week` offers hour/day, `Month` offers day/week, `Trimester`/`Semester`/`Year` offer week/month, and `Custom` adapts to the selected span instead of exposing impossible finer resolutions
- custom ranges of up to one week now keep real hourly backend buckets available, and once a custom range has been applied the selected date-time window stays visible in the chart toolbar using the current user locale’s datetime formatting and can be reopened through an explicit `Edit dates` action, so the user can see and adjust the current custom window without losing context
- hard-refresh chart restore should ignore stale persisted preset/custom ranges that no longer exist in the freshly loaded stats payload and fall back to the first preset with real data instead of leaving the chart blank until the user manually changes the range
- the statistics custom-range dialog now supports relative windows such as `past X hours/days/weeks/months/years` in addition to explicit start/end timestamps, and the chart should adapt its available custom resolutions to the selected span so short windows stay hourly while long windows can roll up to week, month, trimester, semester, or year views as appropriate
- the `Imported` series in the import-activity chart now uses a distinct green stroke so it no longer clashes visually with the red `Errors` series, and expanded source-card `Last result` summaries in both `My InboxBridge` and `/remote` now include a dedicated imported-size pill when that run actually imported bytes
- persisted `source_poll_event` rows now also store `importedBytes`, and the `Last result` summaries in `My InboxBridge` and InboxBridge Go surface that completed-run imported size in human-readable units
- those `Last result` summaries should keep their per-run counters in the same pill row on both surfaces, including the Spam/Junk mailbox count when present, instead of rendering the Spam/Junk total as a separate sentence in `My InboxBridge`
- IMAP Spam/Junk probing now prefers server-advertised special-use folder metadata such as `\\Junk` or `\\Spam`, and only falls back to normalized folder-name matching when the server does not expose those flags
- live pause/stop requests now apply during the active source as well as between queued sources because `PollingService` no longer precomputes the whole live queue up front, checks the live-control state between fetched messages, and now registers cancellation hooks that close active mailbox sessions when a stop is requested
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
- per-user admin-ui preferences, including language and persisted notification history

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
The generated self-signed cert SANs are no longer hardcoded only to localhost: `cert-init` now reads `.env` through Compose, derives the hostname from `PUBLIC_BASE_URL`, preserves the mandatory Docker-internal names, and accepts extra comma-separated SAN entries through `TLS_FRONTEND_CERT_HOSTNAMES` and `TLS_BACKEND_CERT_HOSTNAMES`. If those configured hostnames change, `cert-init` automatically reissues the generated certs on the next compose startup instead of leaving stale SANs behind.

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
- fetch window always means "inspect the latest N messages this run", not "page backward through older mail across future runs"
- for historical backfill, temporarily raise the fetch window, run imports, then lower it again

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

Source checkpoints now deliberately follow the effective destination mailbox identity as well as the source. That means changing a destination mailbox invalidates the old IMAP/POP checkpoint for replay purposes without resetting the broader per-source interval/cooldown state.

Current backoff behavior now uses one shared mail-failure classifier across source cooldowns, adaptive throttle penalties, and OAuth session retry decisions:

- rate-limit, throttling, quota, or lockout style errors trigger longer cooldowns
- mailbox authentication and OAuth authorization failures trigger the longest cooldown tier so InboxBridge does not repeatedly hammer a blocked account, but they do not widen the host/provider adaptive throttle state
- transient network failures, provider-availability failures, and mailbox-state issues such as closed folders trigger a medium cooldown and a single-step adaptive throttle penalty
- unknown failures fall back to the short default cooldown tier
- repeated failures increase the cooldown window with exponential growth up to a capped maximum
- successful polls now schedule their next eligible run on aligned interval boundaries instead of drifting from the previous success timestamp, so user-visible cadences like `2m`, `5m`, and `10m` stay anchored to predictable clock slots

Persisted `source_poll_event` history now also captures the cooldown/throttle decision snapshot observed during that poll run. Each row can now store the classified failure category, the chosen cooldown backoff and resulting `cooldownUntil`, the cumulative source/destination throttle wait time seen during that run, and the final adaptive throttle multiplier / `nextAllowedAt` state left behind after the run completed. That keeps cooldown reasoning queryable after the mutable per-source/per-throttle state has moved on.

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
- the frontend keeps the remote page installable by linking `/remote` to `remote.webmanifest`, registering the service worker only on `/remote`, and showing the install prompt only there when the browser fires `beforeinstallprompt`; local installability can still be suppressed when the HTTPS certificate is not trusted by the browser/OS
- the `/remote` install-help card now shows its primary install action only when the browser exposes a native install prompt or supports a mobile manual-install path such as iPhone/iPad `Add to Home Screen`; unsupported desktop/manual browsers keep the guidance but hide the install button
- the `/remote` install-help card can now be postponed with `Not now`, which hides that section until the user explicitly reopens the install guidance from the hero hamburger menu
- on mobile browsers where `/remote` cannot trigger installation directly, the page should replace the install card with a dismissable local-only hint that points users to the device/browser built-in home-screen pinning feature instead of showing a no-op button
- the InboxBridge Go hero now tucks install guidance, device-location sharing, and sign-out into a top-right hamburger menu so the banner keeps its main width for poll actions on narrow screens
- on mobile widths, that InboxBridge Go hero hamburger menu should stay pinned to the top-right corner of the card instead of joining the stacked poll buttons below
- the signed-in InboxBridge Go hero should show `InboxBridge Go` only once in the main heading instead of repeating the same label in a separate eyebrow above it
- the unauthenticated InboxBridge Go login screen now uses only the main `InboxBridge Go` heading and no longer repeats that label in a separate blue eyebrow under the language selector
- the unauthenticated InboxBridge Go login screen now adds dedicated spacing below the language selector so the title remains visually separated even without the removed eyebrow label
- that login-card spacing should remain in place on narrow mobile widths too, so the top-right language picker does not crowd the `InboxBridge Go` title
- the InboxBridge Go login card now labels its primary action as `Sign in`, and session-expired or similar login notices render below the action buttons so they read as sign-in feedback instead of interrupting the top of the form
- the InboxBridge Go login language selector is now a compact top-right flag button with no visible `Language` label; it opens a floating menu of language options with labels plus flags, and its mobile layout expands cleanly so the picker still works on narrow screens
- the main My InboxBridge login card now reuses that same flag-based language menu in its top-right corner, while the self-registration dialog still keeps the traditional labeled selector
- the Preferences dialog now also uses the shared flag-plus-label language menu for its language field, instead of a plain select, so in-app language switching matches the login surfaces
- the Preferences dialog language menu is intentionally start-aligned so it grows to the right of the trigger instead of covering the field label, while the InboxBridge Go login picker keeps remote-specific surface colors so the button and dropdown match the remote page rather than the main workspace panels
- the InboxBridge Go language picker now uses fully opaque white surfaces for both the trigger and the floating menu so the control stays crisp against the remote page gradients instead of looking semi-transparent

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

- translations are routed through `admin-ui/src/lib/i18n.js`, while the locale catalogs themselves now live in `admin-ui/src/lib/i18n/locales`
- the backend persists the selected language in `user_ui_preference.language`
- the browser also mirrors the last selected language in local storage so the login screen can reuse it before session data is loaded
- the unauthenticated login screen now exposes that language selector directly, and the self-registration modal mirrors it so users can switch locales before authentication
- visible labels now route through the translation helper instead of mixing translated and raw JSX text
- the React boot path now preloads the active locale before rendering, and additional locale catalogs load on demand so production builds do not ship one oversized translation chunk up front
- expanded user-management entries are grouped into explicit subsections for user configuration, Gmail account, poller settings, passkeys, and source email accounts
- the most prominent labels inside those subsection bodies now follow the selected language too instead of remaining in English
- quick-setup guidance, Gmail account controls, poller-setting forms, and source-email-account forms/lists now have broader locale coverage so changing language updates section bodies as well as headings
- translation regression coverage now includes localized rendering tests for the major admin-ui surfaces plus a critical-key catalog test in `admin-ui/src/lib/i18n.test.js`
- password-policy checklists and normalized passkey failure/cancellation messages are now translated too, instead of depending on raw browser English
- ALTCHA verification now yields to the browser before solving the local proof-of-work challenge so the shared loading spinner and `Verifying…` button state are visible during processing
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
- admin diagnostics that surface destination identities, per-folder checkpoints, persisted poll-decision history, and explicit source alerts for disconnected IMAP IDLE watches, active cooldown loops, and sustained throttling directly in the UI
- richer metrics and alerting
- more structured audit logging

The earlier retry/backoff roadmap item is now implemented: polling cooldown decisions, adaptive throttle penalties, and source OAuth session retries share the same richer mail-failure categories instead of maintaining separate overlapping string heuristics.

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
- the add/edit source email account modal now groups mailbox-processing controls intentionally: `After polling` sits beside `Mark as read after polling`, `Source update mode` appears above the `TLS only` / `Unread only` row, and `Enabled` is the final toggle
- the add/edit source email account dialog now treats `Custom Label` as destination-aware UI: it stays visible while the destination mailbox is Gmail-backed or not configured yet, but hides once the saved destination is a non-label IMAP APPEND target such as Outlook so the form does not imply label support where only folders exist
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

Validated on 2026-04-01:

- full backend Maven suite passes with `mvn -q test` when run in an environment that allows the GreenMail integration tests to bind local ports
- focused backend polling coverage also passes with `mvn -q -Dtest=PollingSettingsServiceTest,UserPollingSettingsServiceTest,SourcePollingSettingsServiceTest,SourcePollingStateServiceTest,PollingServiceTest,PollingServiceGreenMailIntegrationTest test`
- full frontend Vitest suite passes with `cd admin-ui && npm run test:run`
- frontend coverage can now be generated with `cd admin-ui && npm run test:coverage`
- backend coverage can now be generated as part of `mvn -q test` through JaCoCo under `target/site/jacoco/`
- the current frontend Vitest coverage report is about `85.97%%` statements / `75.61%%` branches / `65.18%%` functions
- the current backend JaCoCo report is about `55.47%%` lines / `39.65%%` branches / `57.51%%` instructions, so backend coverage remains well below the frontend baseline
- polling regression coverage now explicitly exercises effective-settings inheritance and precedence across global, per-user, and per-source polling settings, plus GreenMail-backed multi-user mailbox-isolation checks so one user's source mail cannot land in another user's destination
- mailbox replay safety is now covered for destination switches too: same-user destination mailbox changes no longer reuse the previous mailbox's source checkpoint or dedupe identity, and GreenMail integration tests lock that behavior in for both IMAP and POP3
- admin-ui Docker build succeeds with the Vitest suite enabled in-container
- `docker compose up --build -d` succeeds
- backend starts on both HTTP `8080` and HTTPS `8443`
- admin UI serves correctly over HTTPS in the container
- unauthenticated `GET /api/auth/me` returns `401`
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`
- Flyway migrations `V1` through `V40` apply successfully
- direct backend startup works with the current polling pacing defaults because `DESTINATION_PROVIDER_MIN_SPACING` now uses the valid ISO-8601 duration `PT0.25S`

Admin UI frontend structure now follows a feature-first split:

- `src/app/App.jsx` owns main-workspace session state and shared data loading
- `src/app/RemoteApp.jsx` owns the `/remote` boot path and remote-specific orchestration
- `src/features/email-accounts/hooks/useEmailAccountsController.js` owns source email account orchestration
- `src/features/admin/hooks/useAuthSecurityController.js` owns session auth, password, and passkey flows
- `src/features/destination/hooks/useDestinationController.js` owns destination mailbox actions
- `src/features/polling/hooks/usePollingControllers.js` owns user/global polling dialog and run flows
- `src/features/<feature>/components/...` contains feature-owned UI sections with colocated CSS/tests
- `src/shared/components/...` contains reusable primitives such as `SectionCard`, `CollapsibleSection`, and form/menu helpers
- `src/lib/...` contains formatting, translation, runtime, and API helper utilities
- `admin-ui/README.md` documents the frontend layout and test workflow
- the Google and Microsoft OAuth callback pages now support navigating back to InboxBridge after in-browser code exchange
- the Google and Microsoft OAuth callback pages support copying the raw code, automatically attempt the exchange on load, warn before navigating away without exchange, and auto-redirect to InboxBridge after a 5-second countdown once exchange succeeds unless the user cancels that automatic redirect
- admin-ui buttons that trigger backend work now show inline loading spinners so the user gets immediate feedback during authentication, saves, polling, refresh, and OAuth start flows
- source-email-account notifications are now stored as structured descriptors and resolved at render time, so changing the UI language also re-translates existing notifications instead of leaving them in the previous language
- source-specific notification links can now focus the matching source email account card in the user workspace, including OAuth-related error notifications such as a missing refresh token
- the add/edit source email account dialog now supports IMAP folder discovery after a successful test connection and while editing an existing IMAP account, mirroring the destination mailbox folder-selection flow
- the frontend production bundle now uses explicit manual chunking for React/vendor, router, i18n, and chart code, which removed the earlier Vite large-chunk build warning
- polling now includes a first scaling-hardening layer: aligned per-source schedule slots, per-instance minimum spacing between polls to the same source host, and per-instance minimum spacing between deliveries to the same destination provider/host
- those scaling-hardening knobs are configured through `SOURCE_HOST_MIN_SPACING`, `SOURCE_HOST_MAX_CONCURRENCY`, `DESTINATION_PROVIDER_MIN_SPACING`, `DESTINATION_PROVIDER_MAX_CONCURRENCY`, `THROTTLE_LEASE_TTL`, and `ADAPTIVE_THROTTLE_MAX_MULTIPLIER`; older success-jitter config fields may still exist in persisted settings, but the current scheduler cadence is driven by aligned interval boundaries
- the current throttling/scheduling protection is per app instance only; true cluster-wide coordination, global provider quotas, and distributed locking are still future work for multi-node deployments

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
- The `Administration -> OAuth Apps` section now includes explicit operator guidance for the shared Google Cloud and Microsoft Entra registrations, with provider-specific setup steps and the exact redirect URI to copy into the external console before saving the resulting client ID and secret back into InboxBridge.
- The `Administration -> OAuth Apps` Google and Microsoft cards now stack vertically and expand independently, and each setup dialog now presents `Client ID`, `Client Secret`, and `Redirect URI` as separate full-width fields before a provider-specific instruction box with a top-right console shortcut, a copyable redirect URI row, and stronger emphasis on the Microsoft supported-account-type selection.
- Language selection, layout persistence, and reset-layout controls now live in a dedicated preferences modal opened from the header instead of an always-visible inline selector.
- The admin UI now enforces HTTPS both through nginx (`80 -> 443`) and with a frontend-side upgrade guard if the app is ever served over plain HTTP by another deployment path.
- The header `Security` action now opens the password, passkey, and session tools in a dedicated modal with separate tabs, instead of rendering both tools inline in the page.
- The new `Sessions` tab shows recent successful sign-ins, active sessions, the login method used, and sign-out actions for one other session or all other sessions for the current account.
- The `Sessions` tab now also includes `/remote` remote-control sessions beside normal admin-ui sessions, labels the session type explicitly, and treats revoke-all-others as applying to both session stores.
- The shared `Sessions` tab now also shows a best-effort browser label and device type for each active/recent session, derived from the stored `User-Agent`, so users can distinguish mobile Safari, desktop Edge, remote-control sessions, and similar sign-ins more easily.
- Client IP capture for auth and remote-session activity should prefer the direct socket address unless the request came through a local/private trusted proxy hop; in that trusted-proxy case the app may use `CF-Connecting-IP`, `True-Client-IP`, `X-Real-IP`, or forwarded headers from the proxy chain instead of blindly trusting arbitrary client-supplied `X-Forwarded-For` values.
- When a signed-in account detects a newer sign-in from another session during the normal background refresh cycle, the UI now raises a warning notification that links directly to the `Sessions` tab.
- The header `Refresh` action now runs the same session-activity poll as the background refresh cycle, so clicking it can also surface new-sign-in notifications instead of only reloading the main dashboard data.
- If one browser session is revoked from another session, the revoked browser now detects the next `401` response centrally and immediately returns to the login screen instead of staying in a broken authenticated shell.
- authenticated SSE subscriptions now also carry a targeted `session-revoked` event for the exact browser or `/remote` session that was signed out elsewhere, so those clients can leave immediately without waiting for a follow-up authenticated request to hit a `401`
- some browsers appear to close an authenticated SSE connection before JavaScript receives the final revoke payload, so both the main app and `/remote` now also re-check their session endpoint as soon as that authenticated stream drops unexpectedly; if the session was revoked, they still return to login immediately
- those same authenticated SSE channels are now also used as the fast path for new-sign-in notifications: whenever a new browser or `/remote` session is created, existing authenticated browser subscribers for that account receive an immediate `notification-created` event and the UI silently refreshes session activity so the Sessions tab stays current without waiting for the next periodic refresh
- new-sign-in warnings now also try to flag unusual Geo-IP locations conservatively: the backend compares the new session's country token against recent stored session-location labels for that account and only warns when the new country is unseen and there is enough prior history to make the comparison meaningful; because the current signal is Geo-IP-label based instead of coordinate-based, this warning should be treated as a best-effort security hint rather than proof of compromise
- `user_session` rows now also persist client IP, login method, user-agent, and revocation timestamp so session history can be shown without guessing from cookies.
- Approximate session location is now resolved only on new sign-ins, never on every request. The current backend strategy is: one primary Geo-IP provider, aggressive cache-by-IP in PostgreSQL, and ordered fallback providers only when the primary is down or rate-limited. The default chain is `IPWHOIS -> IPAPI_CO -> IP_API`, with optional `IPINFO_LITE` when a token is configured. Those Geo-IP settings are now also overridable from `Administration -> Authentication Security`. If Geo-IP is not configured, the UI still shows the explicit “location unavailable” notice instead of inventing a location.
- the admin Geo-IP editor now uses a primary-provider dropdown plus a chip/tag fallback input, and each provider is represented by a readiness card that can show provider-specific configuration requirements, docs, and terms links; providers that need credentials (currently `IPINFO_LITE`) remain disabled until those secrets are configured through secure storage
- browser/device geolocation is now an optional second session signal, captured only when the user explicitly allows it from the main app or `/remote`; it is stored separately from the Geo-IP label on both `user_session` and `remote_session`, exposed through the shared Sessions tab, and should never overwrite or masquerade as the server-observed Geo-IP location
- when a session has browser-reported latitude/longitude, the admin UI now does a best-effort client-side reverse-geocode to show a friendlier device-reported place label and exposes an `Open in Maps` link; that guessed label is a convenience hint only and does not replace the stored raw device-location sample
- the Sessions tab now distinguishes `not captured yet` for the current browser session from older `not shared` sessions, and exposes an inline retry capture action there so a dismissed or missed banner prompt does not strand the user without a way to save the device location
- both the main app and `/remote` now auto-capture device location only when the browser already reports geolocation permission as granted; browsers that still require a user gesture, especially mobile browsers, keep the explicit `Share Device Location` button as the reliable capture path, and the geolocation flow still retries with a lower-accuracy lookup when the first high-accuracy request fails
- passkeys are viable only on `localhost` or a real hostname/domain configured in `SECURITY_PASSKEY_*`; the admin UI now treats raw IP hosts such as `192.168.50.6` as non-passkey-capable and shows explicit guidance instead of letting WebAuthn fail with browser `effective domain` errors
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
- Authenticated polling now also exposes an SSE-backed live run coordinator with per-source progress snapshots, so the UI can follow the active source in real time instead of relying only on periodic `/api/poll/status` refreshes.
- That live coordinator remains intentionally single-run-per-instance. Admins can see and control the active run across users, while non-admin users only receive filtered live state and notifications for sources they actually own.
- Live polling controls now support pause, resume, stop-after-current-source, move-a-queued-source-next, and retry-a-completed-source during the same active run through authenticated `POST` endpoints.
- The admin UI now subscribes to authenticated SSE poll events for live batch progress and immediate poll notifications, but it falls back to the existing `/api/poll/status` refresh path if the stream disconnects or the browser lacks `EventSource`.
- Scheduler-triggered polling now pre-filters sources by effective eligibility before it opens a live run, so background 5-second scheduler ticks should stay invisible when every source is still waiting on its configured interval or cooldown window.
- Live source progress now publishes both message counts and cumulative raw MIME byte totals; the main app, admin dashboard, and `/remote` all render that as a single determinate progress line plus a progress bar when available, and completed per-source result summaries now also keep the imported-byte total from that run.
- The frontend poll surfaces now disable both broad and per-source manual run buttons while any live run is active, so a second browser surface cannot queue an overlapping poll by clicking during an in-flight run.
- The main app and `/remote` now both watch the authenticated poll SSE stream for staleness during active runs; if no snapshot, progress event, or keepalive arrives for about 45 seconds, they intentionally drop the stream connection and fall back to the existing snapshot-refresh path so `RUNNING...` does not linger indefinitely after a silent SSE failure.
- When that authenticated live stream is unavailable from the start, the main app and `/remote` still keep polling their live snapshot endpoints in the background so a poll started from another browser surface can appear without waiting for a manual refresh.
