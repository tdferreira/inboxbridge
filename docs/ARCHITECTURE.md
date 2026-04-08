# Architecture notes

## Main flow

The backend is still primarily organized by layer, but the polling slice now
anchors the first feature-oriented backend subpackage under
`dev.inboxbridge.service.polling`, the extracted mail-source collaborators now
live together under `dev.inboxbridge.service.mail`, and the destination import
/ delivery path now lives under `dev.inboxbridge.service.destination`. The
coordinator, live-run, stats, source-mailbox protocol, and destination-delivery
classes that evolve together no longer have to keep growing in the flat
top-level `service` package.
The browser polling REST surface now also lives under
`dev.inboxbridge.web.polling`, so the authenticated `/api/poll` endpoints sit
next to the polling model they expose instead of staying in the flat top-level
`web` package.
The auth and browser-session services now also live behind
`dev.inboxbridge.service.auth`, so login, passkeys, session issuance,
per-session device/location formatting, and auth-security setting resolution
can evolve together without leaking package-private test seams back into the
older flat service namespace.
The remote-control surface now also has explicit feature seams:
`dev.inboxbridge.service.remote` owns remote sessions, remote service-token
auth, remote run rate limiting, and the remote control/dashboard projection,
while `dev.inboxbridge.web.remote` owns the remote auth and remote control REST
resources that sit on top of that model.
The per-user mailbox/config/preferences slice now also has an explicit feature
boundary: `dev.inboxbridge.service.user` owns user-managed source mailboxes,
user destination mailbox config, per-user polling overrides, UI preferences,
and runtime resolution of user-accessible source bridges, while
`dev.inboxbridge.web.user` owns the authenticated `/api/app` resource that
exposes those per-user mailbox/config endpoints.
The admin/operator slice now also has explicit feature seams:
`dev.inboxbridge.service.admin` owns the admin dashboard summary model,
application-mode switching, and application-user lifecycle/admin-user
management, while `dev.inboxbridge.web.admin` owns the `/api/admin` dashboard
and `/api/admin/users` resources that expose those admin-only operations.
The browser auth/account surface now also has a narrower web boundary:
`dev.inboxbridge.web.auth` owns the `/api/auth` and `/api/account` resources,
so browser session/login/passkey/account-security transport concerns evolve
next to each other instead of staying in the flat top-level `web` package.
Within that still mostly layer-oriented backend, the provider OAuth web surface
now also uses a narrower seam under `dev.inboxbridge.web.oauth`: the Google and
Microsoft OAuth REST resources live alongside their callback-page renderers and
shared callback helpers there, so the large browser callback HTML/JS stays out
of the resource classes and the provider-specific web flow evolves behind one
feature package.

1. `PollingService` runs on schedule or manually via REST
2. `PollingService` resolves the eligible sources and lets a bounded set of virtual-thread workers claim them just in time
3. `PollingService` delegates one eligible source at a time into `PollingSourceExecutionService`, which owns the per-source execution path
4. Each source execution binds the current live-run/source scope, so mailbox and destination resources can register cancellation handles for that source
5. `MailSourceClient` orchestrates source fetches and delegates protocol I/O to `MailSourceFetchService`
6. `ImportDeduplicationService` checks whether a message was already imported
7. The configured destination implementation prepares the append/import target
8. `MailDestinationService` dispatches to the active destination provider, such as Gmail API import or IMAP APPEND
9. Import metadata is stored in PostgreSQL

The worker count is intentionally bounded, so InboxBridge can overlap unrelated
mailboxes without abandoning the existing provider-throttle rules that protect
source and destination hosts from being hammered.

One implementation constraint matters here: virtual-thread workers do not carry
the original request context with them, so any repository-backed helper they
call must open its own short `@Transactional` read/write boundary instead of
depending on ambient request-scope entity access.

Two shared backend helpers now keep that flow from drifting into copy-pasted
infrastructure details:

- `PublicUrlService` resolves the canonical browser-facing base URL from
  `PUBLIC_BASE_URL` or the derived `PUBLIC_HOSTNAME` / `PUBLIC_PORT`
  combination, so OAuth redirect defaults and other externally visible links do
  not each rebuild that precedence logic. It now lives under
  `dev.inboxbridge.service.oauth` with the rest of the provider/config OAuth
  slice instead of staying as the last flat top-level backend service.
