# InboxBridge 0.8.5

This release refreshes application dependencies, build tools and container
images, and mitigates Java4Shell while retaining Eclipse Temurin Java 25.
It includes the changes reviewed in [PR #122](https://github.com/tdferreira/inboxbridge/pull/122).

## Security

- Disable the affected Base64 intrinsic with
  `JAVA_TOOL_OPTIONS=-XX:-UseBASE64Intrinsics` in backend build/runtime images,
  development tools and CI to mitigate CVE-2026-114938 (Java4Shell).
- Keep Temurin 25. The inspected Linux images contain 25.0.5+8; patched
  25.0.5.1 images were not yet available during the dependency audit. The
  workaround does not install the full November Java security update.
- Preserve the mitigation when supplying custom `JAVA_TOOL_OPTIONS`. Base64
  throughput may decrease until patched Temurin images replace the workaround.
- Update Nginx to 1.31.9 and apply available Alpine package fixes.
- Retain the remaining upstream advisories and applicability assessments in
  [the dependency audit](DEPENDENCIES.md); this release does not claim that
  every included package is free of known vulnerabilities.

## Dependencies and compatibility

- Quarkus 3.40.1.2 LTS, Surefire/Failsafe 3.6.1, GreenMail 2.1.15 and JaCoCo 0.8.16.
- Current compatible frontend packages, including paired Vitest/V8 coverage
  5.1.1, Vite 8.3.8, React Router 7.18.6, Recharts 3.11.0 and jsdom 31.0.1.
- Node 26.13.1 LTS and npm 12.2.3 for frontend builds, tooling and CI.
- PostgreSQL 16.16, Alpine 3.24.4, OpenBao 2.7.2 and Vault Community 2.2.2 test images.
- Updated GitHub Actions pinned to verified release commit SHAs.

Backend and admin UI versions are 0.8.5. Browser extensions retain their
independent 0.2.1 version. PostgreSQL stays on major 16; no database-major or
edition migration is introduced. Rebuild source deployments or use the matching
versioned backend/frontend image pair. Preserve the documented backup procedure
before upgrading: [upgrade instructions](SETUP.md#upgrade-an-existing-installation).

## Validation

The dependency refresh passed 821 backend tests, 34 packaged API tests,
695 frontend tests with V8 coverage, 81 Chromium tests and 11 Firefox tests.
Public-site checks, production builds, both release ingress smoke tests,
runtime isolation, workflow/certificate checks and tracked-source privacy/secret
checks also passed. The release workflow repeats builds and tests against the
exact signed release tag before publishing its artifacts.

Browser CA trust and real-provider mailflow remain separate manual checks.
