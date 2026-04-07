# Contributing to InboxBridge

Thanks for your interest in improving InboxBridge.

This project is open to contributions, but it is still a self-hosted project with a relatively small maintenance surface. Clear, focused changes are the easiest to review and merge.

## Before You Start

- Read [README.md](README.md) for the project overview and setup path.
- Read [CONTEXT.md](CONTEXT.md) for the current product and architecture context.
- Check the docs in [docs](docs) when your change touches setup, OAuth, TLS, or architecture.
- Keep changes minimal and aligned with the existing structure and style.

## Local Setup

InboxBridge includes:

- a Quarkus backend
- a React admin UI
- PostgreSQL
- Docker Compose wiring for local startup

Typical local bootstrap:

```bash
cp .env.example .env
docker compose up --build
```

The main local entry points are:

- Admin UI: `https://localhost:3000`
- Remote control page: `https://localhost:3000/remote`
- Backend HTTP: `http://localhost:8080`
- Backend HTTPS: `https://localhost:8443`

For deeper setup help, see [docs/SETUP.md](docs/SETUP.md).

## Project-Local Codex Skills

This repository now includes project-local Codex skills under [`.codex/skills`](.codex/skills) for common InboxBridge work:

- `inboxbridge-worktree-guardrails`
- `inboxbridge-backend-change`
- `inboxbridge-admin-ui-change`
- `inboxbridge-mailflow-safety`
- `inboxbridge-validation-release`

These skills are meant to help future agents stay aligned with the repo's architecture, security model, docs expectations, and validation flow.

## Development Expectations

Please follow these rules when contributing:

- update documentation whenever behavior, UI copy, setup flow, or architecture changes
- add or update tests for every behavior change
- avoid introducing weaker secret-handling or token-storage behavior
- keep operator-facing and user-facing wording clear and direct
- prefer small pull requests over large mixed changes

## Validation

Before opening a pull request, run the relevant checks locally.

Backend:

```bash
mvn test
```

The backend suite intentionally mixes plain unit tests, GreenMail-backed
integration tests for protocol-facing mailflow behavior, and a small
Quarkus-managed component-test layer for shared CDI/config helpers. Prefer the
lightest test style that still covers the behavior you changed, but keep
protocol-facing mailbox changes covered by GreenMail.

Frontend:

```bash
cd admin-ui
npm test -- --run
npm run build
```

End-to-end local stack:

```bash
docker compose up --build
```

If your change touches runnable services, please leave the stack in a state that is ready for manual testing.

## Pull Requests

Good pull requests usually:

- explain the problem being solved
- describe the chosen approach
- mention any setup, schema, provider, or security implications
- include screenshots when UI behavior changes
- call out follow-up work that is intentionally left out

If your change affects source mailbox handling, destination mailbox handling, authentication, OAuth, or secret storage, include extra detail so reviewers can understand the risk profile quickly.

## Scope and Review

Maintainers may decline changes that:

- significantly expand maintenance burden without clear project value
- weaken security defaults or encrypted secret handling
- add large architectural shifts without prior discussion
- broaden provider support in ways that are difficult to validate or maintain

## AI-Assisted Contributions

AI-assisted contributions are welcome, but contributors remain responsible for reviewing, testing, and understanding what they submit.

If AI tooling was used in a meaningful way, say so in the pull request description when it helps reviewers understand how the change was produced.
