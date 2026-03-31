# InboxBridge

InboxBridge is a self-hosted mail importer that polls external IMAP / POP3 mailboxes and imports their messages into a destination mailbox.

Today that destination can be:

- Gmail through the Gmail API
- Outlook.com / Hotmail / Live through IMAP APPEND with Microsoft OAuth2
- known IMAP providers with preset server defaults
- a generic IMAP mailbox through manual IMAP APPEND settings

The project now runs as two cooperating applications:

- a Quarkus backend
- a separate React admin UI

By default, Docker Compose starts both over HTTPS with locally generated self-signed certificates.

InboxBridge supports both operator-managed system configuration from `.env` and per-user configuration stored securely in PostgreSQL through the admin UI.

If a Gmail account unlink removes InboxBridge's local tokens but Google-side revocation fails, the user can remove the remaining grant manually from `myaccount.google.com`:

- open `Security`
- open `Manage third-party access` under `Your connections to third-party apps & services`
- select `InboxBridge`
- choose `Delete All Connections`

Google help: https://support.google.com/accounts/answer/13533235

## What it already does

1. Polls env-managed system email accounts and DB-managed user email accounts.
2. Imports messages into Gmail with `users.messages.import` or appends into IMAP destination mailboxes.
3. Deduplicates imports in PostgreSQL.
4. Stores OAuth tokens encrypted in PostgreSQL when secure storage is enabled.
5. Stores user-managed destination-mailbox and source-email-account secrets encrypted in PostgreSQL.
6. Provides a separate React admin UI with login, self-registration, approval workflow, user management, destination mailbox config, and source email account config.
7. Supports Google OAuth for Gmail destinations and Microsoft OAuth for Outlook source and destination accounts.
8. Organizes the admin UI into reusable React components with component-scoped styles and frontend unit tests.
9. Supports WebAuthn passkeys for browser sign-in after a user enrolls one from the security panel.
10. Supports per-user poller overrides plus automatic per-source cooldown/backoff when providers start rejecting or throttling requests.
11. Can run either in single-user mode or multi-user mode, controlled by `.env`.
12. Exposes a lightweight remote control surface at `/remote` so users can trigger polling from phones or other devices without opening the full admin workspace.
13. Lets both the main app and `/remote` store an optional browser-reported device location for the current session, separate from Geo-IP, with a guessed place label and maps link in the Sessions view when coordinates are available.

## What it still does not do

- encrypt env-managed mailbox passwords from `.env`
- support non-Google / non-Microsoft provider OAuth flows
- provide IMAP IDLE or durable mailbox cursors
- provide production-grade metrics or circuit breakers
- integrate with an external secret vault or KMS

## Stack

- Java 25
- Quarkus 3.33.1 (LTS)
- React 19 + Vite 7
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

- `SECURITY_TOKEN_ENCRYPTION_KEY=<base64 32-byte key>`
- `PUBLIC_BASE_URL=https://localhost:3000`
- `SECURITY_PASSKEY_RP_ID=localhost`
- `SECURITY_PASSKEY_ORIGINS=https://localhost:3000`
- `JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge`
- `JDBC_USERNAME=inboxbridge`
- `JDBC_PASSWORD=inboxbridge`

With only that configuration, you can:

- open `https://localhost:3000`
- sign in with `admin` / `nimda`
- change the bootstrap password
- register passkeys from the `Security` panel if the browser supports them
- register passkeys through a focused modal dialog so the security panel stays compact
- create users
- configure the destination mailbox and source email accounts from the admin UI
- test source email account connectivity from the add/edit dialog before saving IMAP/POP3 settings, including protocol, TLS, authentication, and mailbox reachability details
- source-email-account test diagnostics now render below the dialog action row, and long modal content scrolls inside the viewport instead of hiding the buttons off-screen
- access password changes from the top header via `Change Password`

You do not need to prefill `GOOGLE_*` or `MICROSOFT_*` just to bring the stack up.
But Gmail API support requires a Google Cloud app registration, and Microsoft OAuth source-mail support requires a Microsoft Entra app registration.

## Minimum to import mail

To actually import mail, you need the bootstrap config above plus:

- a destination mailbox configured either as Gmail through shared `GOOGLE_*` env vars / per-user linking, or as an IMAP APPEND destination in the admin UI
- provider OAuth app credentials for any OAuth-based source or destination flow
- at least one source email account, either from `.env` or from the admin UI
- destination mailbox setup now uses an Add/Edit modal workflow: Gmail must always use `Save and Authenticate`, while Outlook can save folder-only changes without reconnecting and requires Microsoft OAuth again for mailbox-identity changes
- InboxBridge now blocks any source mailbox that points at the same mailbox as `My Destination Mailbox`; if the destination is changed to match an existing source mailbox, that source is disabled automatically until the conflict is removed
- browser-based Google and Microsoft OAuth exchange now requires `SECURITY_TOKEN_ENCRYPTION_KEY`; if secure storage is missing, the callback page stops and tells the user to configure the key and retry instead of offering a manual fallback

The fastest operator-managed path is:

```bash
cp .env.example .env
```

Important values when you want a shared, env-managed deployment setup:

