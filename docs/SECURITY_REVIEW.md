# Source security review — 2026-10-25

This review combines automated source analysis, manual trust-boundary review,
dependency/image scans, authenticated API regression tests against PostgreSQL,
and anonymous checks against the rebuilt HTTPS deployment. It is a dated
assessment, not a guarantee that the software has no vulnerabilities.

## Findings fixed

| Area | Problem | Change and regression evidence |
| --- | --- | --- |
| OAuth authorization | Public Google/Microsoft exchange endpoints accepted a code without state and selected the shared/default or caller-specified mailbox. This bypassed the authorization performed when starting a flow. | Both require a valid one-time state from an authorized start. Missing state cannot modify credentials. Resource tests, real HTTP tests and deployed anonymous checks cover rejection. |
| Extension authorization | Opening an attacker-supplied handoff link while signed in silently approved that extension connection. PKCE alone did not establish the browser user's consent. | The page requires explicit approval, bound to the exact request and signed-in user, with a warning in all six languages. UI tests verify no completion request is sent before approval. |
| Extension redemption | Concurrent polls could consume the same handoff twice; a second user could overwrite an earlier approval. Anonymous pending state was unbounded. | Completion/redemption are serialized per handoff; ownership cannot change. RFC 7636 challenge/verifier lengths are validated, pending handoffs are capped at 1,000 and expire after five minutes. Race, ownership, malformed input, capacity and expiry tests pass. |
| CAPTCHA replay | Replay detection used the hash of the submitted JSON/base64 text. Reformatting the same valid solution or changing unsigned timing metadata bypassed it; a reservation could also be prematurely removed by a concurrent purge. | Atomically consume the verified, signed nonce until its signed expiry. Require the signature, bound token/cache sizes and reject replay across formatting changes, IPs and simultaneous requests. |
| Client address trust | Any private peer was implicitly trusted and caller-supplied Cloudflare/True-Client-IP headers could override Nginx's address, undermining per-IP throttling and audit attribution. | Trust only configured immediate proxies. Accept literal IPs from Nginx's authoritative address/rightmost forwarded hop; strip alternative headers and replace forwarded-for at Nginx. Tests cover spoofing, private peers, malformed addresses and IPv6. |
| Passkey replay | Concurrent database transactions could load and verify the same ceremony before deletion. | Lock the ceremony row until verification/deletion commits. A two-transaction PostgreSQL test proves only one consumer succeeds. |
| Password storage | PBKDF2-HMAC-SHA256 used 210,000 iterations, below the current 600,000 recommendation. | New hashes use 600,000 iterations. Successful verification upgrades old hashes using a conditional update so it cannot overwrite a concurrent password reset. Legacy verification and both upgrade outcomes have regression tests. |
| Public metadata | Anonymous health responses exposed the total number of imported messages across users. | Return liveness status only. Unit, packaged API and deployed checks verify the response shape. |
| Container trust | The Java and Nginx containers ran as root and every service mounted the directory containing the CA signing key and other services' TLS keys. | Run both applications as unprivileged users with capabilities dropped and privilege escalation disabled. Publish separate leaf-only mounts; only cert-init mounts the CA key. Disposable bootstrap tests check permissions, isolation, certificate verification and repeat runs; deployed checks confirm UIDs and visible files. |
| Network and resource limits | The backend was published directly despite the documented proxy boundary. First-run access also exposed the bootstrap account on all host interfaces. | Publish only the frontend. Fresh `.env.example` installations explicitly select loopback; existing installations without the new setting retain prior ingress behavior. Operators can select a LAN/tailnet bind address. Limit API bodies to 256 KiB and headers to 16 KiB; throttle sensitive authentication routes at Nginx. |
| Release workflow | Release tags and other context values were inserted directly into shell source. Action tags and the Firefox publishing tool were mutable/unpinned. | Pass context through environment variables, validate release tag syntax and pin actions to verified release commit SHAs. Pin web-ext 10.8.0. Tests execute the metadata script with shell substitutions, quotes and newline payloads and verify that no code runs or outputs are injected. |

