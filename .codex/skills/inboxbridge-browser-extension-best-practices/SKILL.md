---
name: inboxbridge-browser-extension-best-practices
description: Use when designing, reviewing, or extending InboxBridge browser extensions for Chromium, Firefox, or Safari. Captures official browser-extension best practices around permissions, storage, auth, packaging, and testing.
---

# InboxBridge Browser Extension Best Practices

Use this skill whenever work touches anything under `browser-extensions/`, the backend extension auth/API surface, or extension packaging/signing docs.

Start with:

- [`browser-extensions/README.md`](../../../browser-extensions/README.md)
- [`browser-extensions/shared/README.md`](../../../browser-extensions/shared/README.md)
- [`CONTEXT.md`](../../../CONTEXT.md)

For backend extension auth/session work, also load [`../inboxbridge-backend-change/SKILL.md`](../inboxbridge-backend-change/SKILL.md).

## Official references

Prefer these primary sources when making extension design calls:

- Chrome `activeTab` and permission guidance:
  [developer.chrome.com/docs/extensions/develop/concepts/activeTab](https://developer.chrome.com/docs/extensions/develop/concepts/activeTab)
- Chrome privacy and least-privilege guidance:
  [developer.chrome.com/docs/extensions/develop/security-privacy/user-privacy](https://developer.chrome.com/docs/extensions/develop/security-privacy/user-privacy)
- Chrome Manifest V3 security posture, including no remotely hosted code:
  [developer.chrome.com/docs/extensions/develop/migrate/what-is-mv3](https://developer.chrome.com/docs/extensions/develop/migrate/what-is-mv3)
- MDN optional permissions / optional host permissions:
  [developer.mozilla.org/en-US/Add-ons/WebExtensions/manifest.json/optional_permissions](https://developer.mozilla.org/en-US/Add-ons/WebExtensions/manifest.json/optional_permissions)
- MDN extension testing guidance:
  [developer.mozilla.org/en-US/curriculum/extensions/testing/](https://developer.mozilla.org/en-US/curriculum/extensions/testing/)
- Apple Safari extension platform overview:
  [developer.apple.com/safari/extensions/](https://developer.apple.com/safari/extensions/)

## Hard rules

- Keep permissions least-privilege. If a capability is only needed for an explicit user action, prefer optional permissions or runtime grants over install-time permissions.
- Keep host access least-privilege. Request the InboxBridge origin at runtime instead of shipping blanket host access in required permissions.
- Do not introduce remote-code execution paths. No remotely hosted executable code, no `eval`, and no weakened CSP.
- Treat encrypted local storage for access tokens, refresh tokens, and other sensitive extension state as mandatory.
- Preserve rotating refresh-token behavior and short-lived access tokens for extension auth.
- Keep extension auth scoped to the extension API only. Do not reuse the main browser session cookie as the extension's primary auth mechanism.
- Prefer a shared cross-browser core under `browser-extensions/shared/`, with only thin target adapters in browser-specific folders.
- Do not weaken secure-storage guarantees with plaintext fallback paths just because a browser target is inconvenient.
- For extension-authenticated live updates, prefer authenticated `fetch`-based SSE consumption over plain `EventSource` whenever the stream requires `Authorization` headers; do not move extension bearer tokens into query strings just to satisfy `EventSource`.

## Permission guidance

- Use required permissions only for features the extension cannot function without.
- For current-tab discovery or similar convenience actions, prefer optional `tabs` access and request it only when the user clicks the relevant action.
- Prefer optional host permissions for InboxBridge origins and request them per configured origin.
- Keep manifest permission prompts understandable from the user's point of view. If a permission looks broader than the feature needs, tighten it.
- If a browser-specific limitation forces a broader permission, document why in the target README and add a regression test that locks the rationale in.

## Storage and auth guidance

- Encrypt sensitive values before writing them to extension storage.
- Prefer Web Crypto primitives with non-extractable keys where the platform allows it.
- Keep server URL and other non-sensitive routing metadata separate from encrypted auth blobs when that improves resilience.
- On refresh failure, clear invalid auth state without silently keeping broken tokens around.
- Be honest about the security boundary: extension-side encryption protects against casual storage inspection and reduces accidental disclosure, but it does not make secrets unreadable to malicious code already running inside the extension context.

## Cross-browser packaging guidance

- Chromium and Firefox should share as much runtime code and test coverage as practical.
- Safari work should continue from the shared WebExtension codebase, but packaging and distribution remain Apple-specific through Safari/Xcode/App Store tooling.
- Keep browser-specific files minimal: manifest differences, wrapper scripts, packaging docs, and platform adapters only.
- Document the manual-install path and the eventual signing/package path separately so operator docs stay accurate during phased rollout.

## Testing expectations

- Shared controller, storage, auth, and permission logic should have unit coverage.
- Each implemented browser target should have its own manifest and adapter-level tests, even when it reuses shared modules.
- Add regression tests for permission posture, token refresh, encrypted-storage behavior, and sign-in/passkey flows.
- Prefer browser-run integration coverage when practical for popup/options/background wiring and permission prompts.
- Packaging smoke tests should prove that Chromium bundles, Firefox bundles, and their distributable archives still build.
- Safari packaging should eventually include at least a packaging/export smoke path once the target exists.

## Review checklist

Before finishing extension work, verify:

- Are required permissions truly required, or should they be optional/runtime-requested?
- Is InboxBridge host access requested per origin rather than granted globally up front?
- Are sensitive values encrypted at rest in extension-managed storage?
- Are access tokens short-lived and refresh tokens rotated?
- Does the implementation avoid remote executable code and other CSP regressions?
- Do Chromium and Firefox both have meaningful tests for the affected behavior?
- If Safari is touched, is the Apple-specific packaging/distribution path documented accurately?
- Are extension docs and `CONTEXT.md` updated when the runtime model or support matrix changes?
