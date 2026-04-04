# Admin UI

The admin UI is a separate React + Vite application served by Nginx in its own container. It talks to the Quarkus backend through proxied REST endpoints under `/api/...`.

The frontend dependency set is intentionally kept on current stable major versions, and now targets React 19, Vite 8, Vitest 4, jsdom 29, and Recharts 3.x.

## Structure

```text
admin-ui/src
├── App.jsx
├── components
│   ├── account
│   ├── admin
│   ├── auth
│   ├── common
│   ├── destination
│   ├── emailAccounts
│   ├── gmail
│   ├── layout
│   ├── polling
│   └── stats
├── lib
└── test
```

Key design choices:

- `App.jsx` owns shared data loading and top-level workspace composition.
- Imperative UI slices are being moved into controller hooks under `src/lib/...`, including auth and security flows, source email account orchestration, destination mailbox actions, polling flows, and admin user-management actions.
- Reusable presentational components live under `src/components/...`.
- `src/components/layout/SetupGuidePanel.jsx` gives users a first-run checklist inside the app itself.
- the setup guide entries are clickable links that focus the corresponding working section
- the setup guide uses neutral / green / red state styling to reflect pending, complete, and error conditions
- the setup guide auto-collapses once every tracked step is complete
- once every tracked step is complete, the setup guide can also be hidden entirely and it automatically reappears if any requirement later becomes invalid again
- `My InboxBridge` and `Administration` now keep separate quick-setup visibility preferences, and the `Preferences` dialog controls the guide for the currently selected workspace instead of one generic global toggle
- users can opt into per-account persisted collapse state for the major admin-ui sections
- users can now also persist a custom per-workspace section order and reset it back to the default arrangement from `Preferences`
- section reordering controls are only shown while `layout editing` is enabled from `Preferences`, and sections can then also be rearranged by drag-and-drop with a dotted placeholder
- layout editing now reorders against the actual visible sections in the active workspace, so the last visible section can be moved down into the final slot or moved back out again without getting stuck
- expanding any major section now forces a fresh data reload for that section and shows an inline loading indicator while the refresh is running
- password changes are exposed from the hero/header controls instead of being buried inside Gmail setup
- the same hero/header security area now also handles passkey enrollment and removal
- passkey registration now opens in a focused modal dialog instead of stretching the security panel inline layout
- the login screen supports passkey sign-in for users who have already enrolled one
- the login form only prefills the bootstrap `admin` / `nimda` credentials while the untouched bootstrap admin is still in its original first-login state; after a password change, passkey enrollment, or user removal, the form falls back to empty fields
- passkey UI is intentionally disabled on raw IP hosts because WebAuthn does not work there; LAN access should use a real hostname configured in `SECURITY_PASSKEY_*` instead of a bare address like `192.168.50.6`
- the app now also ships a dedicated `/remote` mobile-first remote-control surface with its own scoped session, tiny source list, and poll-now actions for phones and quick-access devices
- that `/remote` surface now also hydrates the active live poll snapshot on load, listens to the SSE progress stream, highlights the source or sources currently being processed, and exposes pause/resume/stop plus per-source `Move Next` / `Retry` controls when the current remote viewer can manage the run
- disabled source accounts are filtered out of `/remote`, so the quick-access list only shows sources that can actually be triggered from that surface
- collapsed source cards on `/remote` now use tighter spacing and always-visible summary badges for owner, folder, polling cadence, and latest result, so the source list fits more comfortably on smaller screens
- `/remote` now folds live polling state and controls into the existing source cards instead of rendering a separate `Live Poll Progress` source list, so each source row stays the single place for status, actions, and expanded error details even when several sources are `RUNNING` concurrently
- expanded source details on `/remote` now also keep the latest fetched/imported/duplicate counts under `Last result`, so those totals remain visible after the transient live-progress row disappears
- the `/remote` sign-in screen now has its own language selector, and the authenticated remote surface adopts the language saved in the signed-in user's Preferences after login instead of staying pinned to the browser default
- the `/remote` surface should not render raw English helper strings; status pills, live-progress copy, action buttons, and the install/PWA guidance card all route through the translation dictionary the same way as the main workspace
- the main `My InboxBridge` workspace now includes a dedicated `InboxBridge Go` launch card so the lightweight `/remote` page is discoverable from the normal dashboard
- the `/remote` surface can now expose an in-app install prompt when the browser considers that remote page installable as a PWA
- the `/remote` surface now also keeps a visible install-help card even when the browser uses a manual install path, with guidance for Chrome/Edge, Safari `Add to Home Screen` / `Add to Dock`, Firefox Android install, and a note that Firefox desktop does not currently expose a full install flow
- the `/remote` install-help card now also includes a `Not now` action that hides the section until the user reopens it from the hero hamburger menu, so the PWA guidance can be postponed without disappearing entirely
- InboxBridge Go now groups install guidance, device-location sharing, and sign-out inside a top-right hero hamburger menu instead of exposing those utility actions as separate inline banner buttons
- the unauthenticated InboxBridge Go login screen now shows a single `InboxBridge Go` heading, instead of repeating the same label in a blue eyebrow directly under the language picker
- the unauthenticated InboxBridge Go login screen also keeps explicit spacing between the language picker and the main title so the top of the card does not feel cramped after removing the old blue eyebrow
- the InboxBridge Go login card now uses the standard `Sign in` button label, and any session-expired or other login notice is rendered below the sign-in actions instead of above the form copy
- the InboxBridge Go login language control is now a mobile-friendly flag button in the top-right corner of the login card that opens a floating menu of languages showing both the language label and its flag, instead of rendering as a plain labeled select dropdown
- the main My InboxBridge login screen now uses that same top-right flag language menu on the login card, while the registration dialog keeps its labeled selector
- the Preferences dialog language control now also uses the shared flag-based menu, so language selection is visually consistent across login flows and in-app preferences
- the Preferences dialog language menu now opens to the right of its trigger instead of growing left over the field label, and the InboxBridge Go login picker uses remote-specific menu surfaces so its button and dropdown visually match the remote page
- the InboxBridge Go language picker button and dropdown now use fully opaque white surfaces, which keeps the control legible against the remote gradient background without the washed-out transparency
- the `/remote` bootstrap path now injects the route-scoped manifest and related mobile-web-app metadata into the document head so Chromium can evaluate `/remote` for installation without making the main workspace advertise itself as a PWA
- browser/device geolocation prompts are now mobile-safe: InboxBridge only auto-captures when the browser already reports permission as granted, while phones and other gesture-gated browsers should rely on the explicit `Share Device Location` action
- the Security `Sessions` tab now also shows a best-effort browser label and device type for each session, derived from the stored `User-Agent`, so users can quickly tell desktop/admin-ui sessions from mobile/remote-control sign-ins
- when one session signs out another from the shared `Sessions` tab, both the main admin UI and InboxBridge Go now react immediately through a targeted authenticated SSE `session-revoked` event instead of waiting for the next fetch to return `401`
- both UIs also verify their session immediately if that authenticated SSE stream drops unexpectedly, which covers browsers that terminate the revoked stream before exposing the final revoke event to client-side JavaScript
- the main app also uses that authenticated SSE channel for immediate new-sign-in warnings, and when one of those session-activity notifications arrives it refreshes the Sessions data in the background so the notification and the Security tab stay aligned
- both the main app and `/remote` now offer an explicit opt-in browser location prompt for the current session so device-reported location can be stored separately from Geo-IP
- both the main app and `/remote` now auto-capture device location only when geolocation permission is already granted, while still leaving an explicit share action and a retry path in the Sessions UI when no sample was saved
- when a session includes device coordinates, the Sessions view now tries to turn them into a friendlier place label in the browser and exposes an `Open in Maps` action for that device-reported location
- the login screen intentionally avoids exposing live bootstrap-account state to unauthenticated visitors; bootstrap credentials are documented in the operator docs instead
- the unauthenticated login screen now also exposes the language selector directly, and the self-registration modal mirrors that selector so users can pick their locale before signing in
- the main login screen and `/remote` now start with a username-only step, then move into the same generic continue-sign-in step instead of exposing account-specific password/passkey hints up front
- the main login card title is now simply `InboxBridge`, and the eyebrow is hidden when it would only repeat that same product name
- the admin UI installs a same-origin fetch wrapper during bootstrap so unsafe browser writes automatically include the current CSRF header for the authenticated session, while cross-origin requests are left untouched
- live polling now hydrates an immediate snapshot after sign-in and keeps Pause/Resume/Stop controls visible in the polling section headers whenever the current viewer can control the active run, instead of making those controls depend only on the next SSE event or the inner progress panel
- local HTTPS trust still matters for the browser UX: passkeys/WebAuthn, PWA installability, and some secure-context APIs require the browser/device to trust the local CA in `certs/ca.crt`, not only the generated leaf certificate
- self-registration is launched from a dedicated `Register for access` button and uses a modal dialog instead of always rendering the full form
- self-registration now also loads a real CAPTCHA challenge before the request can be submitted; the default path is a self-hosted ALTCHA proof-of-work flow that does not require any external registration or token
- the self-registration modal keeps its anti-robot wording generic and user-facing; provider and challenge implementation details stay in the admin configuration surface rather than the normal registration copy
- the ALTCHA verification action now yields to the browser before solving so the shared loading-button spinner and `Verifying…` state stay visible while the proof-of-work challenge is processed locally
- when the deployment sets `MULTI_USER_ENABLED=false`, the login screen hides self-registration and the post-login UI hides user-management features entirely
- accounts with both a password and a passkey now use password + passkey login, not passkey-only login
- accounts with only a passkey ignore any typed password and fall through into the passkey prompt instead of stopping on an error
- the dedicated unauthenticated passkey button now appears on that generic continue-sign-in step, starts a discoverable passkey flow without using the typed username as an account lookup, and if a password is already filled in it reuses the normal password + passkey login path
- repeated failed sign-ins are rate-limited per client IP address with an exponential lockout, so a hammered login screen starts blocking new attempts for progressively longer periods
- administrators can tune those login lockout, self-registration anti-robot, and Geo-IP session-visibility defaults from a dedicated `Authentication Security` section in the Administration workspace instead of editing `.env`
- the `Edit Authentication Security` dialog now follows the same grouped-card structure as the polling editor, separating login protection, registration protection, CAPTCHA provider selection, CAPTCHA provider configuration, Geo-IP provider chain, Geo-IP timing, provider-specific configuration, and effective values
- user-facing secret-storage labels now stay implementation-neutral, using copy like `Encrypted storage` instead of surfacing backend technology names in the UI
- users can remove their password and run the account in passkey-only mode
- self-service password removal and passkey deletion are guarded by confirmation modals before the backend call is sent
- the final remaining passkey cannot be removed while the account has no password configured
- admins can set a temporary password for another user and wipe that user’s passkeys
- admin password reset uses a focused modal dialog instead of an always-open inline form
- that reset-password dialog shows the live password policy checklist so admins can see the temporary password requirements before submitting
- admin actions that suspend/reactivate a user, force a password change, or wipe passkeys are also guarded by confirmation modals
- admins cannot demote themselves out of the admin role from the UI
- user creation now starts from a dedicated modal dialog instead of an always-visible inline form
- that create-user dialog blocks duplicate usernames up front and shows the same password policy checklist used elsewhere in the app
- the user list now follows the same expandable-entry pattern as source email accounts, with a `...` contextual menu for suspend, role, password, and passkey actions
- expanded user entries are now split into clear detail subsections such as `User Configuration`, `Gmail Destination`, `Poller Settings`, `Passkeys`, and `Source Email Accounts`
- contextual `...` menus no longer duplicate expand/collapse actions; expansion is handled directly by clicking the row itself
- admins can override scheduled polling enablement, poll interval, and fetch window without changing `.env`
- fetch window help now explicitly explains that InboxBridge rechecks the latest `N` messages on each run rather than paging backward automatically, and recommends temporarily raising the window when doing historical backfill
- admins now see an admin-only `Global Poller Settings` section for deployment-wide polling controls, while the actual global override form lives in a focused modal dialog opened from `Edit Poller Settings`
- each user now gets a dedicated `My Poller Settings` section whose main page stays focused on effective values while the actual override form lives in a focused modal dialog opened from `Edit Poller Settings`
- polling analytics now live in separate `Global Statistics` and `My Statistics` sections instead of being mixed into the settings editors
- those statistics views now show line-chart trends for imports, duplicates, and errors, along with provider breakdowns, source-account health buckets, manual-vs-scheduled run counts, and average poll duration
- expanded source-email-account rows now also show the same statistics model scoped to that single account, and expanded admin user rows now show the same statistics model scoped to that selected user
- source-email-account statistics now suppress deployment-wide account-health counters and instead show source-relevant values such as imported-message totals, error-poll counts, provider breakdown, and manual-vs-scheduled poll activity
- the nested statistics cards inside each expanded source-email-account row and each expanded admin user row can now be collapsed independently, and they default to collapsed when there is not yet any meaningful data to render
- the statistics charts now support a `Custom` range that opens a modal dialog for `date-time from` and optional `date-time to`; the charts then reload scoped timeline data for that selected window
- admin users now get a workspace switcher that separates their normal user-facing setup flow from the deployment `Administration` controls
- those admin/My InboxBridge workspaces are now also addressable through URL paths, with `/` remaining the canonical `My InboxBridge` workspace and `/admin` (or its translated admin slug) opening the administration workspace directly; older localized user slugs are normalized back to `/`
- inside those workspaces, the movable content sections can now be reordered independently while the header and workspace switcher remain fixed in place
- the default `My InboxBridge` order is `Quick Setup Guide`, `My Destination Mailbox`, `My Source Email Accounts`, `My Polling Settings`, `InboxBridge Go`, `My Statistics`
- the default `Administration` order is `Quick Setup Guide`, `Global Polling Settings`, `OAuth Apps`, `Users`, `Authentication Security`, `Global Statistics`
- the statistics charts now use `Recharts 3.x`, which adds shared hover tooltips and cleaner multi-series trend rendering without migrating the whole UI to Material UI
- contextual `actions` buttons now render as compact hamburger menu icons while keeping the same translated accessibility labels and tooltips
- that hamburger icon styling now comes from the shared global stylesheet so the user list and source-email-account list render the same menu trigger
- each source email account now also has its own poller-settings dialog and `Run Poll Now` action in the contextual `...` menu
- while a single source email account poll is running, that account’s status pill switches to a spinner-backed running state instead of still showing the previous success/error label
- the running status pill now keeps its spinner visibly aligned next to the label instead of shrinking inside the badge
- the per-account poller dialog title now uses the source email account ID directly so it is always clear which account is being configured
- env-managed source email accounts load those per-account poller actions through admin-only endpoints, while UI-managed source email accounts use user-scoped endpoints
- user-scoped source-email-account actions no longer fall back to env-managed sources with the same ID, so the `...` menu always addresses the DB-managed account entry that was clicked
- the source-email-account detail view now surfaces poll cooldown/backoff state, next scheduled poll time, and the last recorded provider failure
- OAuth2 source email accounts now also show whether the provider connection is already established, and the Microsoft action switches between connect/reconnect based on that state
- the source-email-account area is now presented as `My Source Email Accounts`, with add/edit happening in a wider modal dialog instead of an always-visible form
- the source-email-account dialog includes provider presets, auth-aware field visibility, inline help tooltips, and duplicate-ID validation before save
- the source-email-account dialog now also includes a `Test Connection` action that verifies the entered IMAP/POP3 settings against the source server before the account is saved and reports protocol, endpoint, TLS, authentication, and mailbox reachability details
- those source-email-account test results are rendered below the dialog action buttons, and the modal itself now scrolls safely inside the viewport so long diagnostics never push the buttons off-screen
- source-email-account contextual menus auto-close on outside click or `Escape`, and now anchor to the `...` button with viewport-aware placement so they flip above when there is no room below
- compact `...` action buttons now keep a fixed square size, and shared button styling keeps labels on a single line instead of wrapping
- env-managed source email accounts appear in the same list with a read-only `.env` badge only for the account named `admin`; other users never see those env-backed entries
- placeholder fallback values do not count as env-managed source email accounts, so an empty `.env` source configuration produces no `.env` account entry in the UI
- DB-managed source email accounts cannot reuse an ID that is already occupied by an env-managed `.env` account
- Outlook / Microsoft source connects now retry once with a freshly refreshed access token if the provider rejects the cached token as invalid before its recorded expiry time
- busy poll responses now include the active trigger/source details so the UI can show why a click could not start a new run yet
- notification copy actions now use compact icon-only buttons with tooltip text, and floating notifications become less transparent on hover so long error text is easier to read
- the floating notification stack also keeps a stronger default opacity and a subtle blur backdrop so messages remain readable over dense UI sections
- floating notifications now wrap long text inside the card instead of overflowing past the viewport edges
- the floating notification stack is now capped to the number of banners that fits the current viewport, so older notices stay available in Notifications Center history instead of spilling past the visible page area
- the source-email-account list now reloads after manual poll attempts and whenever an account row is expanded so the details panel reflects the latest polling state and last-event status
- expanding an individual source email account or user entry now also triggers a fresh data load for that item, with visible loading feedback while the refresh is in progress
- live polling progress now shows up in `My InboxBridge`, the admin dashboard, and `/remote`, with a combined determinate line such as `Processing 3 / 50 emails (1.5 MB / 6 MB)` once the backend knows the batch totals; before that, source cards stay on the simpler `Running…` state, and once the batch has been fully examined but the run is still persisting/finalizing the UI switches to copy such as `Finalizing 50 emails (2.9 MB)` instead of leaving the row on a misleading completed `Processing 50 / 50 ...` label
- in `My InboxBridge`, that running source state now stays inside the normal compact status pill height instead of growing into a taller badge, while `/remote` mirrors the same determinate email-and-size progress text directly inside each source card's top summary status pill whenever a source is actively running
- expanded source-mailbox `Last result` summaries in both `My InboxBridge` and `/remote` now surface the imported-byte total as a dedicated size pill when that run actually imported messages, keeping those byte counts separate from duplicates or skipped messages
- the main workspace, admin dashboard, and `/remote` now disable their broad and per-source `Run Poll Now` actions while another live poll is already active, so users cannot start overlapping runs from different surfaces
- the browser clients now treat the authenticated poll SSE stream as stale if no snapshot, progress event, or keepalive arrives for roughly 45 seconds during a running poll; they drop back to snapshot refresh so the UI is less likely to stay stuck on `RUNNING...` forever after a silent stream failure
- if that authenticated SSE stream is unavailable entirely, both the main workspace and `/remote` now keep polling the live snapshot endpoint in the background so a poll started from another browser surface still shows up instead of remaining invisible until refresh
- even while that authenticated SSE stream is still connected, the main workspace and `/remote` now reconcile the live snapshot in the background during active runs so a missed `poll-run-finished` event cannot leave one browser surface stuck on `Running…` after another surface has already returned to idle
- the admin `Global Statistics` section now raises a red anomaly warning if its hourly scheduled-run timeline shows activity far above what the current effective polling interval would permit across the enabled source-mailbox count; the section pulses temporarily to grab attention, a linked notification is added for administrators, and then the section settles into a static warning state so scheduler anomalies remain visible instead of being silently hidden from the charts
- automatic scheduler checks no longer start a visible live run unless at least one source is actually eligible to poll, so interval-skipped sources should not flash `RUNNING` or end with misleading `INTERVAL` failures in the UI
- successful scheduled polling now snaps the next eligible run to aligned clock boundaries instead of drifting from the previous success time, so intervals like `2m`, `5m`, and `10m` stay on `:00`, `:02`, `:05`, `:10`, and similar boundaries
- the `My Destination Mailbox` area now uses an Add/Edit modal workflow instead of keeping the full form inline all the time, and the OAuth connect action only appears once the mailbox has been saved
- when a destination mailbox dialog is open, background page scrolling is locked so wheel and trackpad scrolling stay inside the modal instead of moving the blurred page behind it
- reconnect and unlink actions for destination mailbox links now live inside the destination edit dialog and only appear when that saved provider is actually linked
- the destination mailbox dialog keeps the Outlook IMAP fields visible even for Microsoft OAuth so the user can still confirm or edit host, port, username, TLS, and folder values explicitly, but the save actions now separate plain folder-only saves from `Save and Authenticate` for mailbox-identity changes
- the destination mailbox dialog can test the target mailbox connection as soon as the current settings are sufficient to authenticate
- when a saved IMAP destination mailbox is already linked, the destination folder field inside the edit dialog now loads the real remote folder list and renders it as a dropdown by default, while still allowing manual folder entry as a fallback when needed, including Outlook destinations linked through Microsoft OAuth
- the Gmail account panel distinguishes deployment-shared Google OAuth client credentials from user-specific overrides, but regular users now only see Gmail connection status plus connect/reconnect OAuth while admins keep the advanced override form
- reconnecting Gmail now uses a friendlier `Reconnect Gmail Account` action, warns before replacing the currently linked Gmail account, and the Google callback page reports when the previous linked account was automatically replaced and its old grant revoked
- reconnecting Gmail to the same already-linked account keeps the existing Google grant instead of revoking it; revocation only happens when the newly linked Gmail account is actually different
- the admin UI layout now has an explicit mobile pass, so header controls, section cards, user/mail-account rows, and modal dialogs stack and resize more safely on phones and narrow tablets
- if Gmail returns a confirmed repeated `401` for a user-linked Gmail account, InboxBridge now clears that saved Gmail OAuth link and the UI will show the account as unlinked until the user reconnects it
- the mail-account OAuth provider help text now points users to `docs/SETUP.md`, `docs/OAUTH_SETUP.md`, and Microsoft’s official app-registration guide when Microsoft OAuth2 is selected
- when a source depends on Gmail import but the current account has unlinked Gmail, polling now reports that the Gmail account is not linked instead of surfacing a less clear downstream API failure
- if Gmail unlink cannot revoke the Google-side grant automatically, the UI now tells the user how to remove InboxBridge manually from `myaccount.google.com -> Security -> Manage third-party access -> InboxBridge -> Delete All Connections`
- when that admin-only setup sidebar is absent, the Gmail account panel now expands to the full available width instead of keeping an empty second column
- the admin Gmail account form now shows inline help hints for each configurable field
- connected Gmail accounts can now also be unlinked from the admin UI, which clears InboxBridge's stored Gmail OAuth tokens and attempts a Google-side token revocation when possible
- in the normal operating model, that shared Google OAuth client comes from one deployment-wide Google Cloud project reused across many users, and the `Administration -> OAuth Apps` area only stores that client registration while each user still connects their own Gmail mailbox from `My Destination Mailbox`
- each source mail account row in `My Source Email Accounts` now includes a quick `Enable` or `Disable` action in its contextual menu so users can pause a fetcher without opening the full edit dialog
- the expanded `Last result` details for each source email account now also keep the persisted Spam/Junk mailbox count from that run, matching the completion notification instead of dropping that detail once the toast disappears
- InboxBridge Go now mirrors that Spam/Junk mailbox count in both the expanded source `Last result` pills and the top-level latest remote run summary, so the lightweight remote UI exposes the same mailbox-health signal as the main dashboard
- InboxBridge Go keeps each source card's live state minimal: queued sources may still show queue position and `Move Next`, but the per-card `Fetched/Imported/Duplicates` live summary text and separate `Retry` action were removed so recovery stays on the normal `Poll This Source` button
- that remote source-card separator now belongs to the expanded details block instead of the live-progress area, so a running poll no longer draws an extra horizontal divider unless the user explicitly opens `Show details`
- remote source cards no longer mount an empty live-progress container for active sources, so a `RUNNING` source without queue details or `Move Next` actions no longer grows slightly just because live state exists
- backend poll-throttle lease cleanup is now best-effort, so a transient PostgreSQL disconnect while deleting a `poll_throttle_lease` row logs a warning and lets the lease TTL expire naturally instead of failing the poll just because the cleanup delete could not complete immediately
- persisted source poll history writes are now best-effort too, so a transient PostgreSQL disconnect during the final `source_poll_event` insert logs a warning and preserves the poll outcome rather than turning the run into a database-write error
- InboxBridge Go latest-run banners now distinguish an intentional stop from a clean success, showing `The polling request was stopped.` when the signed-in user stopped it and `The polling request was stopped by an administrator.` when another admin user ended that run
- persisted source stop events now keep a `STOPPED` status instead of being downgraded to `ERROR` just because the stop marker uses the message `Stopped by user.`, which keeps InboxBridge Go source status pills consistent with an intentional stop
- that same `Last result` block now uses a richer execution summary such as `Executed at ... via My InboxBridge` or `Executed at ... by admin via Administration`, while automatic scheduler runs omit the actor and render as automatic execution
- backend Spam/Junk mailbox probing now prefers IMAP special-use folder flags like `\\Junk` / `\\Spam` when the server advertises them, and only falls back to localized folder-name matching otherwise
- the polling area is now framed as `Poller Settings` and focuses on runtime scheduler controls plus polling-health metrics
- `My Polling Settings` now also includes a `Run Poll Now` action for the signed-in user’s own mail accounts
- `My Polling Settings` keeps the broad run and Pause/Resume/Stop controls plus the effective summary, while the detailed `Live Poll Progress` panel now lives only in `My Source Email Accounts` so the same run state is not duplicated across two user sections
- `Global Polling Settings` now asks for confirmation before starting an all-users manual run, and the dialog also exposes the manual-run rate limit configuration used to prevent repeated hammering
- the `Edit Global Polling Settings` modal is now grouped into scheduler, manual-run, source pacing, destination pacing, adaptive recovery, and effective-value subsections so the deployment-wide controls are easier to scan
- broad manual polling runs still respect per-source cooldown and next-window checks, while the single mail-account `Run Poll Now` action remains the explicit force-run path
- effective polling behavior now follows a three-layer precedence chain: deployment-wide defaults, optional per-user overrides for DB-managed sources, and optional per-source overrides on top
- those broad manual polling runs now follow the backend's active source and move the per-row spinner/status pill one mailbox at a time, so the user can see which source is actually being processed right now
- the admin polling section shows a live poll-progress panel driven by authenticated SSE, including pause/resume/stop controls plus per-source `Move Next` and `Retry` actions during the active run; the user workspace keeps those detailed per-source live updates in `My Source Email Accounts` instead
- once a running source has enumerated its current fetch batch, those same live poll surfaces now show a determinate progress meter with copy such as `Processing 3 / 50 emails (1.5 MB / 6 MB)` plus the running fetched/imported/duplicate counters, so large imports no longer look stuck behind a static `Running` badge
- source-account `Last result` summaries in both `My InboxBridge` and InboxBridge Go now also show the total imported payload size for that completed run using human-readable units like `KB`, `MB`, or `GB`
- live pause/stop controls are now honored during the currently active source as well as between queued sources because the backend advances the live queue one source at a time and checks for pause/stop between fetched messages instead of only when the run first starts
- admins see the live panel across user-owned sources with owner labels, while non-admin users only receive the subset of live progress and notifications that belongs to their own mail sources
- if the live SSE stream disconnects, the UI falls back to the live snapshot refresh path so row-level running indicators continue to work until the stream reconnects, including runs where multiple sources are active at once
- that live snapshot fallback now also keeps running during single-source `Run Poll Now` actions and during already-active runs discovered from the first snapshot, so a missed final SSE event does not leave the last source row stuck in `Running`
- disabled source email accounts keep their row-level `Run Poll Now` actions disabled and never show the batch spinner while another source is being processed
- the user poller settings card now uses the same padded section shell as the main dashboard cards, so the form content stays fully inside the card boundaries
- the hero/header now includes a `Preferences` button that opens a modal for language selection, the persisted `Remember layout on this account` toggle, the `Show Quick Setup Guide` toggle, layout-edit controls, and a reset-layout action
- the admin UI enforces HTTPS: nginx redirects plain HTTP traffic on port `80`, and the frontend also upgrades itself to `https://...` if it is ever served over plain HTTP by another deployment path
- the hero/header `Security` button now opens the password, passkey, and session tools in a dedicated modal dialog, with separate tabs so the modal stays less crowded
- the new `Sessions` tab shows recent sign-ins, currently active sessions, the login method and IP address for each one, and sign-out actions for one other session or all other sessions
- that same `Sessions` tab now merges both normal admin-ui sessions and `/remote` remote-control sessions, with a visible session-type label and shared revoke controls
- if the same account signs in elsewhere while this browser is already open, the normal background refresh raises a warning notification that links directly to the Sessions tab
- the header `Refresh` action now also runs that same session-activity poll, so a manual refresh can surface the same new-sign-in notification immediately
- if one browser session revokes another, the revoked browser now detects the next `401` response centrally and immediately returns to the login screen instead of staying on a broken authenticated page
- approximate location is now resolved only when a new session is created, using the backend Geo-IP provider chain when configured; that admin section can now override enablement, provider order, cache TTL, provider cooldown, and request timeout live without editing `.env`
- the Sessions view now also shows an optional device-reported location and capture timestamp for each session when the user allowed browser geolocation on that device, including a best-effort guessed place name plus an `Open in Maps` link when coordinates are available
- when the current browser session supports geolocation but no sample has been captured yet, the Sessions view now labels that state explicitly as pending and offers an inline retry action instead of showing the same `Not shared` copy used for older sessions with no recorded device location
- the Geo-IP editor now exposes a primary-provider dropdown, a chip-based fallback-provider input, provider readiness cards with documentation/terms links, and provider-specific secret inputs such as the optional `IPinfo Lite` token; providers that require missing credentials stay disabled until they are configured
- the same admin dialog now also exposes a primary registration CAPTCHA provider selector plus provider-specific configuration cards; `ALTCHA` is available by default with no extra credentials, while `Cloudflare Turnstile` and `hCaptcha` stay disabled until their required site-key and secret values are configured
- raw duration values such as `PT5M` and `PT0.25S` now expose hover hints with their human-readable meaning in the admin polling and authentication-security settings UI
- the Security dialog now confirms before closing when the password form has in-progress input
- visible labels route through the in-repo translation dictionary instead of mixing translated and raw JSX text
- translated subsection headings and the most prominent detail labels inside the user-management panes now follow the selected language too instead of staying stuck in English
- quick-setup steps, Gmail account fields, poller-setting forms, and source-email-account dialogs now route their visible labels and help text through the same translation dictionary too
- translation regression coverage now includes component-level localized rendering tests for the major UI surfaces plus a catalog test that verifies critical keys exist for every supported locale
- password-policy checklists and passkey cancellation/failure messages now also come from the locale dictionary instead of leaking raw English browser copy into translated sessions
- collapsible panes use compact `+` / `-` window-style controls instead of text-heavy expand/collapse buttons
- collapse buttons expose native hover hints and stay pinned to the top-right corner of the card
- shared button styles now include clearer hover, focus, and pressed states so actions feel more obviously interactive across the UI
- Each component imports its own CSS file for local structure and appearance.
- Shared visual tokens and generic form/layout helpers live in `src/styles.css`.
- shared layout/action primitives now also include reusable `SectionCard`, `CollapsibleSection`, `ButtonLink`, `FormField`, and `FloatingActionMenu` components so new dashboard sections, labeled form rows, contextual menus, and navigational CTA actions do not need one-off structure or button-link styling
- Formatting and API helpers live in `src/lib/...`.

