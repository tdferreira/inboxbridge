---
name: inboxbridge-backend-change
description: Use when changing the Quarkus backend, Flyway schema, mailbox import logic, auth/session services, or REST endpoints in InboxBridge. Focuses on backend architecture, transactional boundaries, security invariants, and backend validation.
---

# InboxBridge Backend Change

Start with [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md), [`CONTEXT.md`](../../../CONTEXT.md), and the affected packages under `src/main/java`.

## Backend invariants

- Keep Quarkus as the system of record for auth, OAuth, sessions, secret handling, and polling state.
- Preserve the hard boundary that one user's source mail must never import into another user's destination mailbox.
- Preserve encrypted-at-rest handling for UI-managed passwords, refresh tokens, and related secrets.
- Avoid fallback paths that weaken secure storage or browser OAuth exchange requirements.
- When async polling workers touch repositories, keep transactional boundaries explicit. Virtual-thread workers do not inherit the original request context.
- Respect the existing package split: `config`, `domain`, `dto`, `persistence`, `service`, `web`.

## When to add stronger coverage

- Polling, mailbox import, dedupe, or destination routing changes: add or update backend tests, and prefer GreenMail-backed integration coverage for real mailbox I/O paths.
- Auth, passkeys, sessions, CSRF, or OAuth changes: cover failure cases and security regressions, not only happy paths.
- Schema or persistence changes: add the Flyway migration plus the tests or code paths that prove the new shape is used.

## Validation

- Backend-focused pass: `mvn -q test`
- Prefer a focused Maven test command first when the changed area is narrow.
- If the task touched runnable services, finish with `docker compose up --build -d` unless the user said not to.

## Docs to update when relevant

- Architecture or runtime model: [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md)
- Product/runtime memory: [`CONTEXT.md`](../../../CONTEXT.md)
- Operator-facing behavior/setup: [`README.md`](../../../README.md), [`docs/SETUP.md`](../../../docs/SETUP.md), [`docs/OAUTH_SETUP.md`](../../../docs/OAUTH_SETUP.md)
