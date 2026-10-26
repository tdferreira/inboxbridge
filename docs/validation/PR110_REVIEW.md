# PR 110 review validation — 2026-10-26

All four requested regressions are addressed:

- Shared timing validation rejects extreme durations before persistence/arithmetic,
  requires renewal >= 1 minute, caps safety at 24 hours, and preserves strict
  safety > renewal + response deadline. Authenticated admin and mailbox-owner
  endpoint tests cover invalid values, accepted boundaries and inherited reset.
- Scheduler eligibility and worker exceptions are isolated per source. A corrupt
  persisted safety value cannot abort two other users' due sources.
- Polling-state DTO reads use short transactions. The real PostgreSQL/CDI scheduler
  regression runs two virtual workers and verifies no transaction spans mailbox I/O.
- A GreenMail proxy keeps the watch TCP session open while suppressing only server
  responses. The watcher initially remains healthy, then its bounded renewal fails,
  reconnect catch-up delivers one message to a real destination, and another sync
  does not duplicate it. Only a test execution seam accelerates renewal.

## Validation

- 700 plain backend unit tests passed (97 classes).
- 82 backend integration tests passed (11 classes, including CDI, GreenMail,
  PostgreSQL and transit providers). These are not counted as unit coverage.
- 660 frontend tests passed (104 files), and the frontend production build passed.
- Negative controls failed as expected: removing the state-read transaction caused
  `ContextNotActiveException` in parallel workers; disabling accelerated renewal
  left the silent session unable to import during the bounded test window. Both
  controls were restored before the positive full-suite runs.
- Repository privacy and tracked-source secret checks passed. Semgrep reported no
  findings on this review's changed lines; its 11 existing findings remain outside
  this correction. Runtime protocol fixtures use synthetic data only.
- Development backend/frontend were rebuilt and freshly deployed. Verified HTTPS
  `/api/health/summary` returned `UP`. Real provider timing remains an operator test.

## Changed executable code coverage

Base: `83936c9a46cffd327aa62f98d719e41e08a3f244` (PR base).
This measures added executable lines/branches across the entire PR, including this
review update. Test code, non-executable lines and locale catalogs without LCOV
instrumentation are not treated as covered production logic.

| Execution group | Lines | Branches |
| --- | --- | --- |
| Backend: plain unit tests | 240/342 (70.2%) | 178/284 (62.7%) |
| Backend: integration tests only | 178/342 (52.0%) | 94/284 (33.1%) |
| Frontend: Vitest | 10/10 (100.0%) | 80/98 (81.6%) |

The new shared `IdleTimingPolicy` alone has 18/18 executable lines and 24/24
branches covered by unit tests. **The whole PR does not have 100% unit coverage.**
The groups are independent and overlap; their percentages must not be added.
The watcher is predominantly exercised in the protocol integration group, which
is deliberately not relabelled as unit testing.

## Reproduce

Use the container-only development environment; backend builds share `target`
and must run sequentially. New source files must be staged before the diff report.
JaCoCo data is cleared per group and appended across test JVMs, never across groups.

```sh
python3 scripts/validation/changed-coverage.py --run
./scripts/dev frontend sh -c 'npm run test:coverage && npm run build'
python3 scripts/validation/changed-coverage.py --base 83936c9a46cffd327aa62f98d719e41e08a3f244
```

Backend XML/HTML reports: `target/coverage/unit/`, `target/coverage/integration/`.
Test classification: `target/coverage/test-groups.json`; integration includes
Quarkus/CDI component tests and every GreenMail or Testcontainers class.
Frontend JSON/LCOV/HTML reports: `admin-ui/coverage/`.
Packaged-runtime and image smoke checks are performed separately by PR CI; they
are not included in the local test/coverage counts above.