## UI primitives

When building or updating UI in `admin-ui`, prefer the shared primitives under `src/components/common` before introducing a new bespoke component or local layout pattern.

Current design-system foundation includes:

- `SectionCard` for non-collapsible section shells with standard header, copy, actions, and body spacing
- `CollapsibleSection` for top-level workspace/admin panels that need the same section shell plus the standard corner collapse button and section-loading treatment
- `ButtonLink` for navigational CTA links that should look and behave like the existing button system
- `FormField` for standard labeled inputs with optional inline help
- `FloatingActionMenu` for anchored hamburger-menu actions that close on outside click, escape, and scroll-out
- `LoadingButton` for async button states
- `PasswordField` for reusable password entry with show/hide behavior
- `ModalDialog` and `ConfirmationDialog` for modal workflows
- `PaneToggleButton` for compact expand/collapse controls
- `Banner`, `CopyButton`, `DurationValue`, and `InfoHint` for repeated feedback and inline help patterns
- utility prompts such as install/location nudges should compose `SectionCard` instead of introducing bespoke card markup

New top-level sections should compose these primitives rather than recreating `surface-card`, header, action, toggle, or CTA-link markup locally.
The movable workspace sections in both `My InboxBridge` and `Administration` should always render through `CollapsibleSection`, and the layout-edit controller must preserve edit mode while arrow moves or drag-and-drop reorder operations are being applied.
Section reordering must also operate on the effective visible workspace order, so newly introduced movable sections such as `InboxBridge Go` still move correctly for users with older saved layouts, and the top/bottom arrow buttons only enable moves that are actually possible on the rendered list.
Drag-and-drop should resolve against the final pointer-up position using midpoint-based insertion slots between rendered cards, so dragging the first card downward or the last card upward lands in the expected adjacent position, cards do not overshoot by an extra slot, and transient layout-edit mode is not lost during a background preference refresh.

