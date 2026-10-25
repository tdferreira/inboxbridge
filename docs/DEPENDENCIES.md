# Dependency policy and audit

## Selection policy

Prefer the latest patched LTS release offered for the distribution already used
by InboxBridge. Check the release lifecycle and published security advisories
before choosing a version. If its latest LTS patch still has a major applicable
security flaw, use the latest stable release that fixes that flaw instead.
For projects without an applicable LTS offering, use the latest compatible
stable release. A commercial edition's LTS offering does not make an open-source
edition an LTS product or authorize changing licenses or editions.

Review frontend packages, backend dependencies and plugins, GitHub Actions,
package managers, build/runtime images, bootstrap images, and integration-test
images. Include transitive dependencies and renamed artifacts: a version check
against an obsolete Maven coordinate can miss an entire supported release line.
Keep framework-managed libraries aligned through the selected framework BOM.
Do not replace a stable dependency with an alpha, beta, milestone, or release
candidate merely because a version listing puts it first.

High/critical advisories and active exploitation warrant applicability review.
A scanner result identifies a package/version match, not proof that the
application executes the affected path. Prefer patched releases and remove
unneeded vulnerable components where practical; retain and report unresolved
findings rather than silently suppressing them. If no corrected upstream image
exists, document the gap instead of assuming that a floating tag is patched.
Database major upgrades require a separate backup, migration, and recovery plan.

Dependabot provides update visibility, but cannot express this dynamic policy.
Its proposals still need lifecycle/security review and checks for image strings
embedded in test code.

## Audit dated 2026-10-25

The audit used npm registry metadata and `npm audit`, Maven Central version and
dependency resolution, `gh` release/advisory queries, official lifecycle pages,
Docker image manifests, OSV batch queries, and Trivy 0.76.1 image scans.

### Frameworks, tools, and infrastructure

| Component | Selected version | Decision |
| --- | --- | --- |
| Quarkus | 3.40.1.1 | Latest LTS patch; includes the October emergency security fixes. Keep its platform libraries on this BOM. |
| Java | 25 LTS | Latest LTS line. Java 25.0.5+8 binaries are released, but the inspected official Temurin images still contain 25.0.4.1+1; see the upstream gap below. |
| Node.js | 24.22.1 | Latest LTS patch. Node 26.12.1 is Current on the audit date; its scheduled LTS transition is October 28, 2026. |
| npm | 12.2.1 | Latest stable, with no separate LTS line. Declared in `admin-ui/package.json`; Docker and CI install it before `npm ci`. |
| Maven | 3.10.0 | Latest stable. Builder uses `maven:3.10-eclipse-temurin-25`. |
| Nginx OSS | 1.31.8 | Latest released mainline. Nginx OSS has mainline/stable branches rather than the commercial Nginx Plus LTS program. |
| Alpine bootstrap | 3.24 / 3.24.3 | Latest stable branch/patch, used by `cert-init`. |
| PostgreSQL | 16.15 deployed; 18.6 available | Latest patch on the existing major. Every PostgreSQL major receives five years of support; there is no separate LTS branch. Version 18.6 requires a planned migration from the existing 16 data volume. |
| OpenBao test server | 2.7.1 | Latest published stable release; replaces an unpinned `latest` test image. |
| Vault Community test server | 2.2.1 | Latest stable Community release; replaces 1.19. Enterprise 1.19 LTS is a different edition. |

Build and release workflows keep Java 25 and Node 24 and enable `check-latest`
so cached tool installations do not silently retain older patches.
The frontend image applies available Alpine package upgrades during the build.
The upstream Pebble binary is retained. InboxBridge and Maven use direct
entrypoints; deleting this unused binary was not an application vulnerability
fix. Its scanner matches remain visible below.

Authoritative references:

