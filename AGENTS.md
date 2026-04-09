# AGENTS

Standing instructions for future agents working in this repository.

## Workflow rules

- Do not read `.env` unless the user explicitly asks for it.
- Treat [CONTEXT.md](CONTEXT.md) as the source of truth for cross-chat memory in this repository, and keep it up to date when meaningful behavior, architecture, validation expectations, or runtime constraints change.
- Keep documentation current whenever behavior, UI copy, setup flow, architecture, workflow expectations, or runtime constraints change.
- Keep the root [README.md](README.md) focused on presenting the project to the broader open-source community: what InboxBridge is, who it is for, its main capabilities and limits, how to run it, how to configure it, and where to find deeper docs. Avoid turning the root README into a repository-administration, GitHub-settings, or workflow-operations guide unless that information is directly relevant to normal users or contributors.
- Keep the Codex helper skills under [`.codex/skills`](.codex/skills), and any other agent-skill files in their respective folders, up to date with meaningful and relevant repository knowledge whenever those skill instructions would otherwise lag behind the product, architecture, validation path, or workflow requirements.
- Document code properly. New or changed code should include clear, maintainable naming and any concise comments needed to explain non-obvious behavior, invariants, or tricky control flow.
- Add or update tests for every behavior change, aiming for the highest practical regression coverage for the affected area rather than minimal happy-path coverage.
- For any feature or bug fix that impacts communication with an email server, mailbox polling, mailbox import, deduplication, folder handling, or other protocol-facing behavior, include integration coverage with `greenmail-junit5` in addition to any narrower unit tests.
- Always finish with the stack ready for manual testing whenever feasible.
- When the task touches one or more runnable applications or services, finish by running the appropriate `docker compose` command to force a fresh deployment of every application or service affected by the changes, unless the user explicitly asks not to or the environment makes that impossible.
- Prefer minimal, focused changes that preserve the existing architecture and style.
- Always provide a ready-to-use semantic commit message for committing all uncommitted changes currently present in the repository, not just the changes made during the current task.
- The proposed commit message must include:
- A semantic commit subject line.
- A detailed commit body summarizing the relevant changes introduced across all current uncommitted changes.
- Enough detail that a teammate can understand the overall scope of the pending commit without re-reading the diff.

## AI agent routing

If you are an AI agent working in this repository, start by reading this file, then load only the additional Markdown docs needed for the task.

Use these task routes:

- Broad or unclear repo work: apply the general guardrails in this file first, then read [README.md](README.md), [CONTEXT.md](CONTEXT.md), and any relevant docs.
- Quarkus backend, persistence, auth, OAuth, polling engine, or REST changes: also read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- React admin UI, `/remote`, translation, or layout changes: also read [admin-ui/README.md](admin-ui/README.md).
- Polling, mailbox import, dedupe, live controls, or source/destination isolation changes: treat mailbox-to-destination isolation and encrypted secret handling as hard invariants and re-read the relevant polling/mailflow sections in [CONTEXT.md](CONTEXT.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- Setup, OAuth callback, TLS, hostname, passkey, or operator bootstrap changes: also read [docs/SETUP.md](docs/SETUP.md), [docs/OAUTH_SETUP.md](docs/OAUTH_SETUP.md), and [docs/TRUST_LOCAL_CA.md](docs/TRUST_LOCAL_CA.md).
- End-of-task validation, PR, or handoff work: follow the validation and commit-message requirements in this file and check [CONTRIBUTING.md](CONTRIBUTING.md) if needed.

This repository also includes Codex-native helper skills under [`.codex/skills`](.codex/skills). Those files are primarily for Codex and are not portable to every AI tool, so any agent that does not understand Codex skills should treat this `AGENTS.md` file as the portable source of workflow instructions and use the `.codex/skills` files only as optional extra references.

## Security rules

- This project aims for the maximum practical security standards that make sense in its context. Agents should prefer the more secure design when choosing between implementation options, especially around authentication, authorization, mailbox access, session handling, network exposure, secret storage, encryption at rest, and browser/server trust boundaries.
- Treat secure encrypted token storage as the default path for UI-managed secrets.
- Do not introduce fallback behavior that weakens secure-storage guarantees when the product now requires a hard failure.
- Preserve or improve encryption-at-rest handling for passwords, refresh tokens, and other sensitive values.

## Architecture rules and definitions

- Check specific documentation in [docs](docs).

## Product rules

- Check specific documentation: [README.md](README.md) and [CONTEXT.md](CONTEXT.md).

## UI rules

- Check specific documentation: [README.md](admin-ui/README.md).

## Validation rules

- Run relevant tests after changes.
- If Docker is the reliable local execution path, use it to build and prepare the app for manual verification.
