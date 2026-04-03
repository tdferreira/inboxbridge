---
name: inboxbridge-admin-ui-change
description: Use when changing the React admin UI, the /remote surface, translated copy, layout behavior, or frontend controller/component structure in InboxBridge. Focuses on frontend conventions, translation expectations, and frontend validation.
---

# InboxBridge Admin UI Change

Start with [`admin-ui/README.md`](../../../admin-ui/README.md), [`CONTEXT.md`](../../../CONTEXT.md), and the affected files under `admin-ui/src`.

## Frontend conventions

- Preserve the split between top-level composition in `App.jsx`, controller hooks in `src/lib`, and presentational components in `src/components`.
- Keep visible copy routed through the translation dictionary. Avoid introducing raw English strings into authenticated UI or `/remote`.
- Preserve the lightweight `/remote` surface as a focused, mobile-first control plane rather than a copy of the full dashboard.
- Respect the existing workspace/layout model: collapsible sections, section cards, persisted preferences, and quick-setup behavior.
- Keep auth/session browser hardening intact, including the same-origin CSRF wrapper behavior for unsafe `/api/...` writes.
- Match existing styling and UX patterns unless the user explicitly asks for a broader redesign.

## Testing expectations

- Add or update Vitest coverage for behavior changes, especially component rendering, translated copy, controller flows, and regression-prone interactive states.
- When copy or localization changes, verify the affected keys exist across supported locales.

## Validation

- `cd admin-ui && npm run test:run`
- `cd admin-ui && npm run build`
- If the task touched runnable services, finish with `docker compose up --build -d` unless the user said not to.

## Docs to update when relevant

- Frontend behavior or UX guidance: [`admin-ui/README.md`](../../../admin-ui/README.md)
- Cross-chat product/runtime memory: [`CONTEXT.md`](../../../CONTEXT.md)
- Operator-facing setup or screenshots/copy-sensitive docs: [`README.md`](../../../README.md)