- `PUBLIC_BASE_URL` for browser callback defaults and UI links
- `MULTI_USER_ENABLED` to choose single-user vs multi-user operation
- `SECURITY_TOKEN_ENCRYPTION_KEY`
- `SECURITY_TOKEN_ENCRYPTION_KEY_ID`
- `MAIL_ACCOUNT_<index>__...` for env-managed system source email accounts
- `GOOGLE_*` only if you want a deployment-level shared Gmail account configuration
- `MICROSOFT_*` for Microsoft OAuth sources
- `SECURITY_PASSKEY_*` if you need to override the default local WebAuthn relying-party settings
- `SECURITY_REMOTE_ENABLED=true` to keep the remote control surface available
- `SECURITY_REMOTE_SESSION_TTL=PT12H` to control how long remote sessions remain valid
- `SECURITY_REMOTE_POLL_RATE_LIMIT_COUNT=60` and `SECURITY_REMOTE_POLL_RATE_LIMIT_WINDOW=PT1M` to harden the remote trigger surface against hammering
- `SECURITY_REMOTE_SERVICE_TOKEN` and `SECURITY_REMOTE_SERVICE_USERNAME` if you want a bearer-token automation path that acts as a dedicated InboxBridge account

For the clearest end-to-end bootstrap instructions, see `docs/SETUP.md`.

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
- remote control page: `https://localhost:3000/remote`
- backend HTTP: `http://localhost:8080`
- backend HTTPS: `https://localhost:8443`
- PostgreSQL: `localhost:5432`

The first compose run generates local certificates into `./certs`. You can later replace those files with your own certificates.
For custom certificates, replace:

- `./certs/backend.crt` and `./certs/backend.key` for the Quarkus backend
- `./certs/frontend.crt` and `./certs/frontend.key` for the admin UI HTTPS endpoint

For Let's Encrypt, the usual mapping is:

- `fullchain.pem` -> `backend.crt` / `frontend.crt`
- `privkey.pem` -> `backend.key` / `frontend.key`

If you use your own certificate authority, also replace `./certs/ca.crt` so the frontend proxy still trusts the backend certificate.

After the stack is up:

1. Open `https://localhost:3000`
2. Sign in with the bootstrap admin
3. Change the bootstrap password
4. Follow the `Quick Setup Guide` panel in the admin UI
5. Connect your destination mailbox
6. Add at least one source email account
7. Run a poll and verify the dashboard counters

The `Quick Setup Guide` cards in the admin UI are clickable and jump to the section where each action is performed. They also reflect live state:

- green when the step is complete
- red when InboxBridge has a recorded error associated with that step
- neutral when the step has not been completed yet
- the provider OAuth step only appears when at least one configured source email account actually uses OAuth
- the guide auto-collapses when all tracked steps are complete
- users can opt into persisting collapsed/expanded section state across sign-ins from the `Preferences` dialog
- users can enable `layout editing` from `Preferences`, which reveals drag-and-drop and move controls for the movable workspace sections
- users can now also reset the section layout back to the default arrangement from `Preferences`
- the main page sections in each workspace can now be rearranged and that custom order can be remembered per account
- expanding any major section now refreshes its data immediately and shows a loading indicator while the refresh runs
- the step numbering is assigned dynamically, so conditional steps never leave numbering gaps
- the `Administration` workspace now uses its own admin-focused quick setup guide instead of repeating the user-area checklist

## Remote control

InboxBridge now includes a small mobile-friendly remote control page at `/remote`.

It is designed for public exposure behind your normal HTTPS endpoint and works well behind Cloudflare Zero Trust as an outer gate.

Security model:

- the remote page does not reuse the full admin-ui browser session
- it uses the same InboxBridge identity and passkey/password flows, but mints a separate remote-scoped session cookie
- remote session writes require a CSRF header that matches a remote-only CSRF cookie
- remote actions are rate-limited independently of the normal admin UI
- the existing polling hardening still applies underneath, including manual trigger limits, host/provider throttling, and busy-run protection
- optional bearer service-token access is available for automation when `SECURITY_REMOTE_SERVICE_TOKEN` and `SECURITY_REMOTE_SERVICE_USERNAME` are configured

Remote capabilities:

- sign in with password or passkey
- poll all sources visible to the signed-in user
- poll all users when the signed-in user is an admin
- poll one specific source from the remote source list
- install the `/remote` page as a PWA shortcut on supported devices
- when a remote session is revoked or expires, the page returns to the remote login screen with an explicit signed-out notice instead of silently dropping back to sign-in
- when the signed-in account is missing a personal destination mailbox or has no personal source email accounts yet, the remote page pauses there and points the user back to `My InboxBridge` to finish setup first
- source cards on `/remote` are collapsed by default so polling actions stay compact on phones and tablets, while each source still keeps its individual poll button visible without expanding the card
- the main `My InboxBridge` workspace now includes a dedicated `Remote control` card that explains the lightweight page and links directly to `/remote`
- both the main app and `/remote` now expose an explicit opt-in browser location prompt so the current session can record a device-reported location beside the server-side Geo-IP result
- when a session has browser-reported coordinates, the Sessions tab now tries to infer a human-readable place label from those coordinates and offers an `Open in Maps` link that can hand off to the device's preferred maps app/browser
- local PWA installation still depends on the browser trusting your HTTPS certificate; an untrusted self-signed certificate can suppress installability even when the manifest and service worker are present

## Admin UI login

Initial bootstrap credentials:

- username: `admin`
- password: `nimda`

