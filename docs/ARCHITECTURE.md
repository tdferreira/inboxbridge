# Architecture notes

## Main flow

1. `PollingService` runs on schedule or manually via REST
2. `PollingService` resolves the eligible sources and lets a bounded set of virtual-thread workers claim them just in time
3. Each worker binds the current live-run/source scope, so mailbox and destination resources can register cancellation handles for that source
4. `MailSourceClient` fetches recent messages over IMAP or POP3
5. `ImportDeduplicationService` checks whether a message was already imported
6. The configured destination implementation prepares the append/import target
7. `MailDestinationService` dispatches to the active destination provider, such as Gmail API import or IMAP APPEND
8. Import metadata is stored in PostgreSQL

The worker count is intentionally bounded, so InboxBridge can overlap unrelated
mailboxes without abandoning the existing provider-throttle rules that protect
source and destination hosts from being hammered.

One implementation constraint matters here: virtual-thread workers do not carry
the original request context with them, so any repository-backed helper they
call must open its own short `@Transactional` read/write boundary instead of
depending on ambient request-scope entity access.

## Remote control flow

The `/remote` surface now sits beside the full admin UI:

1. The browser signs in through `/api/remote/auth/...`
2. InboxBridge validates the same user credentials / passkey ceremonies used by the main app
3. A separate `remote_session` row is created with its own token hash and CSRF token hash
4. The browser calls `/api/remote/control` to list visible sources and allowed actions
5. The browser also hydrates `/api/remote/poll/live` and subscribes to `/api/remote/poll/events` when the remote viewer needs live progress
6. Remote poll actions and live control actions call dedicated `/api/remote/...` endpoints, which still route into the normal `PollingService` and `PollingLiveService`

This keeps the polling engine and hardening behavior shared, while reducing the privilege and blast radius of the public quick-access surface.

The remote page is also the only installable PWA surface in InboxBridge. The main `My InboxBridge` workspace remains a normal authenticated web app, while `/remote` can expose a browser install prompt when the origin is trusted and the browser considers it installable.

## Live polling flow

InboxBridge now exposes authenticated live polling state for both the main app and the admin workspace:

1. `PollingService` starts a run through `PollingLiveService`
2. `PollingLiveService` snapshots the queued, running, completed, failed, paused, and stopped source state for the active run
3. `GET /api/poll/live` and `GET /api/admin/poll/live` return an immediate snapshot so a browser can hydrate mid-run without waiting for the next event
4. `GET /api/poll/events` and `GET /api/admin/poll/events` stream SSE updates plus live notifications as the run changes, and they can also push targeted `session-revoked` events so a browser session signed out elsewhere leaves immediately
5. authenticated `POST` controls call the live service to pause, resume, stop, move one queued source to the front, or retry a completed/failed source
6. `PollingService` now asks `PollingLiveService` for the next source just in time instead of precomputing the full queue, so reprioritize controls affect the remaining queue order while a run is active
7. several workers may therefore dequeue and run different sources concurrently, and the live snapshot's per-source `state` is the authoritative UI signal for row-level running indicators
8. the active source path now checks the live pause/stop state between fetched messages, so pause/stop requests take effect during the current source rather than only after it finishes
9. a stop request also runs the registered cancellation actions for that run, which closes active mailbox sessions and interrupts worker threads so blocking I/O has a better chance to unwind promptly

That keeps the poll engine authoritative in the backend while letting the UI show real-time progress without hammering every row with its own polling loop.

The lightweight `/remote` surface now reuses that same live model through its own remote-scoped `/api/remote/poll/live`, `/api/remote/poll/events`, and `/api/remote/poll/live/...` endpoints, so phones and quick-access devices can follow the active source and issue live pause/resume/stop/reprioritize/retry commands without opening the full workspace. Those remote SSE streams can also push targeted `session-revoked` events for remote-session sign-outs. The remote UI now folds that live state back into the existing source cards instead of maintaining a second live-progress source list.

For compatibility, the live DTO still exposes a single `activeSourceId`, but
that field is now only the primary active source. Any client that needs the
full picture should treat each source row's `state` as authoritative.

## Session and location model

InboxBridge now tracks more than just a bare cookie session:

1. the main browser app uses `user_session`
2. the lightweight `/remote` surface uses a separate `remote_session`
3. both surfaces expose the resulting session history in `Security -> Sessions`

Each visible session can now include:

- session type (`Admin UI` or `Remote control`)
- sign-in method
- best-effort browser and device labels parsed from `User-Agent`
- Geo-IP data from the server-observed client address
- an optional browser-reported device location captured explicitly from the client, stored separately from Geo-IP

That separation is deliberate: Geo-IP is server-observed and approximate, while browser geolocation is user-approved and device-reported. InboxBridge never overwrites one with the other.

The remote surface now has its own persisted `remote_session` model, remote-only
cookies/CSRF token pair, optional bearer service-token auth, and a dedicated
request filter. The filter intentionally reads only the single `remote.enabled`
config property directly so Quarkus startup does not depend on injecting the
full config mapping inside that interceptor.

The main authenticated admin-ui session now mirrors that browser-write hardening
for unsafe `/api/...` requests too:

- a separate non-HTTP-only CSRF cookie is minted beside the normal session cookie
- auth/admin filters enforce same-origin checks plus a matching CSRF header/cookie pair
- API responses add stricter browser-facing headers such as `nosniff`, `DENY`,
  `no-store`, HTTPS HSTS, and no-buffer hints for SSE endpoints

## Why Gmail import is still one destination mode

When the destination provider is Gmail, `messages.import` is the right API for this use case because it imports a raw MIME message into the mailbox without re-sending it to the world. That preserves the original sender, headers, dates, attachments, and threading signals much better than forwarding.

## Dedupe strategy

Current dedupe uses two keys:

- source-message key (`IMAP UID` when available, otherwise `Message-ID`, otherwise SHA-256)
- SHA-256 of the raw MIME message

Those dedupe checks are also scoped to a derived destination mailbox identity rather than just the signed-in user. That means switching a user's destination mailbox causes InboxBridge to treat the new target as a new import surface, even if the same user/provider route remains active.

A second invariant now has explicit regression coverage too: even during a
multi-user poll run, a source mailbox must remain bound to its own resolved
destination mailbox. The backend suite includes GreenMail-backed integration
tests that fail if one user's source mail could ever be appended or imported
into another user's destination.

## Current compromise for simplicity

InboxBridge now supports two IMAP source-ingestion modes:

- scheduled polling, which still inspects the latest configured fetch window when no durable checkpoint is available
- opt-in IMAP IDLE watching, which keeps one long-lived watcher per eligible IMAP source/folder and triggers the normal import pipeline when new mail arrives

The fetch window and scheduler interval now come from effective runtime polling settings:

- environment values provide defaults
- PostgreSQL stores optional admin overrides
- PostgreSQL also stores optional per-user overrides for DB-managed source email accounts
- PostgreSQL also stores optional per-source overrides that take precedence over both per-user and deployment-wide values
- the running poller merges the correct layer at runtime for each source

Each source now has its own persisted polling state:

- next eligible poll time
- active cooldown-until time
- consecutive failure count
- last failure reason and timestamps
- for IMAP sources, the current folder plus the last seen `UIDVALIDITY` / UID checkpoint, scoped to the destination mailbox identity that was active when the checkpoint was recorded
- for POP3 sources, the last seen UIDL checkpoint used to resume from newer mail when that POP mailbox still advertises the older message, also scoped to the active destination mailbox identity

That destination-aware checkpoint scoping matters during destination changes: when a user repoints InboxBridge to a different destination mailbox, the old source checkpoint is no longer reused for that new target, and dedupe also shifts to the new destination identity, so already-seen source mail can be replayed into the new destination under the normal fetch-window rules.

The scheduler checks that state on every run so one blocked or throttled mailbox does not stall unrelated source email accounts.

Scheduler-triggered runs now also filter out sources whose current eligibility says `INTERVAL`, `COOLDOWN`, or `DISABLED` before creating any live run state, so the user-facing live coordinator only tracks real runnable work instead of every scheduler tick.

Scheduler-triggered runs also skip sources whose fetch mode is `IDLE`, because those sources are expected to wake the shared import pipeline from the background watcher instead of being batch-polled every few minutes. When a source mailbox is saved or deleted, an after-commit event also forces the watcher registry to refresh immediately so newly switched `IDLE` sources do not wait for the normal periodic rescan. On backend startup the watcher registry is refreshed immediately as well, so eligible `IDLE` sources reconnect within the normal Quarkus boot path instead of waiting for the 30-second sweep.