## Tests

Frontend unit tests use `Vitest` and `@testing-library/react`.

Run them with:

```bash
cd admin-ui
npm test -- --run
```

Generate a coverage report with:

```bash
cd admin-ui
npm run test:coverage
```

The Docker Compose build path now runs the frontend Vitest suite during the
admin UI image build, so `docker compose up --build` remains the reliable
end-to-end validation path before manual testing.
If you need to skip the frontend suite for a constrained standalone image build, override the Docker build arg explicitly:

```bash
docker build -f admin-ui/Dockerfile --build-arg RUN_TESTS=false .
```

Current unit coverage focuses on:

- formatter utilities
- reusable error banners with copy-to-clipboard actions
- translation coverage for the major user-visible components plus critical-key coverage in `src/lib/i18n.test.js`
- reusable loading-button behavior
- authentication screen interactions
- reusable password-field show/hide behavior
- passkey browser helpers and passkey panel interactions
- Gmail account guidance and shared-client behavior
- first-run setup guide rendering
- admin security-management controls
- per-user poller settings controls
- per-account poller settings controls and contextual poll-now actions
- the standalone remote-control login and source-poll workflow
- the remote-only PWA install prompt plus the shared per-session browser-location capture flow on both the main app and `/remote`
- source email account dialog presets, auth-aware field visibility, and detected IMAP folder selection after a successful connection probe
- IMAP source email account dialogs now also expose post-poll source-message actions so users can keep the source mailbox untouched, mark handled mail as read, delete it, or move it into another detected/manual folder
- reusable email-account card actions
- language-aware setup guide generation and formatting helpers
- the polling test suite now also covers global/per-user/per-source settings precedence and multi-user destination-isolation regressions on the backend side, with GreenMail integration tests reserved for real mailbox I/O paths

