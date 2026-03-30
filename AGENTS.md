# AGENTS

Standing instructions for future agents working in this repository.

## Workflow rules

- Do not read `.env` unless the user explicitly asks for it.
- Treat [CONTEXT.md](CONTEXT.md) as the source of truth for cross-chat memory in this repository, and keep it up to date when meaningful behavior, architecture, validation expectations, or runtime constraints change.
- Update documentation whenever behavior, UI copy, setup flow, or architecture changes.
- Add or update tests for every behavior change.
- Always finish with the stack ready for manual testing whenever feasible.
- When the task touches one or more runnable applications or services, finish by running the appropriate `docker compose` command to force a fresh deployment of every application or service affected by the changes, unless the user explicitly asks not to or the environment makes that impossible.
- Prefer minimal, focused changes that preserve the existing architecture and style.
- Always provide a ready-to-use semantic commit message for committing all uncommitted changes currently present in the repository, not just the changes made during the current task.
- The proposed commit message must include:
- A semantic commit subject line.
- A detailed commit body summarizing the relevant changes introduced across all current uncommitted changes.
- Enough detail that a teammate can understand the overall scope of the pending commit without re-reading the diff.

## Security rules

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