Realtime `idle-source` activations now also get priority over scheduler contention. If a scheduler batch is already running, InboxBridge can start a single-source `idle-source` run alongside that scheduler batch for the newly activated IMAP source instead of repeatedly re-queueing it until the whole batch finishes. Other busy states still return `poll_busy`, so manual/source-scoped runs keep the existing safety guardrails.

IDLE sources now also keep a lightweight watcher-health record. Healthy watchers remain the primary ingestion path and scheduler polling continues to skip those sources. If a watcher disconnects and stays unhealthy beyond the normal reconnect window, the scheduler temporarily treats that source as eligible again so mail still flows while the watcher keeps retrying in the background. Once the watcher reconnects, scheduler fallback stops automatically and the source returns to pure IDLE-driven ingestion.

Successful scheduled polls now persist the next eligible time on aligned interval boundaries rather than `last success + interval`, so the single scheduler loop still wakes up every few seconds but each source's actual cadence follows predictable slots such as `:00`, `:05`, and `:10` according to that source's effective polling settings.

Polling statistics are intentionally timezone-aware at the request boundary: the browser sends the signed-in user's effective IANA timezone in `X-InboxBridge-Timezone`, and `PollingStatsService` uses that zone when building hourly, daily, monthly, and custom buckets. The persisted timestamps stay in UTC, but chart labels such as `09:00` or `2026-04-04` are computed relative to that user's effective timezone. The effective zone itself comes from the persisted user UI-preferences model: `AUTO` follows the current browser timezone, while `MANUAL` pins the account to one chosen timezone so the same user can keep consistent charts and date-time rendering across different devices or travel scenarios. The admin-side scheduled-run anomaly signal is layered on top of those viewer-local buckets in the browser: it warns only admins, keeps the floating notification for at most 24 hours after the suspicious bucket, and lets the static section warning age out after one week.
For source-scoped statistics, IMAP IDLE now has its own run category. `idle-source` events are no longer folded into the generic manual bucket, and source charts for IDLE-configured mailboxes switch to `Source activity over time` so realtime imports are represented as source activity instead of misleading manual polls. The UI labels those runs as `Realtime source activations` and explicitly clarifies that they count the initial IDLE sync plus later activations caused by new mail, not how long a watcher stayed connected.

The source connection test path now also inspects IMAP permanent flags for the selected folder and surfaces a best-effort `$Forwarded` capability signal back to the admin UI. This is advisory only: InboxBridge still treats source-side `$Forwarded` marking as optional and continues importing even if the server later rejects that flag.

UI-managed IMAP sources now also persist optional post-poll source-side actions:

- mark handled mail as read
- mark handled mail with the IMAP `$Forwarded` flag when the server accepts it
- delete handled mail
- move handled mail into another source folder

Those actions are intentionally unavailable for POP3 accounts because there is no equivalent source-side folder/flag model there. The `$Forwarded` action is treated as a best-effort hint only: DB-backed dedupe remains authoritative, and unsupported IMAP servers only emit a warning instead of failing the import.

Polling hardening also now includes:

- deterministic success jitter per source account
- persisted throttle state and leases in PostgreSQL
- per-source-host minimum spacing and concurrency caps
- per-destination-provider/host minimum spacing and concurrency caps
- adaptive throttle widening after contention or throttling-style failures
- a shared mail-failure classifier that now drives source cooldown backoff, adaptive throttle penalties, and OAuth session retryability from the same categories instead of separate ad hoc string lists

That classifier currently distinguishes rate limits, mailbox authentication failures, OAuth authorization failures, provider availability problems, transient network failures, mailbox-state problems such as closed folders, and unknown failures. Rate limits still get the strongest throttle penalty, auth/authz failures still get the longest cooldown tier without widening host/provider throttle state, and transient/provider/mailbox-state failures keep the medium retry tier.

Persisted `source_poll_event` rows now also carry the cooldown/throttle decision snapshot for that poll attempt: the classified failure category, chosen cooldown backoff and `cooldown_until`, cumulative source/destination throttle wait time, and the final adaptive throttle multiplier / `next_allowed_at` state left behind by that run. This keeps cooldown reasoning durable for later stats and operator audit work instead of requiring the current mutable state tables alone to explain what happened.

Import dedupe is now destination-mailbox-identity aware and layered. InboxBridge first checks the persisted source message key for that destination identity and source account. For IMAP those source keys now prefer `UIDVALIDITY + UID`, while POP3 source keys prefer `UIDL`. If that protocol-native identity does not match, InboxBridge next falls back to raw MIME SHA-256 and only then to the normalized `Message-ID` header for that same source account. That keeps protocol-native mailbox identifiers authoritative while still leaving one last heuristic safety net for providers that change UIDs, UIDLs, or MIME details.

