# Container-only development with Codex

Open this checkout in Codex. Install/start Docker Desktop; Git and the macOS
OpenSSL utility are the only other host prerequisites. Java 25/Maven 3.10 and Node 24
run in disposable containers. No host language package installation is needed.

```sh
./scripts/dev doctor
./scripts/dev test-backend
./scripts/dev test-frontend
./scripts/dev build
./scripts/dev up
./scripts/dev status
```

Open https://localhost:3000. The initial upstream credentials are admin / nimda;
change them before configuring test accounts. Polling is disabled initially.
Only the frontend is published, bound to loopback (host 3000 to internal 8443). Backend and PostgreSQL use
TLS with certificates generated inside a dedicated Docker volume.

To export the public development CA, use `./scripts/dev ca`; the certificate is
written to `.dev-local/ca.crt`. See `docs/TRUST_LOCAL_CA.md` if you choose to trust
it for browser/passkey testing. Setup does not alter the macOS trust store.
A browser certificate warning is expected until that separate step is completed.

The wrapper never loads `.env`. It generates a separate random development
key in `.dev-local/` with restrictive permissions; never commit or print it.
Database credentials in Compose are synthetic and for this local stack only.
Never connect the development stack to a production database or real mailboxes
unless you explicitly intend an end-to-end test.

Additional commands:

```sh
./scripts/dev backend -B -Dtest=PollingServiceTest test
./scripts/dev frontend node ../site/test-config-generator.mjs
./scripts/dev logs --tail 100
./scripts/dev down
```

Language caches and frontend node_modules live in project-scoped Docker volumes.
Backend target/ and frontend dist/ are generated in the checkout (Git-ignored)
so the runtime images can consume the tested build outputs. Build is separate
from testing: always run relevant tests before submitting a contribution.
`up` recreates runtime containers; run `build` first after changing source code.
`down` retains database, certificates and cache volumes.

The backend tooling container has access to Docker's socket for Testcontainers
(PostgreSQL/OpenBao/Vault integration tests). That grants control of Docker
Desktop; only run trusted project tests. Ordinary runtime containers have no
socket mount. No Docker-in-Docker daemon is started.

Each checkout gets a distinct Compose project name, persisted in
`.dev-local/project-name` so moving the checkout keeps its volumes. For concurrent stacks set
`DEV_PORT=3001 ./scripts/dev up` (use the same port for subsequent commands).
`DEV_PROJECT` can override the namespace; keep it stable to retain the matching
key/database pair. This is independent of the NAS deployment.

This setup builds modified sources. The sibling inboxbridge-containers repository
continues to package official releases; its release manifest and published image
tags are not modified by local development.

PostgreSQL uses `PGDATA=/var/lib/postgresql/data/pgdata`: the upstream TLS
entrypoint copies certificates into the parent directory before initialization,
so using that same parent as PGDATA would make a fresh initdb fail.

The development stack follows upstream certificate isolation: cert-init alone
mounts the complete CA volume; backend/frontend/PostgreSQL mount their own leaf
subdirectory plus the public CA. Application UIDs are 10001 and 101.
`./scripts/dev security-static` checks upstream workflows, ingress compatibility
and ephemeral certificate bootstrap; `./scripts/dev security-runtime` verifies
the namespaced stack using only the exported public CA and anonymous requests.
The frontend tooling installs the npm version declared in package.json inside
its disposable container, not on macOS.
