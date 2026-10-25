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

Tailscale is optional. It controls reachability and may terminate external HTTPS;
the standard images still expect the internal TLS configuration documented in
[SETUP.md](SETUP.md). An existing deployment using a different HTTP proxy packaging
needs a deliberate ingress/certificate migration before adopting these images.
Do not replace its images blindly or start a second importer on the same mailboxes.
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
on an isolated disposable stack. PR builds run the same image checks without a
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