- `MailSessionFactory` now lives under `dev.inboxbridge.service.mail` and
  builds the Jakarta Mail `Session` instances for source IMAP, source POP3,
  IMAP IDLE, and destination IMAP APPEND traffic from one typed
  `MailClientConfig` mapping. Connection and operation timeouts therefore stay
  aligned across source fetch, source post-poll actions, destination append,
  and long-lived IDLE watches instead of being hard-coded in each service.
- `MailSourceFolderService` and `MailSourceMessageMapper` now keep source-folder
  discovery/spam probing and fetched-message materialization/source-key logic
  out of `MailSourceClient`.
- `MailSourceCheckpointSelector` now owns checkpoint-aware IMAP and POP3
  candidate selection, including resume-vs-tail-window fallback behavior when
  stored mailbox checkpoints are missing or no longer trustworthy.
- `MailSourceConnectionService` now owns mailbox-store connection plus the
  retry-once OAuth token invalidation/refresh path for Microsoft and Google
  source sessions.
- `MailSourceConnectionProbeService` now owns source mailbox connection probes,
  folder listing, and spam-folder inspection so connection-test behavior can
  evolve separately from the polling pipeline itself.
- `MailSourceFetchService` now owns IMAP and POP3 fetch execution, including
  destination-aware checkpoint lookups, per-folder IMAP iteration, metadata
  prefetch, and chronological cross-folder ordering before messages re-enter
  the higher-level polling pipeline.
- `MailSourcePostPollActionService` now owns source-side IMAP mutations after a
  successful import or duplicate match, including mark-as-read, move, delete,
  and best-effort `$Forwarded` handling against the fetched message's actual
  source folder.
- `ImapIdleWatchService`, `ImapIdleHealthService`,
  `SourceDiagnosticsService`, and `SourceMailboxConfigurationChanged` now also
  live under `dev.inboxbridge.service.mail`, so IMAP IDLE watch lifecycle,
  watcher-health fallback tracking, source diagnostics aggregation, and the
  mailbox-configuration change event stay in the same mail-protocol slice as
  the rest of the source mailbox runtime.
- `EnvSourceService` and `MimeHashService` now also live under
  `dev.inboxbridge.service.mail`, so env-managed source filtering and MIME
  fallback-key generation stay with the rest of the source-mail runtime
  instead of lingering in the flat top-level `service` namespace.
- `MailDestinationService`, `GmailApiMailDestinationService`,
  `ImapAppendMailDestinationService`, `GmailImportService`,
  `GmailLabelService`, `DestinationIdentityKeys`, and
  `MailboxConflictService`, and `DestinationIdentityUpgradeService` now live together under
  `dev.inboxbridge.service.destination`, so destination identity, mailbox-link
  checks, Gmail import, label resolution, IMAP APPEND delivery, and the
  legacy destination-identity upgrade backfill evolve inside one
  delivery-focused backend slice.
- `AuthService`, `PasskeyService`, `UserSessionService`,
  `AuthSecuritySettingsService`, `AuthClientAddressService`,
  `AuthLoginProtectionService`, `RegistrationChallengeService`,
  `SessionClientInfoService`, `SessionDeviceLocationFormatter`,
  `SessionLocationAlertService`, and `GeoIpLocationService` now live together under
  `dev.inboxbridge.service.auth`, so sign-in flow, passkey ceremonies, browser
  session issuance, session-security presentation, and auth-hardening settings
  stay in one auth-focused backend slice.
- `SecretEncryptionService` now lives under
  `dev.inboxbridge.service.security`, so the AES-GCM secret-storage primitive
  shared by auth, OAuth, and user-managed mailbox configuration has an explicit
  security-focused home instead of staying in the flat top-level `service`
  package.
- `RemoteControlService`, `RemoteSessionService`,
  `RemoteServiceTokenAuthService`, and `RemotePollRateLimitService` now live
  together under `dev.inboxbridge.service.remote`, while
  `RemoteAuthResource` and `RemoteControlResource` now live under
  `dev.inboxbridge.web.remote`, so the remote-only auth/session/rate-limit
  model evolves behind a dedicated feature boundary instead of staying split
  across the flat service and web packages.
