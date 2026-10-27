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

Dependency updates follow the [LTS and security policy](docs/DEPENDENCIES.md).
Review all ecosystems and images, including package managers and test containers,
and verify the software inside floating image tags. Preserve framework BOM
compatibility, record advisory applicability and upstream gaps, and plan database
major migrations separately. `admin-ui/package.json` declares the npm version
used by Docker and CI; use that package manager when validating installs.

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

For a dependency refresh or a full backend validation, use Java 25 and run:

```bash
mvn -q -Dit.test='*QuarkusIT' verify
```

This also runs all packaged-runtime smoke tests with the H2 build profile.
Docker must be available for the PostgreSQL, OpenBao, and Vault test containers.
Run backend builds sequentially because they share `target/quarkus-app`.

Frontend:

```bash
cd admin-ui
npm test -- --run
npm run build
```

Use current Node 24 LTS (at least 24.15.0). `npm run test:coverage` runs the
same full frontend suite with V8 coverage enabled.

Complete repository validation also includes the browser extensions and public site:

```bash
npm --prefix browser-extensions/chromium test
npm --prefix browser-extensions/firefox test
node site/test-config-generator.mjs
```

End-to-end local stack:

```bash
docker compose up --build
```

If your change touches runnable services, please leave the stack in a state that is ready for manual testing.

Recent Compose/Bake versions may require explicit permission for this repository's
`build.network: host` setting. If a rebuild reports
`pass "--allow=network.host"`, build the existing stack with:

```bash
docker compose build --print inboxbridge inboxbridge-admin > /tmp/inboxbridge-bake.json
docker buildx bake -f /tmp/inboxbridge-bake.json --allow=network.host --pull --load
docker compose up -d --force-recreate --no-deps inboxbridge inboxbridge-admin
```

The final command assumes PostgreSQL and certificate initialization are already
ready; on a new deployment, start the dependencies through Compose first.

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

## Security regression checks

See [the source security review](docs/SECURITY_REVIEW.md) for scan scope, triage
and remaining upstream findings. Run `python3 scripts/security/test-workflows.py`
and `sh scripts/security/test-cert-init.sh` when changing CI/release or TLS
bootstrap. The latter uses disposable certificates in a Docker tmpfs. After
rebuilding the local stack, `python3 scripts/security/test-runtime.py` checks
anonymous HTTPS access, security headers, OAuth state rejection, request size
limits, unprivileged UIDs, certificate mount isolation and unpublished backend/DB
ports. It uses `certs/ca.crt`, never deployment private keys or valid credentials.

Reproduce source analysis with
`sh scripts/security/scan-source.sh /tmp/inboxbridge-source-semgrep.json`. The
script scans an explicit production-source snapshot excluding local environment
files, certificates, dependencies and generated/test assets. Docker/network and
Python 3 are required; scanner findings still require applicability review.
Fresh installations copied from `.env.example` select loopback explicitly.
Existing installations without `PUBLIC_BIND_ADDRESS` retain their prior
all-interface binding. Set this variable explicitly for the intended local, LAN
or tailnet interface, after replacing the bootstrap password. The backend's
8443 port is internal to Compose.

Run `python3 scripts/security/test-compose-ports.py` to verify legacy, fresh
and explicitly configured bindings without reading the deployment's `.env`.
Use `python3 scripts/security/test-runtime.py --base-url https://YOUR_HOST:3000`
to verify the operator's actual URL as well as localhost; testing localhost
alone does not prove a LAN/tailnet address remains reachable.

## Repository privacy

Keep operator home paths, real private hostnames, personal contact details,
local deployment/account counts and mailbox incident histories out of source
and audit notes. Use relative links and reserved example addresses. Public
project ownership, add-on identifiers and explicit synthetic fixtures remain
part of the project. Do not commit environment files, credential/certificate
exports, database dumps or optional personal image metadata.

Before committing, run:

```bash
python3 scripts/security/test-repository-privacy.py
python3 scripts/security/test-secrets-rules.py
sh scripts/security/scan-secrets.sh
```

Gitleaks 8.30.1 scans an explicit Git-tracked source snapshot with redacted
reports. The policy extends its standard secret rules with home-path, private
tailnet-host and email checks. Only exact, public deterministic test keys and
explicit example/project identifiers are exempted; no files or test trees are
skipped. Canary tests prove changed fixture keys and private identifiers are
still detected.

Use `sh scripts/security/scan-secrets.sh history` for a separate full-history
audit. It scans a self-contained Git bundle without local Git authentication
configuration or working environment files. A clean current tree does not
erase disclosures in old commits: published-history remediation requires a
coordinated rewrite and collaborator resynchronization, with additional
provider cleanup where retained copies exist.

### Compose versions and required build checks

The ingress test creates an empty service environment file inside its temporary
project directory. Older Compose releases, including the hosted runner's
2.38.2, validate `env_file` existence even with `--no-env-resolution`; a missing
fixture can therefore fail CI while newer local Compose versions pass. Keep
this fixture isolated: tests must not use the deployment environment file. The
build logs its Compose version before running the regression checks.

The build job reports the exact check name `.github/workflows/build.yml` to
match the existing main-branch requirement. Keep the job name and required
status-check context aligned. A successful check does not remove pull-request,
approval, signature or protected-ref requirements; direct pushes by a bypass
actor can succeed while GitHub reports the bypassed rules.

## Release container images

See [CONTAINERS.md](docs/CONTAINERS.md) for versioned GHCR publication, fork
ownership, first-package visibility and the prebuilt-image Compose override.
Release builds check out the exact validated tag, test the application, then
build a runtime-only context and smoke-test TLS/isolation before pushing
amd64/arm64 images. PR builds run the image checks without registry credentials.
To validate helpers locally without publishing:

```sh
python3 scripts/release/test-images.py
```

Use `scripts/dev` for container-only application tests/builds. After building,
`prepare-images.py` must target a new empty directory; `build-images.sh` and
`smoke-images.sh` require explicit image references, never operator secrets.
