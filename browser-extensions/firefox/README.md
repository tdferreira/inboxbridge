# InboxBridge Firefox Extension

This folder contains the Firefox manual-install extension target.

## Build the unpacked Firefox extension

```bash
cd browser-extensions/firefox
npm run build:firefox
```

The unpacked extension is written to:

```text
browser-extensions/firefox/dist/firefox
```

## Package an `.xpi` for sharing or signing preparation

```bash
cd browser-extensions/firefox
npm run zip:firefox
```

The packaged artifact is written to:

```text
browser-extensions/firefox/dist/packages
```

## Manual install in Firefox

1. Open `about:debugging`
2. Open `This Firefox`
3. Click `Load Temporary Add-on`
4. Select `browser-extensions/firefox/dist/firefox/manifest.json`

## Setup

1. Open the InboxBridge web app in a normal browser tab.
2. Open the extension `Settings`.
3. Click `Detect URL from active tabs` to look through the current browser window for an already open InboxBridge tab and copy its public URL into the field. If several InboxBridge tabs match, the most recently used one wins. Firefox will ask for one-time `tabs` permission only when you use this convenience action.
4. Sign in from the extension options page. Password-only accounts still complete there directly, while passkey-backed accounts automatically open the normal InboxBridge web sign-in in a browser window and then exchange that browser session for extension tokens. Once connected, the primary action becomes `Sign out` and the credential fields collapse into a compact `Logged in as …` summary until you disconnect.
5. Pick the extension theme: `Use InboxBridge preference`, `System`, `Light Green`, `Light Blue`, `Dark Green`, or `Dark Blue`.
6. Optionally enable browser notifications for grouped error alerts and/or successful manual polls started from the extension. The permission is declared in the extension manifest so Firefox can deliver those notifications reliably, while the toggles still control whether InboxBridge actually sends them.
7. You can also right-click the InboxBridge toolbar icon to open Settings directly or start a manual polling run without opening the popup.
7. Test the connection.

Like the Chromium target, the Firefox extension keeps its local auth bundle
encrypted, uses short-lived access tokens plus rotating refresh tokens, and
saves the canonical public URL returned by InboxBridge after sign-in.
Origin access is requested per InboxBridge origin instead of being granted up
front for every site.

The Firefox target currently supports the same MVP feature set as Chromium:

- loading a compact per-user status snapshot
- showing the last poll summary
- surfacing sources that need attention
- triggering the user-scoped manual poll
- showing live toolbar state while polling is running, with a sync-style icon overlay and attention badges for errors
- optionally showing grouped browser notifications for source errors and extension-started manual poll completions
- exposing browser-action context menu shortcuts for Settings and Run poll now
- auto-detecting the active InboxBridge tab URL for easier first-time setup
- direct extension sign-in without manually copying bearer tokens
- encrypted local extension auth storage with automatic access-token refresh

## Tests

- `npm test` covers the Firefox-specific runtime wiring plus the shared options/background logic reused across browser targets.
- Shared behavior is intentionally tested without duplicating the exact same assertions per browser, so target-specific tests here should stay focused on Firefox-only integration points.