- `UserEmailAccountService`, `UserMailDestinationConfigService`,
  `UserPollingSettingsService`, `UserUiPreferenceService`, and
  `RuntimeEmailAccountService` now live together under
  `dev.inboxbridge.service.user`, while `UserConfigResource` now lives under
  `dev.inboxbridge.web.user`, so the authenticated `/api/app` mailbox/config
  surface evolves behind one per-user feature package instead of staying mixed
  through the older flat service/web namespaces.
- `AdminDashboardService`, `AppUserService`, and `ApplicationModeService` now
  live together under `dev.inboxbridge.service.admin`, while `AdminResource`
  and `UserManagementResource` now live under `dev.inboxbridge.web.admin`, so
  admin dashboard aggregation, operator-only mode switches, and application
  user-management flows evolve behind one admin-focused feature boundary.
- `AuthResource` and `AccountResource` now live together under
  `dev.inboxbridge.web.auth`, so browser login, registration, current-session,
  passkey, and own-account security endpoints evolve behind one narrower web
  feature package instead of staying mixed into the flat top-level `web`
  namespace.
- the provider callback UI now lives in the React admin app under
  `/oauth/google/callback` and `/oauth/microsoft/callback`, while the backend
  OAuth resources keep ownership of the real provider callback URLs under
  `/api/.../callback`, validate callback state there, and redirect the browser
  into the frontend-owned callback routes with only the minimal provider result
  parameters needed for the browser exchange flow
- `PollingSourceExecutionService` now owns the per-source polling pipeline that
  used to sit inside `PollingService`: destination-link checks, spam/junk
  probes, dedupe-vs-import decisions, source/destination throttle accounting,
  source checkpoints, live per-message progress updates, and persisted
  poll-event/cooldown recording now evolve behind one narrower seam.
- `PollingSettingsService`, `SourcePollingSettingsService`,
  `SourcePollingStateService`, `SourcePollEventService`,
  `ImportDeduplicationService`, `ManualPollRateLimitService`,
  `PollThrottleService`, `PollThrottleKeys`, `PollDecisionSnapshot`, and
  `PollCancellationService` now live under `dev.inboxbridge.service.polling`,
  so the broader scheduler/rate-limit/checkpoint/dedupe infrastructure evolves
  inside the polling feature package instead of staying split between the
  feature slice and the old flat service namespace.
- `PollingResource` now lives under `dev.inboxbridge.web.polling`, and it
  exposes explicit setter-based test wiring for focused non-CDI tests instead
  of relying on package-private field access from the older flat web package.
- `MailSourceStandaloneFactory` now centralizes the non-CDI assembly path for
  that `dev.inboxbridge.service.mail` slice, so focused unit tests and
  GreenMail-backed integration tests construct the same helper graph instead of
  each service re-creating its own ad-hoc fallback dependencies.
- Inside that slice, the extracted helper services now also use explicit
  constructor injection for their collaborator graph, which narrows the hidden
  dependency surface and gives Quarkus component tests a clearer runtime wiring
  seam than broad field injection.
- With those helpers in place, `MailSourceClient` can stay focused on polling
  orchestration instead of re-implementing every protocol, OAuth, or source
  mutation detail itself.
- `PollingService` can now stay focused on run-level coordination such as
  scheduler eligibility, manual rate limits, live-run lifecycle, and
  concurrent worker orchestration instead of also carrying the full source
  execution pipeline inline.
- `PollingTimelineService` now owns the hourly/daily/weekly/monthly/custom
  bucket generation used by polling statistics, so `PollingStatsService` can
  focus on repository scoping and event aggregation instead of also embedding
  the timeline-bucketing implementation.
