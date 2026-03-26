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
│   └── layout
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
- password changes are exposed from the hero/header controls instead of being buried inside Gmail setup
- the same hero/header security area now also handles passkey enrollment and removal
- the login screen supports passkey sign-in for users who have already enrolled one
- the login screen intentionally avoids exposing live bootstrap-account state to unauthenticated visitors; bootstrap credentials are documented in the operator docs instead
- self-registration is launched from a dedicated `Register for access` button and uses a modal dialog instead of always rendering the full form
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
- admins can override scheduled polling enablement, poll interval, and fetch window without changing `.env`
- the mail-fetcher area is now presented as `My Email Fetchers`, with add/edit happening in a wider modal dialog instead of an always-visible form
- the fetcher dialog includes provider presets, auth-aware field visibility, inline help tooltips, and duplicate-ID validation before save
- env-managed fetchers appear in the same list with a read-only `.env` badge only for the account named `admin`; other users never see those env-backed entries
- the Gmail destination panel distinguishes deployment-shared Google OAuth client credentials from user-specific overrides, but regular users now only see Gmail connection status plus connect/reconnect OAuth while admins keep the advanced override form
- in the normal operating model, that shared Google OAuth client comes from one deployment-wide Google Cloud project reused across many users
- the polling area is now framed as `Poller Settings` and focuses on runtime scheduler controls plus polling-health metrics
- the hero/header now includes a persisted language selector for English, French, German, Portuguese (Portugal), Portuguese (Brazil), and Spanish
- collapsible panes use compact `+` / `-` window-style controls instead of text-heavy expand/collapse buttons
- collapse buttons expose native hover hints and stay pinned to the top-right corner of the card
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
- reusable loading-button behavior
- authentication screen interactions
- reusable password-field show/hide behavior
- passkey browser helpers and passkey panel interactions
- Gmail destination guidance and shared-client behavior
- first-run setup guide rendering
- admin security-management controls
- email fetcher dialog presets and auth-aware field visibility
- reusable bridge card actions
- language-aware setup guide generation and formatting helpers

The Google and Microsoft OAuth callback pages include a direct return path back to the admin UI after in-browser token exchange.
They also include:

- a one-click code copy button
- an automatic in-browser exchange attempt as soon as the callback page loads
- the Google callback page also parses the browser query string directly as a fallback, so the page can still recover the OAuth code if it was not rendered into the initial HTML
- both callback pages now show explicit retry guidance when the provider returns `access_denied` or the token exchange reveals missing required scopes
- a confirmation dialog if the user tries to leave before exchanging the code
- a 10-second auto-return countdown after a successful in-browser exchange
- guidance that leaving early means the code or token must be handled manually later

API-facing error surfaces in the admin UI now include one-click clipboard actions so users can copy diagnostic payloads without manually selecting text.

Buttons that trigger backend calls now show inline loading spinners so the user can see when authentication, saves, bridge actions, polling, refresh, or OAuth start requests are in progress.

Authenticated notifications beneath the setup guide are now dismissable, can focus the related section when action is needed, and automatically close after 10 seconds when they are low-priority success messages.

The Gmail destination area now has two modes:

- regular users see only connection status and a connect/reconnect Gmail OAuth action
- admins can still open the advanced Gmail destination override form when they need to manage redirect URIs, shared-client overrides, or other expert settings, but that is an exception path rather than the default model

When that admin-only form is shown, it explains that `Destination User` is the Gmail API user id, which is normally `me`, and that the actual destination mailbox is the Google account that completed OAuth consent.

Passkeys use the browser WebAuthn APIs from the React app while the backend owns ceremony generation, verification, and persistence.
The helper layer normalizes the backend's wrapped WebAuthn response shape of `{"publicKey": {...}}` before calling `navigator.credentials.create()` or `navigator.credentials.get()`.

The admin UI translations are implemented as a lightweight in-repo dictionary under `src/lib/i18n.js` so new locales can be added without introducing another frontend dependency.