- [Quarkus releases](https://quarkus.io/releases/) and [3.40.1.1 security release](https://quarkus.io/blog/quarkus-3-40-1-1-released/)
- [Node release schedule](https://github.com/nodejs/Release/blob/main/schedule.json) and [release index](https://nodejs.org/dist/index.json)
- [Temurin 25.0.5+8 release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8)
- [Nginx releases](https://nginx.org/news.html), [branch policy](https://docs.nginx.com/nginx/admin-guide/installing-nginx/installing-nginx-open-source/), and [security advisories](https://nginx.org/en/security_advisories.html)
- [Alpine lifecycle](https://alpinelinux.org/releases/) and [PostgreSQL version policy](https://www.postgresql.org/support/versioning/)
- [OpenBao releases](https://github.com/openbao/openbao/releases), [Vault releases](https://github.com/hashicorp/vault/releases), and [HashiCorp LTS policy](https://www.hashicorp.com/en/long-term-support)

### Frontend dependencies

`npm outdated --json` reports no outdated direct dependencies. The selected
frontend projects do not offer a separate applicable LTS line.

| Dependency | Version |
| --- | --- |
| react / react-dom | 19.3.1 |
| react-router-dom | 7.18.4 |
| recharts | 3.10.1 |
| vite | 8.3.5 |
| @vitejs/plugin-react | 6.1.2 |
| vitest / @vitest/coverage-v8 | 5.0.5 |
| jsdom | 30.1.3 |
| @testing-library/react | 16.3.3 |
| @testing-library/jest-dom | 7.0.1 |

Vite's [support policy](https://vite.dev/releases) identifies 8.3 as the current
regular-patch stream rather than an LTS release. The two browser-extension
packages have no external package dependencies.

### Backend dependencies and plugins

| Dependency | Version |
| --- | --- |
| Angus Mail | 2.0.5 |
| Commons Compress | 1.28.0 |
| WebAuthn server | 2.10.1 |
| ALTCHA | 2.1.1 |
| org.json | 20260814 |
| GreenMail JUnit integration | 2.1.14 |
| Testcontainers | 2.0.5 |
| Maven Compiler | 3.16.0 |
| Surefire / Failsafe | 3.6.0 |
| JaCoCo | 0.8.15 |

Testcontainers modules now use the `testcontainers-` artifact prefix and a shared
BOM; the PostgreSQL container uses its current package/API. Quarkus test modules
use the current `quarkus-junit` and `quarkus-junit-component` coordinates.
The [Quarkus migration guide](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.31)
and [Testcontainers releases](https://github.com/testcontainers/testcontainers-java/releases)
document these renames.

Quarkus 4 beta and Angus Mail 2.1 milestone releases are excluded. REST Assured
6.3.0 is available separately, but the project keeps the Quarkus BOM's 6.0.1 to
preserve platform compatibility instead of independently overriding that test
stack. Other platform-managed dependencies follow the same rule.

### GitHub Actions

Actions are pinned to immutable commit SHAs verified from the stable releases
listed below, with version comments for Dependabot. These actions have no
separate LTS stream. The release-only Firefox signing tool is pinned to
`web-ext@10.8.0` rather than resolved through an unversioned `npx` invocation.

| Action release family | Latest release checked |
| --- | --- |
| actions/checkout@v7 | v7.0.1 |
| actions/setup-java@v6 | v6.1.0 |
| actions/setup-node@v7 | v7.1.0 |
| actions/configure-pages@v6 | v6.0.0 |
| actions/upload-pages-artifact@v5 | v5.0.0 |
| actions/deploy-pages@v5 | v5.0.1 |

### Security results and upstream gaps

- `npm audit` reports zero findings for the frontend dependency tree.
- OSV reports no advisories for the final 326 resolved backend dependency
  entries, including test dependencies. This check does not cover JRE binaries
  or build plugins' private dependency trees, and does not unpack libraries
  shaded into another dependency's jar.
- After updating Nginx/Alpine packages, Trivy reports no high/critical findings
  in the Nginx application runtime or Alpine bootstrap image. The Java runtime
  retains upstream Pebble and has four HIGH Go stdlib matches in that binary:
  CVE-2026-111943, CVE-2026-78667, CVE-2026-78669 and CVE-2026-97031. InboxBridge
  starts Java directly and does not execute Pebble. These remain inventory
  findings, not demonstrated reachable application vulnerabilities. The same
  binary is present in the upstream Maven image. Counts describe the inspected
  contents and advisory database on the audit date.
- The official Temurin 25 Docker tag still contains 25.0.4.1+1, despite the
  published 25.0.5+8 binary release. Both the Maven builder and application base
  share this upstream packaging gap. Retain Java 25 LTS and explicitly recheck
  the actual Java version when the official images are rebuilt; a current tag
  alone does not establish that the latest CPU patch is installed. CI asks
  setup-java for the latest available Java 25 patch directly.
- The upstream Node 24 image includes vulnerable npm dependencies. The project
  installs npm 12.2.1 independently; do not treat the upstream base image's
  package-manager inventory as the final build-stage inventory. The final
  frontend build stage still has one high match: npm 12.2.1 bundles Undici
  6.29.0, affected by CVE-2026-109869 (fixed in 6.29.1). No newer npm release was
  available in the registry. This is separate from the application's patched
  Undici 8.12.3 dependency and is not included in the served Nginx image.
- In addition to Pebble, the Maven build stage has four high matches across three CVEs:
  CVE-2026-54399 and CVE-2026-54428 in HttpCore 5.3.6 shaded into docker-java's
  transport jar, and two instances of CVE-2025-67030 in plugin-side Plexus
  Utils 3.0.24/3.6.0. Fixed upstream libraries exist, but the current tool/plugin
  or shaded artifact still bundles the older versions. These findings are not
  in the application runtime image and require upstream artifact updates or a
  separately validated dependency override; they are not silently suppressed.
- PostgreSQL 16's upstream Debian image has 88 high/critical package findings
  (43 distinct CVEs, including two critical matches). They include system
  utilities, libxml2, and Go dependencies embedded in `gosu`; they do not by
  themselves establish an exploitable InboxBridge path. The libxml2 match is
  CVE-2026-6653; Debian currently labels it a minor issue with no stable security
  update, despite the scanner's critical classification. See the
  [Debian tracker](https://security-tracker.debian.org/tracker/CVE-2026-6653).
  The other critical match is CVE-2025-68121 in the Go runtime embedded in the
  privilege-switching helper. Assess the affected code paths and available
  upstream rebuilds before changing the database distribution or major version.
- The latest OpenBao 2.7.1 test image has five high-severity package matches
  (four distinct CVEs) in its Go runtime and `golang.org/x/net`. No newer stable
  image was published in the checked release list. These disposable dev-mode
  test containers do not contain deployment credentials. The latest Vault
  Community 2.2.1 test image has no high/critical matches in this scan.

## Validation

The dependency refresh is validated with the complete Java 25 Maven test suite,
all packaged REST smoke tests, the full Node 24 frontend suite and production
build, both browser-extension suites, and the public-site generator check.
The final pass completed 749 backend tests, 34 packaged smoke tests, 655 frontend
tests, 81 Chromium tests, and 11 Firefox tests without failures or skips. The
public-site check passes. After fresh Compose deployment, frontend-proxied backend/database
health is `UP`, the main UI and `/remote` return HTTP 200 with local-CA TLS
verification, and `nginx -t` succeeds.
See [CONTRIBUTING.md](../CONTRIBUTING.md) for exact commands and the Compose/Bake
build fallback. Leave the rebuilt affected services running and check HTTPS
health using the local CA after deployment.

The subsequent [source security review](SECURITY_REVIEW.md) records application
findings, fixes, source-scan triage and additional security regression checks.