The Google and Microsoft OAuth callback pages include a direct return path back to the admin UI after in-browser token exchange.

That callback flow now uses `Return to InboxBridge`, shows a red `Cancel automatic redirect` action during the 5-second countdown, and keeps the redirect wording product-neutral instead of tying it to the admin UI label.
They also include:

- a one-click code copy button
- if the browser blocks clipboard access, the callback page opens a manual copy dialog so the user can still copy the code or env assignment
- an automatic in-browser exchange attempt as soon as the callback page loads
- the Google and Microsoft callback pages also parse the browser query string directly as a fallback, so the page can still recover the OAuth code/state if they were not rendered into the initial HTML
- both callback pages now show explicit retry guidance when the provider returns `access_denied` or the token exchange reveals missing required scopes
- the Microsoft callback exchange now also returns structured JSON errors, so the callback page can show the actual backend failure reason
- Microsoft mailbox OAuth validation now treats the mailbox protocol scope plus the refresh token as the real success signal, rather than requiring `offline_access` to appear in the echoed scope string
- if a Microsoft source previously failed with `has no refresh token`, that stale error is now hidden automatically once a newer encrypted refresh token has been stored for the same source
- UI-managed Microsoft source email accounts now also read the encrypted refresh token stored for their email account ID, so a successful browser OAuth exchange is enough for the runtime even when the `user_email_account` row does not contain a duplicated token copy
- when secure token storage is not configured, a successful Microsoft exchange for an env-managed source still requires copying the returned `MAIL_ACCOUNT_<n>__OAUTH_REFRESH_TOKEN` value into `.env` and restarting before polling can use it
- a confirmation dialog if the user tries to leave before exchanging the code
- once the user confirms that leave action, the page suppresses the browser's second generic unsaved-changes prompt
- a cancelable 5-second auto-return countdown after a successful in-browser exchange
- when secure token storage is enabled, the callback pages keep the flow fully in-browser and do not ask the user to copy an env assignment
- guidance that leaving early means the code or token must be handled manually later

