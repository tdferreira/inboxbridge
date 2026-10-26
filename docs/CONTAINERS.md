# Prebuilt container images and release publication

[Français](CONTAINERS.fr.md)

## Run a published version

Each release publishes backend and frontend images to GHCR for `linux/amd64` and
`linux/arm64`. No compilation is needed on the deployment machine. For the official
repository the names are `ghcr.io/tdferreira/inboxbridge-backend` and
`ghcr.io/tdferreira/inboxbridge-frontend`. A fork automatically uses its own owner
and repository name: `ghcr.io/OWNER/REPOSITORY-backend` and `...-frontend` (lowercase).
Use the **same version** for both services, or pin the returned manifest digests.

Download the release bundle and create the deployment environment as described in
[SETUP.md](SETUP.md). Select the exact version published by that repository:

```text
INBOXBRIDGE_BACKEND_IMAGE=ghcr.io/OWNER/REPOSITORY-backend:vX.Y.Z
INBOXBRIDGE_FRONTEND_IMAGE=ghcr.io/OWNER/REPOSITORY-frontend:vX.Y.Z
```

These are image-reference settings, not secrets. Then run:

```sh
docker compose -f docker-compose.yml -f docker-compose.images.yml pull
docker compose -f docker-compose.yml -f docker-compose.images.yml up -d --no-build
```

The override preserves standard TLS, private backend/database ports, per-service
certificate mounts, configuration and PostgreSQL storage. `--no-build` prevents
compilation even though the base Compose file also supports source builds.
Back up the database and preserve encryption/vault material before upgrades.
V74/V75 add nullable IDLE settings; rollback requires the prior database backup
as well as the old images. Never copy a running PostgreSQL folder as a file backup.

### One image pair, two ingress modes

The **same backend and frontend tags** support both profiles. Tailscale is optional;
any trusted external HTTPS terminator can serve the second profile.

| Frontend setting | Frontend/backend link | Public-facing HTTPS |
| --- | --- | --- |
| `TLS_ENABLED=true` (default) | HTTPS on backend 8443 | Frontend 8443, with the standard certificate mounts |
| `TLS_ENABLED=false` | HTTP on backend 8080 | A trusted external proxy, such as Tailscale Serve |

`false` requires `PUBLIC_HOSTNAME`, a lowercase canonical DNS hostname without
scheme, port or path. The frontend serves HTTP on 8080, rejects other Host values,
and replaces incoming forwarding headers with the canonical HTTPS origin. It runs
as UID 101 and supports a read-only filesystem with writable `/tmp`. No certificate
mount is required in this mode. Invalid modes, missing default-mode certificates
and malformed hostnames fail closed; missing certificates never select HTTP.

For an existing HTTP deployment, keep its database, networks, folders, encryption
keys, OAuth hostname and Tailscale target. Change both images and add **only**
`TLS_ENABLED: "false"` to the existing frontend service environment. Retain the
existing backend HTTP settings: `HTTP_PORT=8080`,
`QUARKUS_HTTP_INSECURE_REQUESTS=enabled`, empty
`QUARKUS_HTTP_SSL_CERTIFICATE_FILES` and `QUARKUS_HTTP_SSL_CERTIFICATE_KEY_FILES`,
and the existing JDBC URL and forwarding settings. The backend uses Docker `CMD`,
so an existing Compose `command: [java, ..., -jar, /app/quarkus-run.jar]` still works.
The frontend flag selects only the frontend mode; it does not silently reconfigure
the database or backend transport.

Keep HTTP ingress loopback-only (for example `127.0.0.1:18080:8080`), leave backend
and database host ports unpublished, and retain private Docker networks. This
mode assumes those internal links are trusted; source/destination mailbox TLS
requirements remain unchanged. Existing standard-TLS installations need no new
setting or certificate change. Do not start a second importer on the same mailboxes.
No Gmail credentials, environment files, private certificates, database contents
or encryption keys belong in an image or build context.

## Maintainer publication

Pushing a semantic version tag (`vX.Y.Z` or a prerelease such as `vX.Y.Z-rc.1`)
starts the release workflow. Manual runs validate the supplied tag and check out
that tag, rather than building whichever branch launched the workflow.

Before publishing, the workflow verifies backend tests including packaged-runtime
checks, frontend tests/build, extension tests and repository privacy checks. It
creates an explicit runtime-only context from `target/quarkus-app`, `admin-ui/dist`,
Nginx configuration and LICENSE. It builds both images locally and checks HTTPS
health/UI, non-root users, CA-key isolation and unpublished backend/database ports
on an isolated disposable stack. The same pair is also tested with the existing
HTTP/Java command contract, read-only services, hostname rejection and explicit
TLS-mode validation, without certificate mounts. PR builds run the same image checks without a
registry login or publication.

After these gates, GHCR receives both architectures with source/revision/version
labels, provenance and SBOM attestations. Tags are the full release version and
`sha-FULL_SOURCE_COMMIT`; no moving `latest` alias is created. The GitHub release
is published only after both image builds succeed. Publication is not transactional
across packages: a failed second push can leave one image available; rerun the
same workflow to complete the pair and use a completed release as the signal.
The archive and release notes include the prebuilt-image override and instructions.

The release job uses `GITHUB_TOKEN` with `contents: write` and `packages: write`;
no personal registry token is needed. Organization policy can still block package
creation. On first publication, verify package access and set both packages to
**public** if anonymous pulls are intended; repository visibility alone does not
establish package visibility. Existing packages must grant Actions access to the
publishing repository. Source labels identify that repository, including forks.
Fork publication is independent of upstream acceptance. Do not reuse a release
tag for different source; pin digests when immutable deployment references matter.