The bootstrap admin is marked `mustChangePassword=true`, so change it immediately after first login.
The running unauthenticated login screen does not expose whether those bootstrap credentials are still active, so setup operators should rely on this documentation rather than a public status endpoint.
For convenience, the login form only prefills `admin` / `nimda` while the untouched bootstrap admin is still in its original first-login state. As soon as that admin changes the password, enrolls a passkey, is removed, or otherwise leaves the initial bootstrap state, the login form stops prefilling those credentials.
After that, the user can enroll one or more passkeys from the `Security` panel and use `Sign in with passkey` on later visits.
If `MULTI_USER_ENABLED=false`, the login screen hides self-registration entirely and the admin UI does not expose user-management features.
Single-user mode still keeps the rest of the control plane visible for the bootstrap admin, including destination mailbox setup, source email accounts, poller settings, and dashboard views.
Current login rules:

- password only: the normal `Sign in` flow uses only the password
- passkey only: the normal `Sign in` flow ignores any typed password and starts passkey authentication
- password + passkey: the normal `Sign in` flow validates the password first and then requires the passkey as a second factor
- the dedicated `Sign in with passkey` button is mainly for passkey-only accounts or discoverable-credential sign-in
- users can intentionally remove their password and stay passkey-only
- self-registration is opened from a dedicated `Register for access` button and uses a focused modal instead of permanently rendering the request form on the login card
- repeated failed sign-ins from the same client IP are rate-limited with an exponential lockout: after `SECURITY_AUTH_LOGIN_FAILURE_THRESHOLD` failures the address is blocked for `SECURITY_AUTH_LOGIN_INITIAL_BLOCK`, and each later block doubles until `SECURITY_AUTH_LOGIN_MAX_BLOCK`
- when self-registration is enabled, the registration modal also requires a short anti-robot challenge before the request is accepted
- admins can override those login and self-registration protection defaults live from `Administration -> Authentication Security` without editing `.env`

The admin UI also supports these languages, with the user preference stored per account and reused across sessions:

- English
- French
- German
- Portuguese (Portugal)
- Portuguese (Brazil)
- Spanish

User preferences now live behind the `Preferences` button in the header, which opens a modal for language selection, the `Remember layout on this account` option, a `Show Quick Setup Guide` toggle, and `layout editing` controls.
For admin users, the workspace selection is also reflected in the browser URL: `/` stays on the `My InboxBridge` workspace without rewriting, `/admin` opens the administration workspace directly, and translated admin slugs such as `/administracao` are still supported. Older explicit user-workspace slugs are normalized back to `/`.
The admin UI enforces HTTPS: nginx already redirects plain HTTP traffic on port `80`, and the frontend also upgrades itself to `https://...` if it is ever served over plain HTTP by another deployment path.
The `Security` tools also open in a dedicated dialog instead of occupying permanent space in the main page layout, with separate tabs for `Password`, `Passkeys`, and `Sessions`.
The `Sessions` tab shows the latest successful sign-ins, the currently active sessions for the account, the login method used by each session, and actions for signing out one other session or all other sessions at once.
It now includes both full admin-ui browser sessions and `/remote` remote-control sessions in the same list, with the session type clearly labeled.
If the same account signs in somewhere else while this browser is already open, the normal background refresh now raises a warning notification that links directly to the `Sessions` tab.
If one browser session is revoked from another, the revoked browser now detects the next authenticated `401` response and immediately returns to the login screen instead of staying in a broken authenticated state.
Approximate session location can now be enabled with a Geo-IP provider chain, but InboxBridge still resolves it only on new sign-ins, caches by IP aggressively, and falls back to the next configured provider only when the primary is down or rate-limited. The default chain is `IPWHOIS -> IPAPI_CO -> IP_API`, with optional `IPINFO_LITE` after that when a token is configured.
Users can also opt in to sharing the browser/device location for the current session. InboxBridge now auto-captures that location on sign-in only when the browser already reports geolocation permission as granted; on mobile and other gesture-gated browsers, the explicit `Share Device Location` action remains the reliable path, and the `Sessions` tab still offers a retry action if no sample was saved yet. The device-reported location is stored separately from Geo-IP and shown alongside it in the shared `Sessions` tab so both signals can be compared rather than one silently replacing the other. When coordinates are present, the UI also tries to reverse-geocode them into a friendlier place label and provides an `Open in Maps` link.

The `Sessions` tab now also shows a best-effort browser label and device type for each recent or active session, derived from the recorded `User-Agent`. This helps distinguish mobile Safari vs desktop Edge, remote-control sign-ins vs normal browser sessions, and similar cases when reviewing account activity.
The admin `Authentication Security` editor now exposes that chain as a primary-provider dropdown plus a tag-style fallback input, and it shows readiness cards with provider docs, terms, and any provider-specific credentials that must be configured before a provider can be enabled.
The `Quick Setup Guide` can now be hidden once every step is complete, and it automatically comes back if one of those requirements later becomes invalid again.

## Admin UI capabilities

The React admin UI lives in `admin-ui/` and runs in its own container/server.

Current features:

- secure sign-in using HTTP-only same-site cookies
- optional passkey sign-in using WebAuthn
- self-registration followed by admin approval
- self-registration opens through a dedicated unauthenticated modal flow instead of occupying the main login screen full time
- admin-managed user creation
- single-user deployments can disable all self-registration and user-management surfaces with `MULTI_USER_ENABLED=false`
- switching to single-user mode from the admin UI now deactivates every account except the acting admin, and switching back to multi-user mode reactivates the accounts that were disabled by that mode switch
- multiple admin users, with admin rights managed from the UI
- admins can reset another user’s password to a temporary value and wipe that user’s passkeys
- admins can now also deactivate or permanently delete any other user account from the UI
- admin password reset now opens a dedicated dialog instead of an always-visible inline form
- the admin reset-password dialog shows the temporary-password rules inline so the operator can see when the new password satisfies the policy before submitting
- admin actions that can suspend a user, force a password change, wipe passkeys, or remove stored source-email-account data now require an explicit confirmation modal before execution
- admins cannot remove their own admin rights
- admin-managed per-user Gmail account overrides when advanced customization is really needed
- non-admin users get a simplified Gmail account status panel with connect/reconnect OAuth, while the `Administration -> OAuth Apps` area is limited to configuring the shared Google Cloud OAuth client registration reused by those user mailbox consent flows
- when the Gmail account area has no admin-only setup sidebar to show, it now expands to the full available width instead of reserving an empty right column
- the admin Gmail account form now shows inline `(i)` help hints for every configurable field so operators can understand what each setting does without leaving the page
- connected Gmail accounts can also be unlinked from the admin UI, which clears InboxBridge's stored Gmail OAuth tokens and attempts to revoke the Google grant automatically when possible
- the Security dialog confirms before closing when the password form contains in-progress input
- when dialogs are stacked, the `Escape` key now closes only the front-most dialog first
- per-user source email account create/update/delete through a dedicated modal dialog
- per-account polling controls from the source email account contextual menu, including `Run Poll Now` and source-specific poller overrides
- env-managed source email accounts route those per-account poller actions through admin-only backend endpoints, while UI-managed source email accounts use user-scoped endpoints
- user-scoped source-email-account actions no longer fall back to env-managed sources with the same ID, so a contextual action always targets the DB-managed account the user clicked
- expanded user-management entries are split into clearer subsections for user configuration, Gmail account, poller settings, passkeys, and source email accounts
- expandable source-email-account and user rows now use the row itself for expand/collapse, while the `...` menu is reserved for actions only
- the bundled admin-ui locales now cover quick-setup content, Gmail account fields, poller-setting forms, and source-email-account labels more completely instead of leaving those section bodies in English
- common provider presets for Outlook, Gmail, Yahoo Mail, and Proton Mail Bridge when creating a source email account
- the source email account modal now uses `Add Email Account` / `Edit Source Email Account ...` headings, disables `Test Connection` until the required fields are present, hides the plain `Add` action for new Outlook accounts, and locks the provider preset while editing an existing account
- after a successful IMAP source connection test, that dialog now also loads the remote folder list so the user can choose a detected mailbox folder or switch back to manual folder entry; editing an existing IMAP source reloads the folder list automatically too
- auth-aware source-email-account forms that hide password-only or OAuth-only fields when they are not relevant
- inline help tooltips for source-email-account and poller fields so each control explains what it does
- env-managed source email accounts shown in the same operational list with a read-only `.env` badge, but only for the account named `admin`
- placeholder fallback values from `application.yaml` are now filtered out, so if no `MAIL_ACCOUNT_*` values are configured then no env-managed source email account appears in the UI or runtime
- DB-managed source email account IDs are also rejected if they collide with an env-managed `.env` account ID
- per-user source-email-account passwords and OAuth refresh tokens are stored encrypted in PostgreSQL by default when saved from the admin UI
- the add/edit source email account dialog is wider, rejects duplicate IDs before submit, and only shows the `.env` badge for environment-managed entries
- each source email account can now override its own polling enabled state, interval, and fetch window on top of the inherited user/global defaults
- polling now fails early with a clear message when a source depends on a Gmail account that is no longer linked, instead of surfacing a vaguer downstream Gmail API failure
- successful polls now add deterministic jitter to the next scheduled run, and each app instance spaces out repeated source-host access plus destination-provider deliveries so large fleets are less likely to hammer one IMAP host or the Gmail API in a burst
- IMAP message reads now retry once after a `FolderClosedException` by reopening the folder and reacquiring the message before the fetch batch is failed
- Microsoft IMAP/POP connects now invalidate the cached access token and refresh once automatically if Outlook rejects the current token as invalid before the normal expiry time
- when a manual run collides with an active scheduler/manual run, the busy error now includes the current trigger, source when relevant, and start time
- copyable error/details affordances in the admin UI now use compact icon buttons with tooltip text instead of wide visible copy labels, and floating notifications become more opaque on hover for easier reading
- floating notifications now keep a stronger default opacity plus a light blur backdrop so underlying UI does not compete with long error text
- source-email-account detail refresh now reloads automatically after manual poll attempts and when an account row is expanded so the latest status is reflected without a manual page refresh
- expanded user-management rows now also refresh their backing data when opened, and both section-level and row-level refreshes show visible loading feedback
- IMAP message metadata needed for dedupe/import identity is now captured before reading raw MIME so a later folder invalidation does not fail an otherwise readable message
- password changes are available from the top header security panel and enforce confirmation, minimum length, uppercase, lowercase, number, special character, and “must differ from current password” rules
- users can remove their password entirely and operate in passkey-only mode
- passkeys can be registered and removed from that same top-header security panel
- passkey registration now opens in a dedicated modal dialog instead of expanding the security panel inline
- the last passkey on a passwordless account cannot be removed until a password is set again or another passkey is added
- self-service password removal and passkey deletion now require an explicit confirmation modal before the backend call is made
- admin-managed runtime overrides for polling enablement, poll interval, and fetch window while still showing the `.env` defaults
- an admin-only `Global Poller Settings` section for deployment-wide polling controls, with a separate `Global Statistics` section for the all-users analytics
- the admin-facing `Global Poller Settings` area now shows effective settings in-page and opens a dedicated modal dialog for editing the deployment-wide overrides
- the admin-facing `Global Poller Settings` area now also exposes the manual-run rate limit plus the host/provider hardening controls, asks for confirmation before triggering an all-users run, and lets admins tune the throttling / jitter behavior without editing `.env`
- every hardening field in that admin modal now includes an inline `(i)` help hint, and the dialog also includes a longer explanation block describing how host spacing, concurrency caps, adaptive throttle growth, lease expiry, and success jitter work together
- a dedicated `My Poller Settings` section so each user can override polling enablement, interval, and fetch window for their own UI-managed source email accounts, with a separate `My Statistics` section that only shows analytics for the current account
- the user-facing `My Poller Settings` area now also includes `Run Poll Now` for that signed-in user’s own mail accounts, while single-account `Run Poll Now` remains the explicit force-run path when the user wants to bypass cooldown/backoff for one source
- the user-facing `My Poller Settings` area now shows the effective settings as a compact summary and opens a dedicated modal dialog for editing overrides
- mail-account statistics now use source-specific cards instead of deployment-wide account-health counters, and include error-poll totals plus manual-vs-scheduled poll activity for that one account
- the polling charts also support a `Custom` date-time range modal, with a required `from` value and an optional `to` value that defaults to the current time
- imported-message history is retained with timestamps in PostgreSQL and is now surfaced as selectable line charts with preset ranges such as today, yesterday, past week, past month, past trimester, past semester, and past year in both the global and per-user statistics views
- polling statistics now also include provider breakdowns, current source-account health buckets, manual-vs-scheduled run counts, duplicate trends, error trends, and average poll duration
- admin users now switch between a `User` workspace and an `Administration` workspace so their own Gmail / source-email-account setup is separated from deployment-wide controls
- inside each workspace, the movable content sections can now be rearranged independently while the header and workspace switcher stay fixed
- the statistics charts now use `Recharts 3.x`, including shared hover tooltips that show all visible series for the active time bucket
- the admin UI dependency stack is kept on current stable major versions, including React 19, Vite 7, Vitest 3, and Recharts 3.x
- shared action-menu triggers now use one common hamburger icon style across both user rows and source-email-account rows, so the menu affordance stays visually consistent
- expanded source email account cards in the user workspace now show account-scoped statistics, while expanded user cards in the admin workspace show statistics scoped to that selected user only
- nested statistics cards inside expanded source email accounts and expanded admin user rows can now be collapsed independently, and they start collapsed when there is no meaningful data to show yet
- reconnecting the same Gmail account no longer revokes the existing Google grant; InboxBridge only replaces and revokes the previous grant when the newly linked Gmail account is actually different
- the admin UI now includes a dedicated responsive pass for phones and narrow tablets, stacking header actions, section summaries, dialogs, and metric grids more safely on small screens
- if a user manually revokes InboxBridge from their Google account, InboxBridge now treats a confirmed repeated Gmail `401` as revoked consent, clears the saved Gmail link for that user, and asks them to reconnect from `My Destination Mailbox`
- reconnecting a Gmail account now warns that the currently linked Gmail account will be replaced; a successful Google OAuth exchange also reports when the previous linked account was automatically replaced and its old Google grant was revoked
- contextual menu triggers in the mail-account and user lists now use a compact hamburger menu icon instead of a literal `...` label
- manual poll trigger for admins
- per-source cooldown/backoff state that pauses only the affected source email account after repeated auth, quota, or transient provider failures
- floating viewport-level notifications so action feedback stays visible while scrolling long pages
- cooldown visibility in the UI, including next poll time, cooldown-until, failure count, and the last failure reason for each source email account
- Google OAuth launch for the shared Gmail account and for the current user
- Microsoft OAuth launch for visible Microsoft email accounts
- import totals and latest poll outcome per email account
- reusable component-based frontend sections with local CSS files
- frontend unit tests for key auth, Gmail, email-account card, and utility behavior
- frontend translation coverage tests for the major admin-ui surfaces plus a critical-key locale catalog check
- viewport-aware contextual menus in both source email accounts and user management that stay attached while scrolling, plus translated password-policy / passkey-error copy in the admin UI
- compact `...` action buttons now keep a fixed square size, and shared button labels stay on a single line to avoid accidental height expansion
- one-click copy actions for API error banners and email-account error payloads
- dismissable notifications that can focus the related section, with non-critical notices auto-closing after 10 seconds
- per-user admin-ui language selection persisted in PostgreSQL and mirrored to the browser for future visits