- `PollingLivePresentationService` now owns viewer-scoped live snapshot shaping
  plus live notification assembly, so `PollingLiveService` can focus on state
  transitions, permissions, cancellation, and subscriber fan-out instead of
  also embedding the presentation policy for every live event.

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
2. `PollingLiveService` coordinates the active run while the mutable queued/running/completed source model now lives in `PollingLiveRunState`
3. `PollingLivePresentationService` turns that state into viewer-scoped snapshots and notification payloads
4. `GET /api/poll/live` and `GET /api/admin/poll/live` return an immediate snapshot so a browser can hydrate mid-run without waiting for the next event
5. `GET /api/poll/events` and `GET /api/admin/poll/events` stream SSE updates plus live notifications as the run changes, and they can also push targeted `session-revoked` events so a browser session signed out elsewhere leaves immediately
6. authenticated `POST` controls call the live service to pause, resume, stop, move one queued source to the front, or retry a completed/failed source
7. `PollingService` now asks `PollingLiveService` for the next source just in time instead of precomputing the full queue, so reprioritize controls affect the remaining queue order while a run is active
8. several workers may therefore dequeue and run different sources concurrently, and the live snapshot's per-source `state` is the authoritative UI signal for row-level running indicators
9. the active source path now checks the live pause/stop state between fetched messages, so pause/stop requests take effect during the current source rather than only after it finishes
10. a stop request also runs the registered cancellation actions for that run, which closes active mailbox sessions and interrupts worker threads so blocking I/O has a better chance to unwind promptly

That keeps the poll engine authoritative in the backend while letting the UI show real-time progress without hammering every row with its own polling loop.

The live service is therefore split similarly to the poll engine itself: the
service owns coordination, permissions, SSE fan-out, and control flow, while
`PollingLiveRunState` owns the in-memory run/source state shape plus queue and
position helpers. That keeps the live service from mixing transport/control
behavior with the mutable state model.

The broader REST layer now also keeps one repeated concern centralized:
`WebResourceSupport` owns the standard translation of validation-style
`IllegalArgumentException` and `IllegalStateException` failures into
`BadRequestException`, so resource classes can stay thinner without each method
repeating the same `try/catch` boilerplate.

Runtime-level backend validation now has two intentionally different seams:

- fast CDI-backed component checks through `@QuarkusTest` for shared helper
  wiring
- a small packaged-runtime smoke layer through `@QuarkusIntegrationTest`
  backed by Maven Failsafe

That packaged smoke path runs the built Quarkus jar under the `%test` profile
with an in-memory H2 datasource and no external TLS certificate requirement, so
health, browser-auth session wiring, remote-session protection, OAuth callback
redirect behavior, the authenticated `/api/app` protection surface, the admin-only
`/api/admin` surface, the browser `/api/auth` session/account surface, the
authenticated `/api/poll` surface, and startup regressions can be caught
without depending on the normal Docker/PostgreSQL runtime. Those packaged
`verify` runs should be executed sequentially because concurrent Quarkus
builds can corrupt the shared `target/quarkus-app` output.

The user-deletion and session-revocation cleanup path now also keeps
transaction ownership at the service layer. Repository helpers in that slice
remain thin data-access methods, while `AppUserService`, `PasskeyService`, and
`UserSessionService` own the surrounding transactional workflow that deletes
per-user config, per-source polling rows, imported-message history, passkeys,
and browser sessions.

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
- for IMAP sources, one checkpoint row per watched folder, each storing that folder plus the last seen `UIDVALIDITY` / UID checkpoint scoped to the destination mailbox identity that was active when the checkpoint was recorded
- for POP3 sources, the last seen UIDL checkpoint used to resume from newer mail when that POP mailbox still advertises the older message, also scoped to the active destination mailbox identity

That destination-aware checkpoint scoping matters during destination changes: when a user repoints InboxBridge to a different destination mailbox, the old source checkpoint is no longer reused for that new target, and dedupe also shifts to the new destination identity, so already-seen source mail can be replayed into the new destination under the normal fetch-window rules.

The scheduler checks that state on every run so one blocked or throttled mailbox does not stall unrelated source email accounts.

Scheduler-triggered runs now also filter out sources whose current eligibility says `INTERVAL`, `COOLDOWN`, or `DISABLED` before creating any live run state, so the user-facing live coordinator only tracks real runnable work instead of every scheduler tick.

Scheduler-triggered runs also skip sources whose fetch mode is `IDLE`, because those sources are expected to wake the shared import pipeline from the background watcher instead of being batch-polled every few minutes. When a source mailbox is saved or deleted, an after-commit event also forces the watcher registry to refresh immediately so newly switched `IDLE` sources do not wait for the normal periodic rescan. On backend startup the watcher registry is refreshed immediately as well, so eligible `IDLE` sources reconnect within the normal Quarkus boot path instead of waiting for the 30-second sweep.