API-facing error surfaces in the admin UI now include one-click clipboard actions so users can copy diagnostic payloads without manually selecting text.

Buttons that trigger backend calls now show inline loading spinners so the user can see when authentication, saves, source email account actions, polling, refresh, or OAuth start requests are in progress.

Authenticated notifications beneath the setup guide are now dismissable, can focus the related section when action is needed, and automatically close after 10 seconds when they are low-priority success messages.
Those notifications now render in a floating top-right stack so they remain visible even when the related section is outside the current viewport, but only the newest viewport-safe subset stays floated at once while the rest remain accessible through Notifications Center.
Their recent-history state is now also persisted per account through the backend UI-preferences API, so refreshes and normal sign-out/sign-in cycles do not silently discard important notices.

The `...` menus in both the source-email-account list and the user-management list now measure the real floating panel, stay attached to the trigger button while scrolling, flip above it when there is not enough room below, and close automatically when the trigger scrolls out of view.

The Gmail account area now has two modes:

- regular users see only connection status and a connect/reconnect Gmail OAuth action

## Remote control surface

The `/remote` route is intentionally separate from the full admin workspace.

It reuses the same InboxBridge account identity, but it does not reuse the broad admin-ui session cookie.
Instead it signs in through `/api/remote/auth/...`, stores a remote-only session cookie plus CSRF cookie, and talks only to `/api/remote/...`.