To keep upgrades from older databases consistent, startup now also runs a lightweight reconciliation pass that backfills legacy `destination_key`-scoped imported-message rows and legacy/null checkpoint destination keys to the current resolved mailbox identity for each configured destination. This only upgrades rows that still look legacy-scoped; already destination-aware identities are left untouched.

Those values can be overridden live from `Administration -> Global Poller Settings`.

That is still deliberately simpler than a full mailbox-sync engine. The next evolution should be:

- multi-folder IMAP IDLE / checkpoint support
- optional admin diagnostics for destination identity, checkpoints, and persisted poll-decision history

## Package map

- `config`: config mapping interfaces
- `domain`: small internal domain objects
- `dto`: request / response DTOs
- `persistence`: database entities and repositories
- `service`: fetch, import, dedupe, label, and OAuth logic
- `web`: REST resources

## Admin UI layout

The React admin UI now separates:

- `My Poller Settings`: per-user polling overrides for UI-managed source email accounts
- `My Source Email Accounts`: a unified operational list of DB-managed and env-managed source email accounts, with add/edit work happening in a modal dialog and provider-specific actions gated by the selected provider
- `My Destination Mailbox`: provider-neutral destination mailbox configuration for Gmail API or IMAP APPEND destinations, with Gmail always using `Save and Authenticate` and Outlook allowing plain saves only for non-identity edits such as folder changes
- `Global Poller Settings`: global polling controls, health metrics, and runtime overrides
- `InboxBridge Go`: a non-collapsible launch card in `My InboxBridge` that points to the lightweight `/remote` surface

The frontend also now treats authenticated notifications as durable per-account UI state rather than a purely in-memory React concern. Recent notices are stored in the user's UI-preferences record so refreshes and normal sign-out/sign-in cycles do not silently discard operational feedback.

The frontend layout/design system now relies on shared primitives:

- `CollapsibleSection` for the top-level movable workspace and admin sections
- `SectionCard` for non-collapsible utility or launch cards
- `ButtonLink` for CTA-style internal navigation links

Layout-editing behavior is intentionally preserved as transient state while reordering:

- move buttons and drag-and-drop stay active until the user explicitly saves or discards layout editing
- section order operates on the effective visible order, so newly introduced sections such as `InboxBridge Go` still participate even for older saved layouts
- drag-and-drop resolves using midpoint-based insertion slots between rendered cards so adjacent moves land where the user drops them instead of sticking or overshooting
- `Authentication Security`: deployment-level login lockout, registration CAPTCHA, and Geo-IP controls

Deployment mode is also configurable:

- multi-user mode shows self-registration and admin user management
- single-user mode hides those surfaces and keeps InboxBridge focused on the bootstrap admin only

Authentication and registration hardening now follows this model:

- opaque database-backed browser sessions with secure cookies
- explicit CSRF protection and same-origin validation for unsafe browser writes
- WebAuthn passkeys with optional password+passkey step-up
- per-client-IP exponential login lockout
- provider-based registration CAPTCHA, defaulting to self-hosted `ALTCHA`
- per-account session history with revoke support
- optional Geo-IP lookup only when a new session is created, never on every request
- best-effort unusual-location warnings for new sessions, based on comparing the new session's Geo-IP country token with recent stored session locations for that same account

## Local TLS bootstrap

Docker Compose includes a `cert-init` container that generates the local CA plus
frontend/backend leaf certificates used by the admin UI and backend.

The SAN hostnames are now derived from:

- the hostname in `PUBLIC_BASE_URL`, or the hostname from the derived `https://${PUBLIC_HOSTNAME}:${PUBLIC_PORT}` public URL when `PUBLIC_BASE_URL` is unset
- mandatory Docker-internal names (`localhost`, `inboxbridge`, `inboxbridge-admin`)
- optional extra comma-separated names from `TLS_FRONTEND_CERT_HOSTNAMES` and `TLS_BACKEND_CERT_HOSTNAMES`

If those configured hostnames change, `cert-init` automatically reissues the
generated leaf certificates on the next compose startup instead of keeping stale
SANs. Client devices still need to trust `certs/ca.crt` for browsers to accept
those generated certificates as valid secure-context TLS.
