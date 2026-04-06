---
name: inboxbridge-worktree-guardrails
description: Use when working anywhere in the InboxBridge repository or when a request is broad and spans backend, frontend, docs, validation, or release workflow. Applies the repository's standing rules before making changes.
---

# InboxBridge Worktree Guardrails

Read [`AGENTS.md`](../../../AGENTS.md) first, then load only the repo docs needed for the task.

## Always follow

- Do not read `.env` unless the user explicitly asks.
- Treat [`CONTEXT.md`](../../../CONTEXT.md) as the cross-chat source of truth.
- Update docs whenever behavior, UI copy, setup flow, or architecture changes.
- Add or update tests for every behavior change.
- Prefer minimal changes that preserve the current architecture and style.
- Treat encrypted storage for UI-managed secrets as mandatory. Do not add weaker fallback behavior.
- Preserve the GitHub Pages/public-site rules captured in `CONTEXT.md` when touching `site/`: full locale coverage for `en`, `fr`, `de`, `pt-PT`, `pt-BR`, and `es`; a compact flag language picker with direct-open fallback behavior; the centered lower-level runtime diagram plus security-highlights block; and the guidance that `.env` should stay minimal while normal mailbox setup belongs in the web UI.
- Preserve localized workspace-route behavior in the admin UI: explicit admin routes such as `/admin` should canonicalize immediately to the current locale slug (for example `/administracao`) instead of waiting on delayed preference hydration.
- Preserve the current operator-diagnostics model: expanded source rows in the admin UI now expose destination identity, POP/IMAP checkpoint state, throttle buckets, IMAP IDLE watcher health, and recent poll-decision audit data. Do not silently remove or narrow that visibility without replacing it with an equally usable troubleshooting surface.
- Preserve the reusable `PillboxInput` contract: it is a shared component, not dialog-local UI. Real touch devices may use the native multi-select path when options are locked, but desktop browser mobile emulation should continue using the floating combobox rather than being treated like a real touch device.
- End with the stack ready for manual testing whenever feasible.
- If a runnable service was touched, finish with a fresh `docker compose` deployment of the affected services unless the user asked not to or the environment blocks it.
- Always provide a semantic commit message with a detailed body that covers all current uncommitted changes in the repo, not only the changes from the current chat.

## Doc routing

- Product and current behavior: [`README.md`](../../../README.md), [`CONTEXT.md`](../../../CONTEXT.md)
- Architecture and runtime boundaries: [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md)
- Local bootstrap and validation path: [`docs/SETUP.md`](../../../docs/SETUP.md)
- OAuth and TLS specifics: [`docs/OAUTH_SETUP.md`](../../../docs/OAUTH_SETUP.md), [`docs/TRUST_LOCAL_CA.md`](../../../docs/TRUST_LOCAL_CA.md)
- Frontend structure and UX rules: [`admin-ui/README.md`](../../../admin-ui/README.md)

## Finish checklist

1. Run the relevant automated checks for the changed area.
2. Refresh docs and `CONTEXT.md` if the repo knowledge changed.
3. If services changed, bring the stack back up with Docker Compose for manual verification.
4. Summarize what changed, what was validated, any gaps, and the ready-to-use commit message.