That remote surface is deliberately optimized for:

- small screens
- quick poll-now actions
- installability as a PWA
- reduced exposed functionality on public endpoints
- collapsed-by-default source cards that keep each per-source poll button visible even before the user expands the extra details
- an explicit signed-out notice on the login card whenever a revoked or expired remote session sends the page back to sign-in
- admins can still open the advanced Gmail account override form when they need to manage redirect URIs, shared-client overrides, or other expert settings, but that is an exception path rather than the default model

When that admin-only form is shown, it explains that `Gmail API User` is the Gmail API user id, which is normally `me`, and that the actual Gmail mailbox is the Google account that completed OAuth consent.

Passkeys use the browser WebAuthn APIs from the React app while the backend owns ceremony generation, verification, and persistence.
The helper layer normalizes the backend's wrapped WebAuthn response shape of `{"publicKey": {...}}` before calling `navigator.credentials.create()` or `navigator.credentials.get()`.

The admin UI translations are implemented as a lightweight in-repo dictionary under `src/lib/i18n.js` so new locales can be added without introducing another frontend dependency.
The security panel now requires the current password before `Remove Password` becomes available, and the Quick Setup Guide auto-collapses once all tracked setup steps are complete.
All modal dialogs now support `Escape` to close; form dialogs confirm before closing if the user has already typed changes. The admin user detail view now shows the configured Gmail API user value, which is usually `me` for Gmail API based accounts rather than the literal mailbox address.
The password-visibility eye toggle now keeps a stable position on hover/focus instead of jumping vertically with the shared button hover animation.
The source-email-account add/edit dialog now compares the current form against its initial snapshot, so closing it without making any edits no longer triggers the unsaved-changes confirmation.
The Quick Setup Guide now says `Add at least one email account`, links to the source-email-account section, and only shows the provider OAuth step when at least one configured source account actually uses OAuth.
The Quick Setup Guide step numbering is now assigned dynamically so hidden conditional steps cannot leave gaps such as `1, 2, 3, 5`.
The `Administration` workspace now keeps its own admin-specific quick setup guide focused on shared Google OAuth, creating an additional user in multi-user mode, and confirming the first successful import.
The `Administration -> OAuth Apps` area now explains what the shared Google and Microsoft app registrations are for, including the operator steps for creating the Google Cloud OAuth client used for Gmail destinations and the Microsoft Entra app registration used for Outlook destinations, always reusing the exact redirect URI shown in the InboxBridge dialog.
The `Administration -> OAuth Apps` provider cards are now stacked vertically and expand individually like the admin user cards, while the edit dialogs keep `Client ID`, `Client Secret`, and `Redirect URI` on separate full-width rows and render the redirect URI as its own copyable block inside the provider setup instructions.
User management now also supports switching between single-user and multi-user mode with confirmation, preserving disabled accounts for later reactivation, and deleting any other user account from the admin UI.