Security model:

- admin APIs require `ADMIN`
- user config APIs require an authenticated session and are scoped to the current user
- admins can inspect other users’ configuration summaries without seeing raw client secrets or refresh tokens
- users cannot access other users’ email-account or Gmail configuration through the authenticated user APIs
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
- `GET /api/microsoft-oauth/start?sourceId=<email-account-id>`
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
5. The callback page starts a 5-second countdown and redirects to InboxBridge automatically after a successful in-browser exchange unless you cancel that automatic redirect
6. You can still use the callback page exchange button to retry manually if the automatic attempt fails
7. You can still use the callback page return button to navigate back immediately
8. If secure storage is enabled, InboxBridge stores the token encrypted in PostgreSQL automatically

OAuth callback usability notes:

- the callback page includes a `Copy Code` button
- if the browser blocks clipboard access on the callback page, InboxBridge now opens a manual copy dialog with the code instead of presenting that clipboard failure as the main OAuth result
- the callback page automatically attempts the code exchange when it loads
- after a successful in-browser exchange, the callback page shows a cancelable 5-second auto-return countdown so the user can stay on the page and inspect the exchange details if needed
- the Google and Microsoft callback pages now also re-read the browser query string directly, so they can recover if the reverse proxy or callback rendering path did not populate the code/state into the initial HTML
- both Google and Microsoft callback pages now detect consent denial and tell the user to retry the OAuth flow while approving every requested permission
- the Microsoft callback exchange endpoint now returns a structured JSON error body, so the callback page shows the real exchange failure reason instead of an empty generic error
- Microsoft OAuth validation now treats the mailbox protocol scope plus the returned refresh token as the real success signal, so it does not fail merely because Microsoft omitted `offline_access` from the echoed scope string
- when a Microsoft OAuth exchange stores a newer encrypted refresh token successfully, the admin dashboard suppresses any older stale `has no refresh token` error for that same source instead of continuing to present it as the current state
- UI-managed Microsoft source email accounts now also reuse that encrypted credential store by email account ID, so a successful browser OAuth exchange can immediately satisfy the runtime even when the `user_email_account` row itself does not yet hold a duplicated refresh token copy
- browser OAuth exchange now fails hard when secure token storage is missing, so `SECURITY_TOKEN_ENCRYPTION_KEY` must be configured before exchanging Google or Microsoft authorization codes from the callback pages
- when Microsoft destination access is unlinked or replaced, InboxBridge removes its stored tokens but Microsoft may still keep the app consent until the user removes `InboxBridge` manually from their Microsoft account permissions or My Apps page
- the callback page includes a `Return to InboxBridge` button
- returning to InboxBridge before exchange asks for confirmation
- once you confirm that leave action, the page suppresses the browser's second generic `beforeunload` prompt so you are not asked twice
- after a successful in-browser exchange, the callback page shows a 5-second auto-return countdown that can be canceled from the page itself
- if you leave without exchanging, you must add the code or resulting token manually later