Realtime `idle-source` activations now also get priority over scheduler contention. If a scheduler batch is already running, InboxBridge can start a single-source `idle-source` run alongside that scheduler batch for the newly activated IMAP source instead of repeatedly re-queueing it until the whole batch finishes. Other busy states still return `poll_busy`, so manual/source-scoped runs keep the existing safety guardrails.

IDLE sources now also keep a lightweight watcher-health record. Healthy watchers remain the primary ingestion path and scheduler polling continues to skip those sources. If a watcher disconnects and stays unhealthy beyond the normal reconnect window, the scheduler temporarily treats that source as eligible again so mail still flows while the watcher keeps retrying in the background. Once the watcher reconnects, scheduler fallback stops automatically and the source returns to pure IDLE-driven ingestion.

Successful scheduled polls now persist the next eligible time on aligned interval boundaries rather than `last success + interval`, so the single scheduler loop still wakes up every few seconds but each source's actual cadence follows predictable slots such as `:00`, `:05`, and `:10` according to that source's effective polling settings.

Polling statistics are intentionally timezone-aware at the request boundary: the browser sends the signed-in user's effective IANA timezone in `X-InboxBridge-Timezone`, and `PollingStatsService` uses that zone when building hourly, daily, monthly, and custom buckets. The persisted timestamps stay in UTC, but chart labels such as `09:00` or `2026-04-04` are computed relative to that user's effective timezone. The effective zone itself comes from the persisted user UI-preferences model: `AUTO` follows the current browser timezone, while `MANUAL` pins the account to one chosen timezone so the same user can keep consistent charts and date-time rendering across different devices or travel scenarios. Date-format selection is modeled separately: `AUTO` follows the selected locale's default pattern, while manual preset or custom patterns only change how the already-localized instant is rendered. The admin-side scheduled-run anomaly signal is layered on top of those viewer-local buckets in the browser: it warns only admins, keeps the floating notification for at most 24 hours after the suspicious bucket, and lets the static section warning age out after one week.
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

Import dedupe is now destination-mailbox-identity aware and layered. InboxBridge first checks the persisted source message key for that destination identity and source account. For IMAP those source keys now prefer `folder + UIDVALIDITY + UID`, while POP3 source keys prefer `UIDL`. If that protocol-native identity does not match, InboxBridge next falls back to raw MIME SHA-256 and only then to the normalized `Message-ID` header for that same source account. That keeps protocol-native mailbox identifiers authoritative while still leaving one last heuristic safety net for providers that change UIDs, UIDLs, or MIME details.

To keep upgrades from older databases consistent, startup now also runs a lightweight reconciliation pass that backfills legacy `destination_key`-scoped imported-message rows and legacy/null checkpoint destination keys to the current resolved mailbox identity for each configured destination. This only upgrades rows that still look legacy-scoped; already destination-aware identities are left untouched.

IMAP sources now also support watching more than one folder at a time by storing a comma-separated folder list in the existing source folder field. Polling selects candidate mail independently per folder, IMAP IDLE starts one watcher per configured folder, and post-poll source actions reopen the fetched message's actual source folder so move/delete/read updates still apply to the right mailbox path.

For UI-managed OAuth2 source accounts, the persisted source row now tracks whether the user wanted the source enabled before OAuth was fully connected. If no usable refresh token exists yet, InboxBridge saves that source disabled, stores the pending-enable intent, and only flips the source to enabled after the OAuth flow completes and an immediate mailbox connection test succeeds with the stored provider credential.

Those values can be overridden live from `Administration -> Global Poller Settings`.

That is still deliberately simpler than a full mailbox-sync engine. The next evolution should be:

- operator/admin diagnostics that expose destination identity, checkpoints, IMAP IDLE watcher health, persisted poll-decision history, and explicit source alerts for disconnected IDLE watches, repeated cooldown loops, and sustained throttling directly in the source detail UI

## Package map

- `config`: config mapping interfaces
- `domain`: small internal domain objects
- `dto`: request / response DTOs
- `persistence`: database entities and repositories
- `service`: fetch, import, dedupe, label, and OAuth logic
- `web`: REST resources

Within those packages, the current preference is to keep protocol/session
construction, browser-URL derivation, and similar cross-cutting infrastructure
in narrowly scoped shared services instead of rebuilding them inside large
feature services or REST resources.

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
