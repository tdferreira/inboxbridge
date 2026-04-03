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
4. If runnable services were touched, finish with `docker compose up --build -d` unless the user explicitly asked not to or the environment blocks it.
5. Leave the stack ready for a human to open `https://localhost:3000` and `https://localhost:3000/remote`.

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

- Contributor expectations: [`CONTRIBUTING.md`](/Users/tdferreira/Developer/inboxbridge/CONTRIBUTING.md)
- Repo memory and validation notes: [`CONTEXT.md`](/Users/tdferreira/Developer/inboxbridge/CONTEXT.md)
- Workflow rules: [`AGENTS.md`](/Users/tdferreira/Developer/inboxbridge/AGENTS.md)
