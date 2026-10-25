---
name: inboxbridge-validation-release
description: Use when finishing InboxBridge work, validating a change, preparing a PR, or summarizing the current worktree for handoff. Focuses on the repository's required validation path, Docker readiness, and commit-message expectations.
---

# InboxBridge Validation And Release

Use this skill near the end of a task or when asked for release, PR, review, or handoff help.

## Validation ladder

1. Run the smallest relevant automated checks first.
2. Backend changes: `mvn -q test` or a narrower `-Dtest=...` pass before the full suite when appropriate.
3. Frontend changes: `cd admin-ui && npm run test:run` and `cd admin-ui && npm run build`.
4. For focused `PillboxInput`, App live-event, or other frontend regression work, run the narrow Vitest target first, then the full `cd admin-ui && npm run test:run` before handoff.
5. Public GitHub Pages site changes: `node site/test-config-generator.mjs` to catch locale/catalog drift and public-site markup regressions.
6. If runnable services were touched, finish with `docker compose up --build -d` unless the user explicitly asked not to or the environment blocks it.
7. Leave the stack ready for a human to open `https://localhost:3000` and `https://localhost:3000/remote`.

For dependency refreshes or an explicit request to run all tests, use Java 25
and `mvn -q -Dit.test='*QuarkusIT' verify` so packaged runtime checks run too.
Docker must be available for the PostgreSQL, OpenBao, and Vault integrations;
run backend builds sequentially because they share `target/quarkus-app`.
Use current Node 24 LTS (at least 24.15.0 for jsdom 30), run the full frontend
suite and build, and exercise `npm run test:coverage` when updating Vitest or
its coverage provider. Also run both browser-extension suites with
`npm --prefix browser-extensions/chromium test` and
`npm --prefix browser-extensions/firefox test`, plus the public-site generator
check. Compose image builds install with `npm ci` but do not run frontend
tests unless `RUN_TESTS=true` is explicitly enabled.
If a recent Compose/Bake version rejects the existing host build network, use
the explicit `--allow=network.host` Bake path in `CONTRIBUTING.md`, then recreate
the affected services with Compose.

Dependency audits must also check advisory applicability and image contents,
including build tools and integration-test images. A floating tag can contain
older runtimes or package-manager dependencies. The frontend's npm version is
declared by `packageManager` in `admin-ui/package.json`; Docker and CI install
that version before `npm ci`. Record dated findings and unresolved upstream
gaps in `docs/DEPENDENCIES.md`, without treating scanner severity alone as proof
that a deployed request path is exploitable.

## What to report back

- What changed
- What tests or builds were run
- Any gaps, blockers, or environment limits
- A ready-to-use semantic commit message that covers all current uncommitted changes in the repo

## Commit message rule

The proposed commit message must include:

- a semantic subject line
- a detailed body
- enough scope that a teammate can understand the whole pending commit without rereading the diff

## Supporting docs

- Contributor expectations: [`CONTRIBUTING.md`](../../../CONTRIBUTING.md)
- Repo memory and validation notes: [`CONTEXT.md`](../../../CONTEXT.md)
- Workflow rules: [`AGENTS.md`](../../../AGENTS.md)
- Public-site validation target: [`site/test-config-generator.mjs`](../../../site/test-config-generator.mjs)

## Runtime security checks

Run `python3 scripts/security/test-workflows.py` for workflow changes,
`sh scripts/security/test-cert-init.sh` for bootstrap/mount changes, and
`python3 scripts/security/test-runtime.py` after local deployment when changing
the ingress/auth/container boundary. The public URL remains localhost:3000;
backend 8443 is internal. Fresh `.env.example` installs explicitly bind the
frontend to loopback; existing deployments without the bind variable retain
the prior LAN/tailnet reachability. Run `test-compose-ports.py` for ingress
changes and `test-runtime.py --base-url https://OPERATOR_HOST:3000` against the
actual operator URL as well as localhost.
Certificate bootstrap must precede the unprivileged services so separate leaf
mounts have correct permissions. Preserve CA-key isolation. The scanner and
remaining findings are documented in `docs/SECURITY_REVIEW.md` and
`docs/DEPENDENCIES.md`; never report zero vulnerabilities from a clean scan.

For the container-only local path use `./scripts/dev test-backend`,
`./scripts/dev test-frontend`, `./scripts/dev build` and `./scripts/dev up`.
This replaces host Maven/npm commands and the production Compose invocation.
Report browser CA trust and real mailflow checks separately from automated tests.

IMAP IDLE changes require both narrow renewal/concurrency tests and GreenMail
coverage of DONE/NOOP, reconnect catch-up and safety import without notification.
Accelerated local tests do not establish provider-specific long-duration behavior.

For release-workflow or image publication changes, run
`python3 scripts/release/test-images.py` and the workflow security checks.
Use the same tested runtime-only context for amd64 smoke checks and amd64/arm64
publication. Source/fork registry names must be derived from the repository,
and manual release runs must check out the validated tag. Package visibility
and real operator mailflow remain separate checks.
