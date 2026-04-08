---
name: inboxbridge-quarkus-best-practices
description: Use when reviewing, refactoring, or extending the Quarkus backend architecture in InboxBridge. Captures modern Quarkus, Java 25, Maven, testing, package-structure, and backend-boundary best practices adapted to this repository.
---

# InboxBridge Quarkus Best Practices

Use this skill when the task is about backend architecture quality, Quarkus
project layout, service boundaries, testing strategy, or whether a change fits
modern Quarkus / Java / Maven practice.

Start with [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md),
[`CONTEXT.md`](../../../CONTEXT.md), and the affected backend packages under
`src/main/java`.

## Technology baseline

- Assume Quarkus is the backend platform of record.
- The repository targets modern Quarkus on Java 25. Prefer current Quarkus
  idioms over older Jakarta EE / Spring-style layering habits when they
  conflict.
- Keep Maven's standard layout intact:
  `src/main/java`, `src/main/resources`, `src/test/java`,
  `src/test/resources`.

## Package and architecture guidance

- Prefer feature-oriented package boundaries when a slice has enough cohesive
  behavior to justify them. InboxBridge already uses `service.auth`,
  `service.mail`, `service.polling`, `service.destination`, `service.oauth`,
  `service.user`, `service.admin`, `service.remote`, and matching `web.*`
  slices where it helps.
- Keep the top-level layer packages meaningful: `config`, `domain`, `dto`,
  `persistence`, `service`, `web`.
- Avoid re-flattening moved feature slices back into the top-level `service` or
  `web` package unless the class is truly cross-cutting.
- Keep REST resources thin. They should translate HTTP concerns, authorization,
  and request/response DTOs, then delegate business flow to services.
- Keep repositories thin data-access helpers. Transaction ownership should live
  primarily in service or resource entry boundaries, not inside random
  repository helper methods.
- Prefer extracting focused collaborators when a service begins mixing several
  responsibilities, especially protocol I/O, orchestration, state shaping,
  view assembly, retry policy, or diagnostics aggregation.
- Favor explicit seams over hidden package-private coupling. If a move exposes
  test-only reach-through, add a narrow constructor/setter/protected seam
  rather than widening unrelated implementation details.

## Quarkus coding guidance

- Prefer CDI-managed beans with clear scopes (`@ApplicationScoped` in most
  service cases).
- Prefer constructor injection for new helper/service code when practical,
  especially in extracted collaborators and code that benefits from explicit
  dependency graphs. Field injection is tolerated in existing code, but do not
  add hidden dependencies casually.
- Prefer `@ConfigMapping` for cohesive typed configuration groups, with
  validation/defaults where appropriate. Use scattered `@ConfigProperty` only
  for truly isolated values.
- Keep Quarkus REST execution semantics correct:
  blocking database/session/auth work must not accidentally run as non-blocking
  IO-thread logic. Use `@Blocking` where required, especially on endpoints or
  filters tied to DB-backed auth/session checks and SSE/live polling surfaces.
- Use `@Transactional` to define entry-point transaction boundaries on CDI
  beans. Avoid manual `UserTransaction` unless there is a concrete reason.
- Keep exception mapping centralized when several resources repeat the same
  HTTP error translation pattern.

## Java 25 guidance

- Use modern Java language features that improve clarity and are already a good
  fit for the repo: records, switch expressions, text blocks, pattern-friendly
  control flow, and virtual threads where the backend already uses them.
- Do not introduce novelty-only Java 25 syntax if it makes a stable Quarkus
  codepath harder to read or test.
- Prefer expressive immutable value carriers for DTO-like or diagnostic state
  where records fit naturally.

## Persistence and Panache guidance

- With Panache, keep entity/repository code simple. Use the repository pattern
  consistently where the project already chose it.
- Keep persistence models, runtime domain objects, and transport DTOs separate
  when they serve different concerns.
- Make dedupe, checkpoint, mailbox identity, and auth/session persistence
  rules explicit in code and tests; those are architecture invariants, not just
  implementation details.

## Security guidance

- Prefer the more secure design around secrets, OAuth tokens, sessions, CSRF,
  mailbox credentials, and browser trust boundaries.
- Preserve encrypted-at-rest handling for UI-managed secrets.
- Do not add fallback behavior that weakens secure storage or loosens browser
  OAuth validation.
- Keep auth/session hardening logic, GeoIP/location checks, and remote-surface
  protection inside coherent auth- or remote-focused boundaries.

## Testing guidance

- For narrow backend changes, start with focused unit tests around the touched
  services/resources.
- Keep `@QuarkusTest` coverage for CDI wiring and runtime helper seams where it
  adds real value.
- Keep packaged `@QuarkusIntegrationTest` smoke coverage for important runtime
  surfaces and startup health.
- For any mailbox, IMAP, POP3, destination-import, IDLE, dedupe, or
  protocol-facing change, add or update GreenMail-backed integration coverage.
- Expected warning/error paths in tests must be asserted, not blindly silenced.
  Prefer explicit log capture assertions over broad suppression.

## Validation expectations

- Typical backend validation path:
  1. focused `mvn -q -Dtest=... test`
  2. `mvn -q test`
  3. relevant packaged smoke tests via `mvn -q -Dit.test=... verify`
  4. warning/error scans when the task touches logging-sensitive paths
  5. `docker compose up --build -d`
- Packaged `verify` jobs in this repo must run sequentially. Concurrent runs
  can collide on GreenMail ports or shared `target/quarkus-app` output.
- After large package moves, a clean rebuild such as
  `mvn -q clean -DskipTests test-compile` may be required before trusting the
  full-suite result.

## Review checklist

When reviewing or refactoring the backend, explicitly check:

- Does the code fit an existing feature slice, or should a new slice exist?
- Are resources thin and services cohesive?
- Are transactions owned at sensible entry points?
- Is config centralized and typed where it should be?
- Are secrets handled securely?
- Are tests layered correctly: unit, Quarkus wiring, packaged smoke, and
  GreenMail where mailflow is involved?
- Does the change preserve InboxBridge invariants around mailbox isolation,
  destination identity, dedupe continuity, live polling truthfulness, and
  auth/session safety?