The password work factor follows the [OWASP Password Storage Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
The existing algorithm/format is retained so upgrading does not lock out users.

## Scan scope and triage

Semgrep 1.182.0 ran the Java, JavaScript, React, OWASP Top Ten and security-audit
rule packs against an explicit production-source snapshot. It executed 227
applicable rules on 632 targets with no parser errors and no ERROR findings.
Tests, dependencies, generated assets, local environment files and certificate
keys were excluded. This is community-rule static analysis, not a complete
taint analysis or proof of absence. Rule packs/advisories can change on rerun.

Ten WARNING findings remain visible and were reviewed without blanket
suppression:

- Four Nginx host-header warnings: the reverse proxy preserves the browser's
  authority/port for same-origin validation and the unexposed HTTP listener's
  redirect. Host headers are untrusted; they do not authorize API access or
  select a mailbox. Canonical OAuth redirects and WebAuthn origins remain
  operator-configured, unsafe browser writes still require the session-bound
  CSRF token, and TLS hostname verification remains required. Configure public
  hostnames/certificates correctly; do not treat the forwarded authority as an
  authorization credential.
- Five TLS/socket warnings in `SourceTransportSecurityService`: this is an
  unauthenticated capability probe that sends only IMAP/POP3 discovery commands,
  never a password or token. Accepting a probe certificate permits discovering
  TLS on a self-signed server; actual mailbox sessions enforce the separate
  TLS/hostname/certificate policy. The generic `TLS` context uses the Java
  runtime's enabled protocols; the name does not enable SSLv3/TLS 1.0.
- One RSA transformation warning in `ConfigBackupService`: `RSA/ECB/OAEP...`
  names the JCA RSA transformation, not AES-ECB encryption. Backups use a random
  AES-256-GCM data key and nonce, wrapped using RSA-OAEP with explicit SHA-256
  parameters. Replacing it with unauthenticated symmetric encryption would
  weaken the design.

Manual review also covered role and mailbox ownership checks, browser/remote/
extension session boundaries, cookie and CSRF handling, SQL parameter binding,
secret encryption and backups, OAuth callbacks, frontend rendering, extension
HTML escaping, redirects, token storage and command/deserialization patterns.
The existing authenticated endpoint, CSRF, cross-user isolation, secret-provider
and GreenMail suites remain part of validation. No mail protocol behavior was
changed by this review.

## Remaining deployment responsibilities and limits

- Change the initial admin password before allowing LAN/public access. Fresh
  installations copied from `.env.example` explicitly select loopback. Existing
  deployments without `PUBLIC_BIND_ADDRESS` retain their prior all-interface
  binding, so upgrades preserve configured LAN/tailnet access. Set the variable
  explicitly to narrow exposure when desired. The bootstrap account must never be treated as a suitable
  permanent credential.
- Mailbox servers and transit providers are intentional outbound destinations,
  including private/LAN hosts such as local mail bridges. Approved users can
  cause connections to those configured endpoints. Deploy behind appropriate
  network egress rules; private addresses cannot be categorically prohibited
  without breaking supported deployments.
- Nginx's per-IP limits constrain bursts, not a distributed denial of service.
  Keep host resources, ingress controls and account approval appropriate to the
  deployment. If adding another proxy, configure the trusted immediate peers
  and sanitize forwarding headers there; otherwise addresses may collapse to
  that proxy and throttling will be shared.
- In-memory CAPTCHA/handoff state is bounded and safe within the single
  backend instance used by Compose. A future horizontally scaled deployment
  needs shared atomic state before these endpoints can be spread across nodes.
- The upstream Java, Maven, npm, PostgreSQL and OpenBao image/tool findings in
  [the dependency audit](DEPENDENCIES.md) are still open. The Pebble deletion was
  reverted: it is an upstream binary unused by InboxBridge's direct Java
  entrypoint, and removing it was not a demonstrated application vulnerability
  fix. Its four high-severity Go-runtime matches remain reported.
- This review does not claim a live authenticated penetration test of every
  endpoint, browser exploit testing, fuzzing, provider-side Google/Microsoft
  consent validation, or protection against an already compromised host/admin.

## Reproducing checks

```bash
# Does not read local .env files or certificate keys; Docker/network required.
sh scripts/security/scan-source.sh /tmp/inboxbridge-source-semgrep.json
python3 scripts/security/test-workflows.py
python3 scripts/security/test-compose-ports.py
sh scripts/security/test-cert-init.sh

# Requires the local stack at https://localhost:3000 and its public CA certificate.
python3 scripts/security/test-runtime.py
# Also verify the actual operator URL for LAN/tailnet deployments:
python3 scripts/security/test-runtime.py --base-url https://YOUR_HOST:3000

# Java 25 and Docker available; run backend builds sequentially.
mvn -q -Dit.test='*QuarkusIT' verify
cd admin-ui
npm ci
npm run test:coverage
npm run build
```

Run the browser-extension and site checks from `CONTRIBUTING.md` too. The build
workflow now runs packaged backend tests, workflow security checks, disposable
certificate checks, frontend tests/build, both extension suites and site checks.
Report actual test/scan results and unresolved findings when handing off work.

## Full regression revalidation — 2026-10-25

| Check | Final result |
| --- | --- |
| Backend, including GreenMail, PostgreSQL, OpenBao and Vault | 749 passed |
| Packaged Quarkus API checks | 34 passed |
| Frontend on Node 24 LTS | 655 passed in 104 files; coverage and production build passed |
| Chromium / Firefox extensions | 81 / 11 passed |
| Workflow / Compose ingress regressions | 3 / 3 passed |
| Disposable certificate bootstrap and site generator | Passed |
| Deployed runtime boundary | 6 passed through localhost and 6 through the configured Tailscale hostname |

The 1,530 application/extension tests completed without failures or skips.
The extension harness initially omitted the shared `/remote` icon mount; after
including the required fixture, both complete suites passed. No application
change was needed for that harness issue.

Added a PostgreSQL-backed HTTP test for an existing 210,000-iteration password
hash. The original password signs in successfully, the stored hash upgrades,
subsequent sign-in still succeeds and the first authenticated session remains
valid. Existing coverage also exercises settings persistence, browser/remote/
extension sessions, token rotation/revocation, OAuth routing, CSRF, user
isolation, mailbox polling/import/deduplication, admin controls and backups.

Both applications were rebuilt and force-recreated. The original database
volume and configuration counts were preserved: three users, five sources and
one destination. Internal backend/database readiness is UP. Real browser
checks load the main and `/remote` sign-in pages over Tailscale and the main
page over localhost without bypassing certificate checks. Network checks
verify login POST reachability without submitting real account credentials.

One live Sapo source currently receives an authentication rejection. Older
Sapo polling failures are present before these changes; retained records do
not establish their original authentication cause. No mailbox credentials
were changed. Automated mailflow checks do not establish that every live
provider credential is valid. Physical passkey interaction, live provider
consent and a live authenticated user walkthrough remain manual checks.
