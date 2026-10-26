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

## Follow-up review: live states and unit coverage

Both live-source early-return paths are now terminalized. If a renewal moves the
safety deadline forward between scheduler selection and worker execution, the
source completes normally with zero work. If its execution guard is occupied, the
source finishes with the existing poll_busy error and leaves that other execution's
guard owned. Tests subscribe to real PollingLiveService SSE events and assert the
final source state and finishedAt. Both regressions failed against the previous
head (source=RUNNING) and pass with the fix.

The renewal component has package-level scheduling collaborators for deterministic
close-race unit tests. Its production constructor still starts virtual threads
and sleeps normally. Pure watcher tests substitute only protocol boundaries and
assert notification/catch-up coalescing, retry, reconnect, replacement, acknowledgements
and stale callback rejection, without a socket, CDI runtime or database.

## Validation

- 732 backend plain unit tests passed in 99 classes, from cleared JaCoCo data.
- 82 backend integrations passed in 11 classes, independently recorded. These
  retain the real PostgreSQL/CDI and silent-open GreenMail destination regressions.
- 668 frontend tests passed in 104 files; the frontend production build passed.
- Three coverage-gate regressions pass: integration evidence cannot satisfy missing
  unit coverage, frontend branch gaps fail, and the separate integration percentage
  is not silently relabelled as a unit threshold.
- 34 packaged-runtime tests passed separately (no failures/errors/skips).
- Production PostgreSQL runtime was rebuilt after the H2 packaged-test profile;
  the freshly deployed local HTTPS health endpoint returned UP.
- Repository privacy and tracked-source secret checks pass; Semgrep has no findings
  on this update's changed lines (11 existing findings remain outside this fix).

## Changed executable code coverage

Base: `83936c9a46cffd327aa62f98d719e41e08a3f244` (the reviewed PR base).
All added executable lines/branches across the whole PR are included. No coverage
exclusions were added. Reports were regenerated from cleared execution data in
disjoint groups; previously accumulated local reports are not used for these results.

| Execution group | Added executable lines | Added branches |
| --- | --- | --- |
| Backend plain unit | 349/349 (100.0%) | 286/286 (100.0%) |
| Backend integration only | 180/349 (51.6%) | 94/286 (32.9%) |
| Frontend Vitest | 10/10 (100.0%) | 98/98 (100.0%) |

The requested changed-code unit line/branch threshold is reached. In particular,
ImapIdleWatchService has **77/77 added executable lines and 48/48 branches** covered
by plain unit tests. IdleTimingPolicy remains 18/18 lines and 24/24 branches.
Integration execution is reported separately and is not added to the unit totals.

Frontend 10/10 describes instrumented executable locations, not complete coverage
of every UI edit or rendering behavior. The 98/98 figure covers the branches
instrumented on added lines. Locale catalogs and other non-instrumented locations
are not presented as covered production logic. These metrics do not establish
provider-specific long-duration behavior.

## Reproduce

Use container-only tooling. Backend builds share target and must run sequentially.
Stage new source files before the diff report. Each backend group deletes its
prior execution file and appends only across that group's test JVMs.

```sh
python3 scripts/validation/changed-coverage.py --run
./scripts/dev frontend sh -c 'npm run test:coverage && npm run build'
python3 scripts/validation/changed-coverage.py --base 83936c9a46cffd327aa62f98d719e41e08a3f244 --check
python3 scripts/validation/test-changed-coverage.py
```

Classification: target/coverage/test-groups.json. CDI component, Quarkus,
GreenMail and Testcontainers classes belong to integration, not plain unit.
Backend reports: target/coverage/unit/ and target/coverage/integration/.
Frontend reports: admin-ui/coverage/. --check fails if added backend unit or
frontend executable lines or branches remain uncovered; it does not substitute
integration results. Packaged-runtime and deployment smoke checks remain separate.
