---
name: inboxbridge-mailflow-safety
description: Use when changing polling, IMAP/POP3 handling, destination imports, OAuth mailbox flows, deduplication, or live poll controls in InboxBridge. Focuses on the project's highest-risk mailflow, isolation, and security invariants.
---

# InboxBridge Mailflow Safety

Load [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md) and the relevant mail/polling sections of [`CONTEXT.md`](../../../CONTEXT.md) before editing.

## High-risk invariants

- A source mailbox must stay bound to its own resolved destination mailbox, even during broad multi-user runs.
- Dedupe must remain stable across source-message identity and raw MIME hashing behavior.
- UI-managed secrets and OAuth tokens must remain encrypted at rest. Browser OAuth exchange must fail hard when secure storage is unavailable.
- Env-managed source accounts stay read-only and separate from DB-managed account mutation paths.
- POP3 must not gain IMAP-only source-side actions such as move or flag changes.
- Live polling state is authoritative per source row. Several sources may be `RUNNING` concurrently, and control actions must continue to honor pause/resume/stop/retry/reprioritize semantics.

## Change strategy

- Prefer focused, surgical edits. Mailflow regressions are expensive.
- When changing worker flow, cancellation, or live status, reason about concurrency and cleanup, not only the happy path.
- Keep `/remote` and the main UI behavior aligned where they intentionally share the same live polling model.
- Re-read the affected tests before editing so the existing invariants stay visible while you work.

## Validation

- Prefer targeted backend tests for the touched services first.
- Run `mvn -q test` when the change meaningfully affects polling, mailbox import, auth, or persistence.
- Use Docker Compose for final manual verification when runnable services were touched.

## Useful anchors

- Architecture: [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md)
- Product/runtime memory: [`CONTEXT.md`](../../../CONTEXT.md)
- Operator setup: [`docs/SETUP.md`](../../../docs/SETUP.md), [`docs/OAUTH_SETUP.md`](../../../docs/OAUTH_SETUP.md)
