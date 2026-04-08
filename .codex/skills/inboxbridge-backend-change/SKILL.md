---
name: inboxbridge-backend-change
description: Use when changing the Quarkus backend, Flyway schema, mailbox import logic, auth/session services, or REST endpoints in InboxBridge. Focuses on backend architecture, transactional boundaries, security invariants, and backend validation.
---

# InboxBridge Backend Change

Start with [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md), [`CONTEXT.md`](../../../CONTEXT.md), and the affected packages under `src/main/java`.

If the task is specifically about backend architecture quality, Quarkus / Java
best practices, folder structure, or package-boundary reviews, also load
[`../inboxbridge-quarkus-best-practices/SKILL.md`](../inboxbridge-quarkus-best-practices/SKILL.md).

## Backend invariants

- Keep Quarkus as the system of record for auth, OAuth, sessions, secret handling, and polling state.
- For provider OAuth callbacks, keep the backend as the secure callback and
  state-validation authority, but prefer frontend-owned callback UI over
  embedded Java HTML when the user-facing flow needs richer copy, translation,
  or visual consistency.
- Preserve the hard boundary that one user's source mail must never import into another user's destination mailbox.
- Preserve the newer destination-aware runtime model: checkpoint reuse, dedupe visibility, and source diagnostics all key off the resolved destination mailbox identity rather than the source alone.
- Preserve encrypted-at-rest handling for UI-managed passwords, refresh tokens, and related secrets.
- Avoid fallback paths that weaken secure storage or browser OAuth exchange requirements.
- When async polling workers touch repositories, keep transactional boundaries explicit. Virtual-thread workers do not inherit the original request context.
- Remember that authenticated SSE endpoints also pass through database-backed auth/session filters, so blocking execution and transactional boundaries matter for `/api/poll/events`, `/api/admin/poll/events`, and related live-poll endpoints.
- Keep the current source-diagnostics plumbing coherent when changing polling state or summaries: admin and user source views now surface destination identity, per-folder checkpoints, throttle state, IMAP IDLE watcher health, and recent poll audit details through dedicated DTOs/services.
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
