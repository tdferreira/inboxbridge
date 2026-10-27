# CodeQL review for InboxBridge 0.8.4

Reviewed on 2026-10-27 against `fa2b11d6536c81e598e9f85f0297e40394259d37`.
All 23 extended CodeQL alerts were traced to their inputs, sinks and trust
boundaries. Five expose real injection paths, one prompted logging hardening,
two identify display/test quality issues, and fifteen are false positives for
the security property reported. No protection or scan threshold was relaxed.

## Alert dispositions

Alert links use `https://github.com/tdferreira/inboxbridge/security/code-scanning/NUMBER`.
Locations below refer to the original analysis. False positives require a
control-flow or trust-boundary explanation in addition to passing tests.

| Alert | Rule / location | Disposition and evidence |
| --- | --- | --- |
| 1 | Python path injection, prepare-images:11 | False positive: this CLI intentionally accepts an operator-selected output path, not a server path under a promised restricted root. |
| 2 | Python path injection, prepare-images:12 | False positive: existing output paths are rejected, including symlinks to existing directories; the new canary regression verifies no overwrite. |
| 3 | Python path injection, prepare-images:18 | False positive: creating the explicit CLI output is the intended operation; CI chooses its own temporary directory. |
| 4 | Python path injection, prepare-images:20 | False positive: the two copied build directories are fixed. Existing allowlist tests exclude synthetic environment files and private keys. |
| 5 | Python path injection, prepare-images:22 | False positive: only fixed public Nginx files are copied to the chosen output. |
| 6 | Python path injection, prepare-images:23 | False positive: only the fixed public ingress directory is copied to the chosen output. |
| 7 | Python path injection, prepare-images:24 | False positive: the fixed public license is copied to the chosen output. |
| 8 | Python polynomial ReDoS, image-metadata:8 | False positive: repeated classes exclude the slash delimiter, without ambiguous nested repetition. Adversarial no-slash, bad-suffix and late-slash inputs scale linearly. CI supplies GitHub's repository name, not an HTTP parameter. |
| 9 | JS identity replacement, popup-controller.test:126 | Test quality: remove the no-op replacement from an identity stub; production escaping is separate. |
| 10 | JS DOM XSS, site/app:136 | Confirmed: folder text becomes HTML in the public environment generator. Replace HTML rendering with DOM creation/textContent. Stored and typed hostile-markup tests fail against the original implementation. No remotely supplied URL-to-input exploit was demonstrated. |
| 11 | JS password hashing, altcha.test:10 | False positive: SHA-256 hashes a proof-of-work challenge/counter, not an authentication password. Rename the misleading test variable without changing the protocol. |
| 12 | JS URL substring, EmailAccountListItem:19 | Display quality: substring matching mislabels unrelated hosts. Use exact provider domains/subdomains. This label never authorizes OAuth or selects credential recipients. |
| 13 | Java trust manager, SourceTransportSecurityService:208 | False positive for credential TLS: a private unauthenticated discovery socket detects even self-signed TLS. It receives no credentials and changes no global/default trust manager. Mail-session unit tests and a GreenMail handshake regression verify isolation. |
| 14 | Java sensitive log, PollThrottleService:61 | Hardening: the internal UUID is a DB concurrency lease, not an authentication credential. Nevertheless remove lease identifiers and the raw exception from warnings; test their absence. |
| 15 | Java log injection, ExtensionAuthFilter:70 | Confirmed: rejected-user warnings contain decoded request paths. Remove request-derived fields, retaining only server-derived numeric IDs. |
| 16 | Java log injection, ExtensionAuthFilter:55 | Confirmed: invalid-token warnings contain decoded request paths. Use a fixed event message. |
| 17 | Java log injection, ExtensionAuthFilter:63 | Confirmed: missing-session warnings contain decoded request paths. Remove request-derived fields. |
| 18 | Java log injection, ExtensionAuthFilter:46 | Confirmed: missing-token warnings contain decoded request paths. Use a fixed event message. All four CR/LF regressions fail against the original implementation. |
| 19 | Java user-controlled bypass, ExtensionAuthFilter:52 | False positive: missing/malformed input throws before authentication. Invalid tokens, deleted sessions and inactive/unapproved users also fail closed, without establishing user context. |
| 20 | Java user-controlled bypass, ConfigBackupService:99 | False positive: missing request or risk acknowledgment rejects before any secret-management/snapshot work; negative tests verify no interactions. |
| 21 | Java user-controlled bypass, ConfigBackupService:99 | False positive: reauthentication status comes from the server, not the request. Unsatisfied fresh authentication throws before decryption; HTTP access separately requires admin authorization. |
| 22 | Java user-controlled bypass, GoogleOAuthResource:88 | False positive: missing/blank state rejects before any OAuth service call. Valid state uses the state-bound exchange service. |
| 23 | Java user-controlled bypass, MicrosoftOAuthResource:107 | False positive: missing/blank state returns 400 before any OAuth service call, even with a source ID. Invalid/expired state fails closed too. |

The certificate test connects to real GreenMail IMAPS with an untrusted
self-signed certificate, verifies handshake rejection, runs the tolerant
unauthenticated probe, and verifies that credential-bearing connections still
reject that certificate. Changing the probe to hide the alert could incorrectly
miss TLS support; sharing its trust manager with authentication would be unsafe.

## Regression and coverage boundaries

Use `scripts/validation/changed-coverage.py` for disjoint backend unit/integration
coverage and Vitest/V8 for the admin UI. GreenMail, CDI, PostgreSQL and packaged
runtime execution must remain separate from unit-only coverage. Node/V8 measures
changed public-site executable lines separately: the standard frontend report
only includes `admin-ui/src`. DOM tests import the actual site module with jsdom.

Python helper tests exercise output refusal, public-file allowlisting, hostile
repository metadata and release contracts. Timing probes support triage; they
are not flaky timing assertions in the regression suite. No production Python
behavior was changed to hide alerts. No test uses real operator/mailbox data.

Full changed-code coverage means executed added lines and instrumented branches,
not proof of every possible input, browser, mail provider or absence of flaws.

Validation on this candidate passed 738 backend unit tests, 83 integration
tests, 34 packaged runtime tests, 695 frontend tests, 81 Chromium tests and
11 Firefox tests. Both public-site DOM regressions passed. The changed-code
gate measured 7/7 Java executable lines (no new branches), 8/8 admin UI lines
and 16/16 branches, plus a separate Node/V8 result of 4/4 public-site lines
(no new branches). Whole-file/site and whole-project coverage are not 100%.
Nine release-helper tests, workflow/certificate checks, tracked-source privacy
checks and Gitleaks also passed. Upstream CodeQL and CI remain required before
merging; these local results do not substitute for them.

## 0.8.4 release notes

- Render public configuration-generator folder names as literal text.
- Prevent forged extension authentication log entries and reduce internal lease
  information in warning logs.
- Correct provider display labels for unrelated mail host names.
- Add fail-closed OAuth, encrypted-backup and certificate-trust boundary tests.
- Document repository security governance and private vulnerability reporting.

Backend and admin UI versions are aligned at 0.8.4. Extension runtime code is
unchanged and retains its independent 0.2.1 version; package and test both
extensions for this release. Publish only from reviewed, merged, signed source
after required checks and independent code-owner approval. Preparing a version
does not authorize bypassing review or releasing unmerged fixes. See
[repository security governance](REPOSITORY_SECURITY.md).
