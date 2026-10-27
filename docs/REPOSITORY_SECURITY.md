# Repository security governance

InboxBridge accepts public contributions through forks. Opening a pull request
does not grant repository access or permission to run privileged workflows.
Repository controls reduce supply-chain risk; they cannot prove that reviewed,
signed or tested code is harmless.

## Main branch

Keep access control separate from merge requirements:

- The merger allowlist permits only the maintainer's individual GitHub account,
  through pull requests only. Do not grant this exception to contributors,
  repository roles, deploy keys or automation.
- The core protection ruleset has no bypass actors, including administrators.
  It requires verified commit signatures, a protected maintainer approval,
  resolved discussions and an up-to-date successful build.
- Bind the `.github/workflows/build.yml` required check to the GitHub Actions
  integration, rather than accepting a matching check name from any source.
- Require CodeQL results, with all security-alert severities and diagnostic
  levels blocking new findings. Keep the managed extended scans for Actions,
  Java, JavaScript/TypeScript and Python, including local taint sources for Java.
- Keep force-push and deletion restrictions active.

GitHub records the narrow merger-allowlist exception as a bypass of that access
rule. It must never exempt a merge from the separate core safety rules. Inspect
the detailed rule evaluation when auditing a merge.

GitHub does not let a PR author approve their own PR. For this sole-maintainer
repository, the required `maintainer-approval` environment supplies the human
decision instead. Its only reviewer is `tdferreira`; self-review is allowed so
the same maintainer can approve owner-authored work. This grants no contributor
approval or merge rights. Every PR author follows the same protected gate.

The approval workflow has no token permissions, secrets, checkout or PR-code
execution. Core rules require its environment deployment to succeed before
merge. Administrator bypass is disabled for that environment. New commits need
their own successful approval deployment; cancellation groups isolate each PR.
GitHub's native second-person review requirements are disabled only after this
replacement gate is configured and validated. `CODEOWNERS` remains a review
routing aid, not the sole-maintainer approval mechanism.

Approval is a manual decision on the specific candidate, separate from green CI.
An agent must not approve this gate merely because tests passed or because it
was authorized to configure the gate. Wait for the owner's explicit approval of
the PR content. Keep the owner-only merger allowlist unchanged.

## GitHub Actions and security reporting

Require approval for **all external contributors**, including contributors whose
earlier PRs were merged. Retain read-only default workflow tokens and prohibit
Actions from approving PRs. Keep PR checks on disposable GitHub-hosted runners,
without publishing credentials or a privileged `pull_request_target` checkout.

Enforce full commit-SHA action references and an explicit action allowlist.
Review additions to that allowlist alongside workflow changes. Existing workflows
use pinned actions; a verified Marketplace publisher alone is not an allowlist.
Keep `CODEOWNERS` coverage for the whole repository, especially `.github/`.

Enable secret-scanning alerts, push protection, Dependabot security updates and
private vulnerability reporting. Triage scanner alerts using the actual trust
boundary and reproducible evidence; neither dismiss every alert automatically
nor present scanner severity as proof of exploitability.

GitHub's generic-pattern and validity-check features are not available in every
repository plan. This personal public repository retains the tracked-source
Gitleaks/privacy checks for broader pattern coverage; do not describe provider
push protection as detecting every possible password or private key.

## Release integrity

Only the maintainer's individual account may create `v*` release tags. The
separate tag-integrity ruleset has no bypass actors and requires signed commits,
blocks updates/force pushes and prevents tag deletion.

Future GitHub releases are immutable: publish all assets before finalizing the
release. Existing published assets must not be replaced; publish a new version
for corrections. Release immutability does not make GHCR image tags immutable.
Consumers needing an exact image should pin its digest.

Release workflows must use reviewed source, fixed action SHAs and the minimum
publishing permissions. Changes to store publishing and release credentials need
specific review. Never expose those credentials to fork PR builds.

## Account boundary

The repository owner can still edit rules and permissions. Protect that account
with phishing-resistant passkeys or hardware security keys and review authorized
apps, tokens and recovery methods. Repository rules cannot prevent an attacker
who controls the owner account from changing repository policy.

## Validation

Use `./scripts/dev` for local checks. After installing the frontend development
dependencies, `./scripts/dev frontend node ../site/test-config-generator.mjs`
runs the public-site generator checks and real-DOM folder-name injection
regressions using the frontend's declared jsdom dependency. The regression covers
initial values, typed values and removal without interpreting folder names as
HTML. CI already installs those dependencies before this site check.

Repository settings are external to Git. Re-read their effective values and
applicable rules after changes; a documentation edit alone changes no protection.
