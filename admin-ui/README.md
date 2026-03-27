# Admin UI

The admin UI is a separate React + Vite application served by Nginx in its own container. It talks to the Quarkus backend through proxied REST endpoints under `/api/...`.

## Structure

```text
admin-ui/src
├── App.jsx
├── components
│   ├── account
│   ├── admin
│   ├── auth
│   ├── bridges
│   ├── common
│   ├── gmail
│   ├── layout
│   └── polling
├── lib
└── test
```

Key design choices:

- `App.jsx` owns data fetching, session orchestration, and submit handlers.
- Reusable presentational components live under `src/components/...`.
- `src/components/layout/SetupGuidePanel.jsx` gives users a first-run checklist inside the app itself.
- the setup guide entries are clickable links that focus the corresponding working section
- the setup guide uses neutral / green / red state styling to reflect pending, complete, and error conditions
- the setup guide auto-collapses once every tracked step is complete
- users can opt into per-account persisted collapse state for the major admin-ui sections
- expanding any major section now forces a fresh data reload for that section and shows an inline loading indicator while the refresh is running
- password changes are exposed from the hero/header controls instead of being buried inside Gmail setup
- the same hero/header security area now also handles passkey enrollment and removal
- passkey registration now opens in a focused modal dialog instead of stretching the security panel inline layout
- the login screen supports passkey sign-in for users who have already enrolled one
- the login screen intentionally avoids exposing live bootstrap-account state to unauthenticated visitors; bootstrap credentials are documented in the operator docs instead
- self-registration is launched from a dedicated `Register for access` button and uses a modal dialog instead of always rendering the full form
- when the deployment sets `BRIDGE_MULTI_USER_ENABLED=false`, the login screen hides self-registration and the post-login UI hides user-management features entirely
- accounts with both a password and a passkey now use password + passkey login, not passkey-only login
- accounts with only a passkey ignore any typed password and fall through into the passkey prompt instead of stopping on an error
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
- the user list now follows the same expandable-entry pattern as mail fetchers, with a `...` contextual menu for suspend, role, password, and passkey actions
- expanded user entries are now split into clear detail subsections such as `User Configuration`, `Gmail Destination`, `Poller Settings`, `Passkeys`, and `Mail Fetchers`
- contextual `...` menus no longer duplicate expand/collapse actions; expansion is handled directly by clicking the row itself
- admins can override scheduled polling enablement, poll interval, and fetch window without changing `.env`
- admins now see an admin-only `Global Poller Settings` section with deployment-wide polling controls, totals, and import-history charts, while the actual global override form lives in a focused modal dialog opened from `Edit Poller Settings`
- each user now gets a dedicated `My Poller Settings` section with statistics and import-history charts scoped only to that account, while the actual override form lives in a focused modal dialog opened from `Edit Poller Settings`
- both the global and user import-history charts now use line charts with preset ranges such as today, yesterday, past week, past month, past trimester, past semester, and past year
- each mail fetcher now also has its own poller-settings dialog and `Run Poll Now` action in the contextual `...` menu
- while a single mail fetcher poll is running, that fetcher’s status pill switches to a spinner-backed running state instead of still showing the previous success/error label
- the running status pill now keeps its spinner visibly aligned next to the label instead of shrinking inside the badge
- the per-fetcher poller dialog title now uses the mail fetcher ID directly so it is always clear which fetcher is being configured
- env-managed mail fetchers load those per-fetcher poller actions through admin-only endpoints, while UI-managed fetchers use user-scoped endpoints
- user-scoped fetcher actions no longer fall back to env-managed sources with the same ID, so the `...` menu always addresses the DB-managed fetcher entry that was clicked
- the fetcher detail view now surfaces poll cooldown/backoff state, next scheduled poll time, and the last recorded provider failure
- OAuth2 mail fetchers now also show whether the provider connection is already established, and the Microsoft action switches between connect/reconnect based on that state
- the mail-fetcher area is now presented as `My Email Fetchers`, with add/edit happening in a wider modal dialog instead of an always-visible form
- the fetcher dialog includes provider presets, auth-aware field visibility, inline help tooltips, and duplicate-ID validation before save
- the fetcher dialog now also includes a `Test Connection` action that verifies the entered IMAP/POP3 settings against the source server before the fetcher is saved and reports protocol, endpoint, TLS, authentication, and mailbox reachability details
- those fetcher test results are rendered below the dialog action buttons, and the modal itself now scrolls safely inside the viewport so long diagnostics never push the buttons off-screen
- fetcher contextual menus auto-close on outside click or `Escape`, and now anchor to the `...` button with viewport-aware placement so they flip above when there is no room below
- compact `...` action buttons now keep a fixed square size, and shared button styling keeps labels on a single line instead of wrapping
- env-managed fetchers appear in the same list with a read-only `.env` badge only for the account named `admin`; other users never see those env-backed entries
- placeholder fallback values do not count as env-managed fetchers, so an empty `.env` source configuration produces no `.env` fetcher entry in the UI
- DB-managed mail fetchers cannot reuse an ID that is already occupied by an env-managed `.env` fetcher
- Outlook / Microsoft source connects now retry once with a freshly refreshed access token if the provider rejects the cached token as invalid before its recorded expiry time
- busy poll responses now include the active trigger/source details so the UI can show why a click could not start a new run yet
- notification copy actions now use compact icon-only buttons with tooltip text, and floating notifications become less transparent on hover so long error text is easier to read
- the floating notification stack also keeps a stronger default opacity and a subtle blur backdrop so messages remain readable over dense UI sections
- floating notifications now wrap long text inside the card instead of overflowing past the viewport edges
- the mail-fetcher list now reloads after manual poll attempts and whenever a fetcher row is expanded so the details panel reflects the latest polling state and last-event status
- expanding an individual mail fetcher or user entry now also triggers a fresh data load for that item, with visible loading feedback while the refresh is in progress
- the Gmail account panel distinguishes deployment-shared Google OAuth client credentials from user-specific overrides, but regular users now only see Gmail connection status plus connect/reconnect OAuth while admins keep the advanced override form
- when a source depends on Gmail import but the current account has unlinked Gmail, polling now reports that the Gmail account is not linked instead of surfacing a less clear downstream API failure
- if Gmail unlink cannot revoke the Google-side grant automatically, the UI now tells the user how to remove InboxBridge manually from `myaccount.google.com -> Security -> Manage third-party access -> InboxBridge -> Delete All Connections`
- when that admin-only setup sidebar is absent, the Gmail account panel now expands to the full available width instead of keeping an empty second column
- the admin Gmail account form now shows inline help hints for each configurable field
- connected Gmail accounts can now also be unlinked from the admin UI, which clears InboxBridge's stored Gmail OAuth tokens and attempts a Google-side token revocation when possible
- in the normal operating model, that shared Google OAuth client comes from one deployment-wide Google Cloud project reused across many users
- the polling area is now framed as `Poller Settings` and focuses on runtime scheduler controls plus polling-health metrics
- the user poller settings card now uses the same padded section shell as the main dashboard cards, so the form content stays fully inside the card boundaries
- the hero/header now includes a `Preferences` button that opens a modal for language selection and the persisted `Remember layout on this account` toggle
- the hero/header `Security` button now opens the password and passkey tools in a dedicated modal dialog, with separate tabs so the modal stays less crowded
- the Security dialog now confirms before closing when the password form has in-progress input
- visible labels route through the in-repo translation dictionary instead of mixing translated and raw JSX text
- translated subsection headings and the most prominent detail labels inside the user-management panes now follow the selected language too instead of staying stuck in English
- quick-setup steps, Gmail account fields, poller-setting forms, and mail-fetcher dialogs now route their visible labels and help text through the same translation dictionary too
- translation regression coverage now includes component-level localized rendering tests for the major UI surfaces plus a catalog test that verifies critical keys exist for every supported locale
- password-policy checklists and passkey cancellation/failure messages now also come from the locale dictionary instead of leaking raw English browser copy into translated sessions
- collapsible panes use compact `+` / `-` window-style controls instead of text-heavy expand/collapse buttons
- collapse buttons expose native hover hints and stay pinned to the top-right corner of the card
- shared button styles now include clearer hover, focus, and pressed states so actions feel more obviously interactive across the UI
- Each component imports its own CSS file for local structure and appearance.
- Shared visual tokens and generic form/layout helpers live in `src/styles.css`.
- Formatting and API helpers live in `src/lib/...`.

