# Shared Browser-Extension Modules

This folder holds cross-browser extension code that should remain browser-agnostic.

Current shared modules include:

- API calls to the InboxBridge extension endpoints
- config normalization plus encrypted auth-storage helpers
- least-privilege permission helpers for optional host and `tabs` grants
- badge-state derivation
- browser-session handoff helpers reused by the browser-extension passkey login flow
- popup, options, and background controllers
- popup-view formatting logic
- cached-status persistence helpers
- authenticated SSE parsing plus live-status overlay helpers for extension poll updates
- toolbar icon-state derivation and dynamic icon overlay rendering
- browser-notification permission handling plus grouped error/manual-poll notification orchestration
- light browser API wrappers that are still compatible with the current targets

When Firefox or Safari need browser-specific behavior, keep the shared contract here stable and move only the target-specific adapter logic into the corresponding target folder.

## Testing expectations

- Prefer testing browser-agnostic behavior here once instead of cloning the same assertions in every target folder.
- Keep target folders responsible only for browser-specific runtime adapters, manifest details, packaging scripts, and browser-only UX differences.
