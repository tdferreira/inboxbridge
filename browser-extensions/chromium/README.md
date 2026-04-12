# InboxBridge Chromium Extension

This folder contains the Chromium-focused manual-install extension MVP.

## Build the unpacked Chromium extension

```bash
cd browser-extensions/chromium
npm run build:chrome
```

The unpacked extension is written to:

```text
browser-extensions/chromium/dist/chrome
```

## Package a zip for sharing

```bash
cd browser-extensions/chromium
npm run zip:chrome
```

The zip package is written to:

```text
browser-extensions/chromium/dist/packages
```

## Manual install in Chromium browsers

1. Open `chrome://extensions`
2. Enable `Developer mode`
3. Click `Load unpacked`
4. Select `browser-extensions/chromium/dist/chrome`

## Setup

1. Open the InboxBridge web app in a normal browser tab.
2. Open the extension `Settings`.
3. Click `Detect URL from active tabs` to look through the current browser window for an already open InboxBridge tab and copy its public URL into the field. If several InboxBridge tabs match, the most recently used one wins. Chromium will ask for one-time `tabs` permission only when you use this convenience action.
4. Sign in from the extension options page. Password-only accounts still complete there directly, while passkey-backed accounts automatically open the normal InboxBridge web sign-in in a browser window and then exchange that browser session for extension tokens. Once connected, the primary action becomes `Sign out` and the credential fields collapse into a compact `Logged in as …` summary until you disconnect.
5. Pick the extension theme: `Use InboxBridge preference`, `System`, `Light Green`, `Light Blue`, `Dark Green`, or `Dark Blue`.
6. Optionally enable browser notifications for grouped error alerts and/or successful manual polls started from the extension. The permission is declared in the extension manifest so Chrome can deliver those notifications reliably, while the toggles still control whether InboxBridge actually sends them.
7. You can also right-click the InboxBridge toolbar icon to open Settings directly or start a manual polling run without opening the popup.
7. Test the connection.

The extension now stores its local auth bundle encrypted and uses a short-lived
access token plus a rotating refresh token. The backend returns the canonical
public URL derived from `PUBLIC_BASE_URL` or `PUBLIC_HOSTNAME` / `PUBLIC_PORT`,
so once sign-in succeeds the saved extension origin snaps to the real public
InboxBridge URL automatically.
Origin access is also requested per InboxBridge origin instead of being granted
up front for every site.

The extension currently supports:

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

In Chrome and other Chromium browsers, the badge is rendered directly on the
extension's toolbar button. If the extension is not pinned, Chrome may hide
that badge inside the extensions menu, so pinning InboxBridge makes the live
polling and error indicators much easier to notice.

Firefox and Safari packaging phases are prepared at the workspace level and will
live under sibling target folders once those browser-specific manifests and
packaging steps are implemented.

## Tests

- `npm test` exercises the Chromium-specific adapter layer plus the shared extension controllers, storage, auth, API, popup, and notification helpers used by every browser target.
- Browser-specific tests in this folder should stay focused on Chromium runtime wiring, because the bulk of the product logic lives under `browser-extensions/shared/`.
