---
name: inboxbridge-admin-ui-change
description: Use when changing the React admin UI, the /remote surface, translated copy, layout behavior, or frontend controller/component structure in InboxBridge. Focuses on frontend conventions, translation expectations, and frontend validation.
---

# InboxBridge Admin UI Change

Start with [`admin-ui/README.md`](../../../admin-ui/README.md), [`CONTEXT.md`](../../../CONTEXT.md), and the affected files under `admin-ui/src`.

## Frontend conventions

- Preserve the split between top-level composition in `App.jsx`, controller hooks in `src/lib`, and presentational components in `src/components`.
- Keep visible copy routed through the translation dictionary. Avoid introducing raw English strings into authenticated UI or `/remote`.
- When changing localization or route behavior, keep the localized workspace-route model intact: `/` remains the user workspace, explicit admin routes localize per current language, and language changes should rewrite explicit admin slugs immediately rather than waiting on deferred UI-preferences hydration.
- Preserve the lightweight `/remote` surface as a focused, mobile-first control plane rather than a copy of the full dashboard.
- Respect the existing workspace/layout model: collapsible sections, section cards, persisted preferences, and quick-setup behavior.
- Keep auth/session browser hardening intact, including the same-origin CSRF wrapper behavior for unsafe `/api/...` writes.
- Treat `PillboxInput` as the shared multi-value picker for folder-like inputs. Keep its desktop floating-combobox behavior, real-touch native-picker behavior, keyboard navigation, and mobile touch-target sizing coherent across callers instead of re-implementing picker logic inside feature dialogs.
- Preserve the current source-detail diagnostics surface in `EmailAccountListItem`: operators can now inspect destination identity, POP/IMAP checkpoints, throttle state, IMAP IDLE watcher health, persisted poll audit fields, and explicit runtime alerts from the normal UI.
- Match existing styling and UX patterns unless the user explicitly asks for a broader redesign.

## Testing expectations

- Add or update Vitest coverage for behavior changes, especially component rendering, translated copy, controller flows, and regression-prone interactive states.
- When copy or localization changes, verify the affected keys exist across supported locales.
- When changing localized routing, workspace switching, or language selection behavior, update the `App.test.jsx` / `workspaceRoutes.test.js` regression coverage that protects localized path rewriting.
- When changing `PillboxInput`, cover both real-touch/native-picker expectations and desktop/mobile-emulation fallback behavior so desktop dev tools emulation does not accidentally regress into touch-only flows.

## Validation

- `cd admin-ui && npm run test:run`
- `cd admin-ui && npm run build`
- If the task also touches the public GitHub Pages site under `site/`, pair the admin-ui validation with `node site/test-config-generator.mjs` because that lightweight check also guards the public locale catalog and site-specific UX rules.
- If the task touched runnable services, finish with `docker compose up --build -d` unless the user said not to.

## Docs to update when relevant

- Frontend behavior or UX guidance: [`admin-ui/README.md`](../../../admin-ui/README.md)
- Cross-chat product/runtime memory: [`CONTEXT.md`](../../../CONTEXT.md)
- Operator-facing setup or screenshots/copy-sensitive docs: [`README.md`](../../../README.md)
