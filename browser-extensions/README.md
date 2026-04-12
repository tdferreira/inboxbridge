# Browser Extensions

This workspace groups the browser-extension targets for InboxBridge.

## Layout

```text
browser-extensions/
  chromium/
  firefox/
  safari/
  shared/
```

- `shared/` contains cross-browser modules that should stay aligned across every target, such as the extension API client, encrypted config/auth storage, popup view derivation, badge state, and status cache helpers.
  Those shared modules now also own least-privilege permission handling so tab access and InboxBridge host access stay optional and user-driven.
  They now also own the authenticated live-poll subscription path so the background worker can react immediately when polling starts, pauses, resumes, or finishes.
  Shared behavior should be tested once at this layer and reused by every browser target, while each browser folder keeps only its own runtime-adapter and packaging coverage.
- The browser-extension visual identity should stay aligned with the `/remote` PWA, using the same `remote-icon.svg` artwork and derived raster icons for browser manifests.
- Popup refresh actions should always surface visible feedback, and the popup/settings surfaces should default to the signed-in InboxBridge theme while still allowing local overrides to `System`, `Light Green`, `Light Blue`, `Dark Green`, or `Dark Blue`.
- Extension language and theme defaults should now follow the signed-in InboxBridge user preferences, while the extension Settings page can still override either preference locally for that browser profile.
- The Settings page can also optionally enable browser notifications for grouped source-error alerts and for successful manual polls started from the extension; notifications remain behaviorally opt-in even though the browser permission is declared in the extension manifest for reliable cross-browser delivery.
- Right-clicking the browser toolbar icon should expose direct actions for opening Settings and starting a manual polling run without opening the popup first.
- The popup should show an explicit signed-out state when no extension session is stored, with a direct `Sign in` call-to-action that opens Settings, while the Settings page owns the actual username/password flow and triggers the browser-window handoff for passkey-backed sign-in.
- During the browser-window handoff, the finishing sign-in page should show a visible five-second close countdown when it was opened as a popup, and the Settings form should mark missing required URL/username/password fields inline instead of failing silently.
- The Settings action formerly labeled `Use current tab` is now `Detect URL from active tabs`: it scans the current browser window for an already open InboxBridge tab and copies that origin into the URL field, preferring the most recently used matching tab when several are open.
- Once the extension is signed in, the Settings page switches its primary action to `Sign out`, replaces the credential form with a compact `Logged in as …` summary, and restores the form only after the browser session is disconnected again.
- A successful background status refresh should stay authoritative for the badge; popup-broadcast or live-SSE follow-up failures must not replace a good status with a generic `?` badge.
- The toolbar presentation now uses both the browser badge and dynamic icon overlays: polling adds a blue live indicator, source or transport errors add a red alert marker, and signed-out state adds a gray prompt marker while preserving the base InboxBridge icon underneath.
- `chromium/` contains the current implemented Manifest V3 target for Chrome, Edge, and other Chromium-based browsers, including its build/package scripts and target-specific documentation.
- `firefox/` is reserved for the Firefox target and its packaging/signing preparation.
- `safari/` is reserved for Safari packaging and Apple-specific wrapper/export work once the Chromium and Firefox targets are stable.

## Current status

- Chromium: implemented manual-install target with direct extension sign-in, rotating refresh tokens, and encrypted local auth storage
- Firefox: implemented manual-install target with the same shared auth/storage flow and matching optional-permission setup
- Safari: folder reserved, implementation pending
- GitHub releases should include packaged Chromium `.zip` and Firefox `.xpi` artifacts in addition to the main backend/admin-ui release archive.
- The GitHub release workflow can now also submit extension updates to Chrome Web Store, Microsoft Edge Add-ons, and Firefox AMO when the corresponding store credentials have been configured as repository secrets. See [docs/BROWSER_EXTENSION_STORES.md](/Users/tdferreira/Developer/inboxbridge/docs/BROWSER_EXTENSION_STORES.md).

For the current manual-install MVP, start with [chromium/README.md](/Users/tdferreira/Developer/inboxbridge/browser-extensions/chromium/README.md).