## Tests

Frontend unit tests use `Vitest` and `@testing-library/react`.

Run them with:

```bash
cd admin-ui
npm test -- --run
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
- per-fetcher poller settings controls and contextual poll-now actions
- email fetcher dialog presets and auth-aware field visibility
- reusable bridge card actions
- language-aware setup guide generation and formatting helpers

The Google and Microsoft OAuth callback pages include a direct return path back to the admin UI after in-browser token exchange.
They also include:

- a one-click code copy button
- an automatic in-browser exchange attempt as soon as the callback page loads
- the Google and Microsoft callback pages also parse the browser query string directly as a fallback, so the page can still recover the OAuth code/state if they were not rendered into the initial HTML
- both callback pages now show explicit retry guidance when the provider returns `access_denied` or the token exchange reveals missing required scopes
- the Microsoft callback exchange now also returns structured JSON errors, so the callback page can show the actual backend failure reason
- Microsoft mailbox OAuth validation now treats the mailbox protocol scope plus the refresh token as the real success signal, rather than requiring `offline_access` to appear in the echoed scope string
- if a Microsoft source previously failed with `has no refresh token`, that stale error is now hidden automatically once a newer encrypted refresh token has been stored for the same source
- UI-managed Microsoft mail fetchers now also read the encrypted refresh token stored for their `bridgeId`, so a successful browser OAuth exchange is enough for the fetcher runtime even when the `user_bridge` row does not contain a duplicated token copy
- when secure token storage is not configured, a successful Microsoft exchange for an env-managed source still requires copying the returned `BRIDGE_SOURCES_<n>__OAUTH_REFRESH_TOKEN` value into `.env` and restarting before polling can use it
- a confirmation dialog if the user tries to leave before exchanging the code
- once the user confirms that leave action, the page suppresses the browser's second generic unsaved-changes prompt
- a 10-second auto-return countdown after a successful in-browser exchange
- guidance that leaving early means the code or token must be handled manually later

API-facing error surfaces in the admin UI now include one-click clipboard actions so users can copy diagnostic payloads without manually selecting text.

Buttons that trigger backend calls now show inline loading spinners so the user can see when authentication, saves, bridge actions, polling, refresh, or OAuth start requests are in progress.

Authenticated notifications beneath the setup guide are now dismissable, can focus the related section when action is needed, and automatically close after 10 seconds when they are low-priority success messages.
Those notifications now render in a floating top-right stack so they remain visible even when the related section is outside the current viewport.

The `...` menus in both the mail-fetcher list and the user-management list now measure the real floating panel, stay attached to the trigger button while scrolling, flip above it when there is not enough room below, and close automatically when the trigger scrolls out of view.

The Gmail account area now has two modes:

- regular users see only connection status and a connect/reconnect Gmail OAuth action
- admins can still open the advanced Gmail account override form when they need to manage redirect URIs, shared-client overrides, or other expert settings, but that is an exception path rather than the default model

When that admin-only form is shown, it explains that `Gmail API User` is the Gmail API user id, which is normally `me`, and that the actual Gmail mailbox is the Google account that completed OAuth consent.

Passkeys use the browser WebAuthn APIs from the React app while the backend owns ceremony generation, verification, and persistence.
The helper layer normalizes the backend's wrapped WebAuthn response shape of `{"publicKey": {...}}` before calling `navigator.credentials.create()` or `navigator.credentials.get()`.

The admin UI translations are implemented as a lightweight in-repo dictionary under `src/lib/i18n.js` so new locales can be added without introducing another frontend dependency.
The security panel now requires the current password before `Remove Password` becomes available, and the Quick Setup Guide auto-collapses once all tracked setup steps are complete.
All modal dialogs now support `Escape` to close; form dialogs confirm before closing if the user has already typed changes. The admin user detail view now shows the configured Gmail API user value, which is usually `me` for Gmail API based accounts rather than the literal mailbox address.
The password-visibility eye toggle now keeps a stable position on hover/focus instead of jumping vertically with the shared button hover animation.
The fetcher add/edit dialog now compares the current form against its initial snapshot, so closing it without making any edits no longer triggers the unsaved-changes confirmation.
The Quick Setup Guide now says `Add at least one email account`, links to the source-email-account section, and only shows the provider OAuth step when at least one configured source account actually uses OAuth.
The Quick Setup Guide step numbering is now assigned dynamically so hidden conditional steps cannot leave gaps such as `1, 2, 3, 5`.