Admin UI loading feedback notes:

- buttons that trigger backend calls now show an inline loading spinner while the request is in progress
- this includes sign-in, registration, password changes, destination mailbox saves, source email account saves/deletes, user management actions, poll runs, refresh, and OAuth start actions

## Passkeys

InboxBridge can use browser passkeys for admin-ui sign-in after a user first signs in with their password and enrolls a passkey from the `Security` panel.

Default local settings:

- `SECURITY_PASSKEYS_ENABLED=true`
- `SECURITY_PASSKEY_RP_ID=localhost`
- `SECURITY_PASSKEY_RP_NAME=InboxBridge`
- `SECURITY_PASSKEY_ORIGINS=https://localhost:3000`
- `SECURITY_PASSKEY_CHALLENGE_TTL=PT5M`

These values are loaded through the same `inboxbridge.security.passkeys` config tree as the rest of the backend settings, so invalid mapping changes will fail startup immediately.

For a deployed hostname, set these to your public origin:

- `PUBLIC_BASE_URL=https://your-domain.example`
- `SECURITY_PASSKEY_RP_ID=your-domain.example`
- `SECURITY_PASSKEY_ORIGINS=https://your-domain.example`

Notes:

- passkeys require HTTPS or localhost
- passkeys do not work from raw IP hosts such as `https://192.168.50.6`; use `localhost` for local-only access or a real hostname/domain and set `SECURITY_PASSKEY_*` accordingly
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
- the source-host minimum spacing
- the source-host max concurrency
- the destination-provider minimum spacing
- the destination-provider max concurrency
- the throttle lease TTL
- the adaptive throttle max multiplier
- the successful-poll jitter ratio
- the successful-poll jitter cap
- source-specific polling enabled state, interval, and fetch window for each source email account

Behavior:

- `.env` remains the default source of truth on startup
- the admin UI stores only overrides in PostgreSQL
- clearing an override falls back to the `.env` default again
- the scheduler checks the effective interval dynamically, so changes apply without editing `.env`
- source-level overrides take precedence over both per-user and global polling settings
- each source email account also exposes a `Run Poll Now` action in its contextual menu

Accepted poll interval formats:

- shorthand: `30s`, `5m`, `1h`, `1d`
- ISO-8601: `PT30S`, `PT5M`, `PT1H`

Current limits:

- minimum interval: `5s`
- fetch window range: `1` to `500`
- default source-host minimum spacing on one app instance: `PT1S`
- default source-host max concurrency on one app instance: `2`
- default destination-provider minimum spacing on one app instance: `PT0.25S`
- default destination-provider max concurrency on one app instance: `1`
- default throttle lease TTL for persisted host/provider permits: `PT2M`
- default adaptive throttle max multiplier: `6x`
- default successful-poll jitter: `20%` of the effective interval, capped at `PT30S`
- the admin polling modal now exposes those hardening values directly, with inline `(i)` help on every field and a longer explanation about how spacing, concurrency caps, adaptive widening, lease expiry, and success jitter interact

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

Shared Google account configuration:

- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`: deployment-shared Google Cloud OAuth client for the Gmail API. In the normal deployment model, one Google Cloud project/client is enough for many users. Each user still needs their own Gmail OAuth consent and refresh token for their own destination mailbox. These values belong to the deployment, not to one specific end user.
- `GOOGLE_REFRESH_TOKEN`: refresh token for the shared Gmail account used by the deployment.
- `GOOGLE_REDIRECT_URI`: optional explicit Google callback override.
- `GMAIL_DESTINATION_USER`: Gmail API target user for the shared/system destination. In most cases this should stay `me`, which tells Gmail to import into the mailbox that granted the token.
- `GMAIL_CREATE_MISSING_LABELS`: create configured Gmail labels automatically if they do not exist yet.
- `GMAIL_NEVER_MARK_SPAM`: asks Gmail import to avoid spam classification where supported.
- `GMAIL_PROCESS_FOR_CALENDAR`: lets Gmail process imported messages for calendar extraction.

Shared Microsoft OAuth app:

- `MICROSOFT_TENANT`: usually `consumers` for Outlook.com / Hotmail / Live accounts.
- `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`: Microsoft Entra app credentials reused across Outlook source and destination email accounts.
- `MICROSOFT_REDIRECT_URI`: optional explicit Microsoft callback override.

Security:

- `SECURITY_TOKEN_ENCRYPTION_KEY`: base64-encoded 32-byte key used to encrypt tokens and user-managed secrets at rest.
- `SECURITY_TOKEN_ENCRYPTION_KEY_ID`: version label stored beside encrypted values.
- `SECURITY_PASSKEYS_ENABLED`: enables or disables WebAuthn passkeys.
- `SECURITY_PASSKEY_RP_ID`, `SECURITY_PASSKEY_RP_NAME`, `SECURITY_PASSKEY_ORIGINS`: passkey relying-party identity settings.
- `SECURITY_PASSKEY_CHALLENGE_TTL`: lifetime for passkey registration/authentication ceremonies.
- `SECURITY_AUTH_LOGIN_FAILURE_THRESHOLD`: failed sign-in attempts allowed from one client IP before lockout starts.
- `SECURITY_AUTH_LOGIN_INITIAL_BLOCK`: initial sign-in lockout duration after the threshold is reached.
- `SECURITY_AUTH_LOGIN_MAX_BLOCK`: maximum sign-in lockout duration after repeated failures.
- `SECURITY_AUTH_REGISTRATION_CHALLENGE_ENABLED`: enables the anti-robot challenge on self-registration.
- `SECURITY_AUTH_REGISTRATION_CHALLENGE_TTL`: lifetime of each anti-robot registration challenge.
- `SECURITY_AUTH_REGISTRATION_CHALLENGE_PROVIDER`: selects the registration CAPTCHA provider (`ALTCHA`, `TURNSTILE`, or `HCAPTCHA`).
- `SECURITY_AUTH_REGISTRATION_ALTCHA_MAX_NUMBER`: proof-of-work difficulty ceiling for the built-in ALTCHA flow.
- `SECURITY_AUTH_REGISTRATION_ALTCHA_HMAC_KEY`: optional stable signing key for ALTCHA challenges.
- `SECURITY_AUTH_REGISTRATION_TURNSTILE_SITE_KEY`, `SECURITY_AUTH_REGISTRATION_TURNSTILE_SECRET`: optional Cloudflare Turnstile credentials.
- `SECURITY_AUTH_REGISTRATION_HCAPTCHA_SITE_KEY`, `SECURITY_AUTH_REGISTRATION_HCAPTCHA_SECRET`: optional hCaptcha credentials.
- `SECURITY_AUTH_GEO_IP_ENABLED`: enables approximate session-location lookup during new sign-ins.
- `SECURITY_AUTH_GEO_IP_PRIMARY_PROVIDER`: the first Geo-IP provider InboxBridge will try.
- `SECURITY_AUTH_GEO_IP_FALLBACK_PROVIDERS`: ordered fallback providers used only when the primary provider is down or rate-limited.
- `SECURITY_AUTH_GEO_IP_CACHE_TTL`: how long successful and not-found Geo-IP results stay cached by IP.
- `SECURITY_AUTH_GEO_IP_PROVIDER_COOLDOWN`: cooldown applied to a provider after retryable failures such as 429 or 5xx responses.
- `SECURITY_AUTH_GEO_IP_REQUEST_TIMEOUT`: per-provider request timeout.
- `SECURITY_AUTH_GEO_IP_IPINFO_TOKEN`: optional token used when `IPINFO_LITE` is part of the provider chain.

Those security-abuse defaults are also available in the admin UI under `Administration -> Authentication Security`, which stores only deployment overrides in PostgreSQL while leaving `.env` as the startup default source of truth.
Raw duration values shown in the polling/authentication-security admin UI now keep hover hints with a human-readable explanation, for example `PT0.25S = 250 milliseconds`.
For registration CAPTCHA, InboxBridge now defaults to `ALTCHA`, which is self-hosted, privacy-friendlier, and works without any external account or token. The admin UI can switch the active provider to `Cloudflare Turnstile` or `hCaptcha` once their required credentials are configured, and it keeps providers disabled until their mandatory settings are present.
Geo-IP session-location lookup is also available in `Administration -> Authentication Security`. The current strategy is:

- one primary provider is tried first
- successful and not-found results are cached by IP in PostgreSQL for the configured TTL
- fallback providers are only tried when the primary provider is down or returns a retryable throttle/server response
- location is resolved only when a new browser session is created, not on every authenticated request

By default, InboxBridge uses `IPwho.is` as the zero-config primary provider, then `ipapi.co`, then `ip-api`, and can optionally use `IPinfo Lite` as a token-backed fallback when an IPinfo token is configured.

Polling defaults:

- `POLL_ENABLED`: default scheduled polling state before any admin override.
- `POLL_INTERVAL`: default polling interval before any admin override.
- `FETCH_WINDOW`: default number of most recent source messages scanned on each poll before any admin override.

Env-managed source email accounts:

- `MAIL_ACCOUNT_<n>__ID`: stable source email account identifier.
- `MAIL_ACCOUNT_<n>__ENABLED`: enables or disables that source.
- `MAIL_ACCOUNT_<n>__PROTOCOL`: `IMAP` or `POP3`.
- `MAIL_ACCOUNT_<n>__HOST`, `MAIL_ACCOUNT_<n>__PORT`: source mailbox server location.
- `MAIL_ACCOUNT_<n>__TLS`: whether to require TLS for the source connection.
- `MAIL_ACCOUNT_<n>__AUTH_METHOD`: `PASSWORD` or `OAUTH2`.
- `MAIL_ACCOUNT_<n>__OAUTH_PROVIDER`: currently `NONE` or `MICROSOFT`.
- `MAIL_ACCOUNT_<n>__USERNAME`: source mailbox username.
- `MAIL_ACCOUNT_<n>__PASSWORD`: source mailbox password or app password for password auth.
- `MAIL_ACCOUNT_<n>__OAUTH_REFRESH_TOKEN`: optional manual refresh token for env-managed OAuth2 sources.
- `MAIL_ACCOUNT_<n>__FOLDER`: IMAP folder to scan.
- `MAIL_ACCOUNT_<n>__UNREAD_ONLY`: whether to import only unread messages.
- `MAIL_ACCOUNT_<n>__CUSTOM_LABEL`: Gmail label to apply after import.

Google OAuth client IDs and client secrets belong to a Google Cloud project, not to a Gmail mailbox. InboxBridge can guide the user through setup and store the provided credentials securely, but it cannot automatically provision a Google OAuth client from the admin UI.

In practice, each deployment must choose one of these patterns:

1. reuse one shared Google OAuth client for many users
2. let each user connect their own Gmail mailbox from `My Destination Mailbox` using that shared client
3. let each user create a Google Cloud OAuth client and paste the values into the UI

Pattern `1` is the intended default for most InboxBridge deployments.

The admin UI now explains those setup steps next to the Gmail account area. For regular users the UI is simplified to Gmail status plus connect/reconnect OAuth, while admins can still access the advanced override form when that is actually needed.

## Secure token storage

Secure storage is enabled by setting:

- `SECURITY_TOKEN_ENCRYPTION_KEY`
- optionally `SECURITY_TOKEN_ENCRYPTION_KEY_ID`

When enabled:

- Google refresh/access tokens are stored encrypted
- Microsoft refresh/access tokens are stored encrypted
- user-managed Gmail client credentials are stored encrypted
- user-managed source-email-account passwords and refresh tokens are stored encrypted by default whenever they are saved from the admin UI

Important nuance:

- secrets are encrypted at the application layer before they reach PostgreSQL
- passwords are hashed, not reversibly encrypted
- non-secret metadata remains queryable in the database so the app can function

For the user Gmail screen specifically:

- `Gmail API User` should usually stay `me`
- `Redirect URI` now defaults to the deployment callback URL and is prefilled in the UI
- the actual Gmail mailbox used for imports is the Google account that completed OAuth consent; `Gmail API User=me` just tells the Gmail API to use that authenticated mailbox
- admins should configure the shared Google `Client ID` / `Client Secret` in `Administration -> OAuth Apps` using the values from the Google Cloud project; per-user `Client ID` / `Client Secret` remain optional advanced overrides and most deployments should not need them
- the Gmail account panel now shows deployment-shared Google client availability separately from user-specific client overrides and refresh-token storage, but non-admin users only see the simplified connection status they actually need

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
3. if you are using Let's Encrypt or another public CA, copy the certificate chain file into `./certs/backend.crt` and `./certs/frontend.crt`, and copy the corresponding private key into `./certs/backend.key` and `./certs/frontend.key`
4. if the backend certificate is signed by a private/internal CA, also replace `./certs/ca.crt` with that CA certificate so the frontend proxy trusts the backend
5. start the stack again

## Important current limitations

- env-managed mailbox passwords are still plaintext in `.env`
- polling still scans the most recent `inboxbridge.fetch-window` messages rather than using durable mailbox cursors
- metrics and audit-friendly structured event logs are still limited

## Tests

The backend test suite now includes a GreenMail-backed mail-flow integration test that verifies InboxBridge can fetch messages from a real IMAP source mailbox, append them into a real IMAP destination mailbox, and suppress duplicate re-imports on the next poll.

Verified on 2026-03-26:

- `mvn test` passes
- admin UI Docker build succeeds, with frontend Vitest intended to run separately unless `RUN_TESTS=true` is passed to the admin UI Docker build
- Docker Compose builds successfully
- the HTTPS admin UI serves correctly in the container
- unauthenticated `GET /api/auth/me` returns `401` through the HTTPS proxy
- bootstrap login `admin` / `nimda` succeeds and returns `mustChangePassword=true`

## More docs

- Microsoft and Gmail provider setup: `docs/OAUTH_SETUP.md`
- architectural summary and code structure: `CONTEXT.md`
- frontend component structure and frontend tests: `admin-ui/README.md`
Password removal now requires the current password as confirmation before an account can switch to passkey-only sign-in.
Form dialogs now support `Escape` to close, and dialogs with unsaved changes ask for confirmation before dismissing the in-progress form.
Floating notifications now wrap long text inside the card instead of overflowing past the viewport edges.
When a single source email account poll is running, that account now shows an explicit running spinner in its own status pill, and OAuth2 source email accounts expose whether their provider connection is already established.
