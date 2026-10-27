---
name: inboxbridge-worktree-guardrails
description: Use when working anywhere in the InboxBridge repository or when a request is broad and spans backend, frontend, docs, validation, or release workflow. Applies the repository's standing rules before making changes.
---

# InboxBridge Worktree Guardrails

Read [`AGENTS.md`](../../../AGENTS.md) first, then load only the repo docs needed for the task.

## Always follow

- Do not read `.env` unless the user explicitly asks.
- Keep operator-specific paths, private hostnames, personal contact details, deployment counts, mailbox incidents and image metadata out of Git. Use relative links/reserved examples; run `test-repository-privacy.py` and `scan-secrets.sh` before commits. A new sanitizing commit does not erase already-published history.
- Treat [`CONTEXT.md`](../../../CONTEXT.md) as the cross-chat source of truth.
- Update docs whenever behavior, UI copy, setup flow, or architecture changes.
- Add or update tests for every behavior change.
- Prefer minimal changes that preserve the current architecture and style.
- Treat encrypted storage for UI-managed secrets as mandatory. Do not add weaker fallback behavior.
- Apply the dependency policy in `AGENTS.md`: latest patched LTS when available for the current distribution, latest compatible stable otherwise, and a security exception for a major applicable flaw in the latest LTS. Audit all ecosystems, build/runtime/test images, renamed artifacts, and the actual software bundled in floating image tags. Keep framework-managed libraries aligned through their BOM, and report upstream image patch gaps instead of claiming that a current tag guarantees current binaries. See `docs/DEPENDENCIES.md` for the latest audit.
- Preserve the GitHub Pages/public-site rules captured in `CONTEXT.md` when touching `site/`: full locale coverage for `en`, `fr`, `de`, `pt-PT`, `pt-BR`, and `es`; a compact flag language picker with direct-open fallback behavior; the centered lower-level runtime diagram plus security-highlights block; and the guidance that `.env` should stay minimal while normal mailbox setup belongs in the web UI.
- Preserve localized workspace-route behavior in the admin UI: explicit admin routes such as `/admin` should canonicalize immediately to the current locale slug (for example `/administracao`) instead of waiting on delayed preference hydration.
- Preserve the current operator-diagnostics model: expanded source rows in the admin UI now expose destination identity, POP/IMAP checkpoint state, throttle buckets, IMAP IDLE watcher health, recent poll-decision audit data, and explicit alert pills for disconnected IDLE watches, cooldown loops, and sustained throttling. Do not silently remove or narrow that visibility without replacing it with an equally usable troubleshooting surface.
- Preserve resilient IMAP IDLE: bounded per-folder renewal, catch-up on reconnect, independent safety polling and the shared per-source execution guard. Watcher health, safety policy and disconnected fallback are separate diagnostics.
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

## Container-only workstation

For this checkout use `scripts/dev` and `dev/README.md` to run builds, tests and
runtime services. Do not install language toolchains on macOS. Keep the local
development stack separate from the release-packaging repository and NAS data.

IDLE runtime controls use existing polling settings endpoints and nullable DB
overrides (V74). Preserve source > global > environment inheritance, the existing
poll interval as safety cadence and after-commit watcher reconciliation.

IDLE acknowledgement policy: successful renewal is health-only, never an import.
Safety and renewal have separate per-source inherited intervals (V75); validate
safety > renewal + response deadline. Catch-up and notification triggers/counters
are distinct; preserve old ambiguous history.
