# Stronger Secret Management Design

This document turns InboxBridge's current encrypted-at-rest secret handling into
a concrete design for stronger deployments where possession of the database plus
the normal `.env` file should not be sufficient to decrypt UI-managed secrets.

It is intentionally written as a design target, though the repository now
implements the first local provider-abstraction step and rotation-friendly
local key metadata described later in this document. The repository now also
implements a deployment policy that can disable env-managed mailbox secrets
(`SECURITY_ALLOW_ENV_MANAGED_MAILBOX_SECRETS=false`), which makes InboxBridge
ignore `MAIL_ACCOUNT_*` source definitions and the `.env` Gmail refresh-token
fallback while still allowing normal bootstrap configuration in `.env`.
The admin-side bulk re-encryption workflow now also supports a configurable
cooldown window plus a server-controlled immediate-execution override intended
only for testing.

## Why This Exists

InboxBridge already encrypts UI-managed secrets in PostgreSQL when
`SECURITY_TOKEN_ENCRYPTION_KEY` is configured.

That protects:

- UI-managed mailbox passwords
- UI-managed OAuth refresh tokens
- UI-managed shared OAuth client secrets
- browser-extension auth/session secrets stored by the backend

It does not fully protect against an attacker or operator who can read both:

- the PostgreSQL database
- the `.env` file or equivalent deployment secret bundle

In the current model, that pair is enough to recover the active encryption key.

This design introduces an optional stronger mode for operators who want a
higher security bar while keeping the current self-hosted deployment model and
the ability for InboxBridge to run unattended.

## Goals

- Keep the current default encrypted-at-rest behavior available for simple
  self-hosted deployments.
- Add an optional hardened mode where database theft plus `.env` theft is not
  enough to decrypt UI-managed secrets.
- Prefer free or open-source-friendly options first.
- Keep unattended polling possible.
- Preserve existing backend ownership of encryption, OAuth, mailbox access, and
  secret handling.
- Make the stronger mode additive and migration-friendly.
- Reduce the need to keep mailbox credentials directly in `.env`.
- Make destructive secret-rotation actions resilient against a hijacked admin
  session by allowing delayed execution and clearer operator verification.

## Non-Goals

- Guarantee safety after full live-runtime compromise. If the server is running
  unattended, some runtime principal must still be able to decrypt secrets.
- Use passkeys directly as unattended mailbox-decryption keys. Passkeys are
  good for user authentication, not for daemon-side decryption without user
  presence.
- Treat Google Drive, OneDrive, Dropbox, or similar consumer file stores as a
  primary key-management system.
- Encrypt every configuration value in `.env`. The hardened design focuses on
  sensitive application secrets, not generic operational toggles.

## Threat Model

This design primarily improves resistance against:

- offline PostgreSQL database theft
- offline backup theft
- accidental exposure of `.env` in logs, archives, or support bundles
- operators keeping sensitive mailbox credentials in plaintext environment
  variables for long periods

It does not fully solve:

- full compromise of the running InboxBridge process
- compromise of the external KMS / Vault account used by the deployment
- compromise of both trust domains in a split-key configuration

## Current Baseline

Today InboxBridge has one practical secret-management mode:

1. A locally configured symmetric encryption key
2. Stored in `.env` through `SECURITY_TOKEN_ENCRYPTION_KEY`
3. Used by the backend to encrypt and decrypt UI-managed secrets before they
   reach PostgreSQL

This remains the recommended default for:

- local development
- simple single-host deployments
- operators who do not want to run a separate key service

## Target Modes

InboxBridge should support three security tiers.

### Mode A: Local Key Mode

This is the current model.

Characteristics:

- `SECURITY_TOKEN_ENCRYPTION_KEY` is required
- the backend encrypts secrets locally with AES-GCM
- PostgreSQL stores ciphertext plus key metadata
- the deployment is self-contained

Trade-offs:

- simplest setup
- strongest compatibility
- database plus `.env` is enough to decrypt secrets

### Mode B: External Transit / KMS Mode

In this mode, InboxBridge does not hold the active data-encryption key in
plaintext `.env`.

Instead, it asks an external service to:

- encrypt a generated data key
- unwrap a stored data key
- or directly encrypt/decrypt secret payloads

Recommended providers:

1. OpenBao Transit
2. HashiCorp Vault Transit
3. Cloud KMS products for operators who already accept a cloud dependency

Characteristics:

- the database stores ciphertext and key metadata
- `.env` stores only the credentials needed to reach the external key service
- the actual cryptographic root key stays outside InboxBridge
- database theft plus `.env` theft is no longer sufficient by itself if the
  external key service remains uncompromised

Trade-offs:

- materially stronger than Mode A
- keeps unattended operation
- adds operational complexity and service dependency

### Mode C: Split-Key Hardened Mode

This is the highest target tier for self-hosted deployments that want a second
trust boundary beyond a single `.env` or a single external secret provider.

In this mode, decrypting InboxBridge-managed secrets requires two inputs:

- one key fragment or unwrap capability available to the application runtime
- one second fragment or wrapped-key authority stored outside the main app host

Practical shapes:

- local wrapped data key + external Vault/OpenBao Transit unwrap
- local encrypted root fragment + external KMS fragment
- operator-supplied recovery fragment for disaster recovery or manual rotation

Characteristics:

- the app can still run unattended
- offline possession of only PostgreSQL plus `.env` is still insufficient
- the second trust domain must remain available or recoverable

Trade-offs:

- strongest self-hosted design
- hardest to operate
- should be optional and clearly labeled as advanced mode

## Recommended Direction

InboxBridge should implement the hardened roadmap in this order:

1. Reduce reliance on env-managed mailbox credentials
2. Add a pluggable secret-provider abstraction
3. Support OpenBao Transit first
4. Support HashiCorp Vault Transit second
5. Add a generic cloud-KMS-backed envelope option
6. Extend split-key mode beyond the first local-plus-transit implementation

That sequence keeps the first version realistic while still aligning with the
stronger long-term security goal.

## Design Decisions

### 1. Keep `.env` for bootstrap and runtime configuration

InboxBridge should continue to use `.env` for normal deployment settings such
as:

- database connectivity
- public URL / hostname settings
- TLS bootstrap settings
- poller defaults
- feature toggles

The stronger design is not an attempt to eliminate `.env` entirely.

### 2. Reduce or phase out plaintext mailbox secrets in `.env`

InboxBridge should de-emphasize and eventually deprecate `.env` for sensitive
mailbox account definitions that can already be managed in the UI.

That means:

- keep `.env` support for general deployment configuration
- keep `SECURITY_TOKEN_ENCRYPTION_KEY` support for the simple/default mode
- stop treating `.env` as the preferred place for routine mailbox credentials
- prefer the admin UI plus encrypted database storage for source and
  destination mailbox configuration

Target product guidance:

- `.env` remains a bootstrap surface
- mailbox credentials belong in the UI unless the operator explicitly chooses
  an env-managed account

Future hardening option:

- a deployment policy flag may disable env-managed mailbox credentials
  entirely while still allowing generic config in `.env`

Current implementation status:

- this repository now includes `SECURITY_ALLOW_ENV_MANAGED_MAILBOX_SECRETS`
  (default `true`)
- when set to `false`, InboxBridge ignores `MAIL_ACCOUNT_*` source definitions
  and the `.env` `GMAIL_REFRESH_TOKEN` fallback
- the admin security panel surfaces whether that policy is enabled and whether
  any env-managed mailbox secret material is still configured
- system-Gmail runtime helpers now fail with an explicit policy-aware message
  instead of silently attempting to use a blocked `.env` refresh-token
  fallback, and dashboard token-storage summaries can distinguish `blocked by
  policy` from truly `not configured`

### 2a. Gate bulk secret re-encryption behind a cooldown window

Bulk re-encryption is a high-impact administrative action. If an attacker
temporarily gains an admin session, an immediate rotation could let them try to
replace the active key path and re-encrypt stored secrets under attacker-
controlled material.

InboxBridge should therefore support a deployment-level cooldown between:

- the moment an admin requests re-encryption
- and the moment the server actually executes it

Current implementation status:

- `SECURITY_SECRET_REENCRYPTION_COOLDOWN` controls that delay
- the backend stores the queued request and executes it only after the
  configured time passes
- the admin modal now shows backend-verified readiness requirements and any
  already-pending request
- `SECURITY_SECRET_REENCRYPTION_ALLOW_IMMEDIATE_OVERRIDE` exists only as a
  server-side testing escape hatch so manual/local validation can bypass the
  delay

Operational guidance:

- keep the immediate override disabled in real deployments
- record the scheduled execution time and the resulting verification summary in
  operator recovery notes
- retire legacy keys only after the post-run verification says every stored
  record is now decryptable through the active provider/key path

### 2b. Verify requirements and outcomes explicitly

The re-encryption dialog should not rely only on checkbox acknowledgements.
Instead, the backend should evaluate whether the action is currently safe to
start.

Current implementation status:

- `/api/admin/secret-management` now reports backend-verified requirements for
  the re-encryption workflow
- the modal disables confirmation when blocking requirements are unmet
- each requirement now carries backend-provided remediation steps, related
  server configuration references, and optional focus targets so the operator
  can jump directly to the key-status, key-usage, pending-request, or session
  verification area that needs attention
- `/api/admin/secret-management` also now reports provider-component
  diagnostics for the active trust boundaries, and the admin section surfaces
  them as separate readiness cards so operators can distinguish a broken local
  key path from a broken transit secondary before rotating secrets
- that same status payload now also includes a provider-aware rotation-plan
  preview that classifies the next operator action as local-key rotation,
  transit-key migration, split-key rotation, provider migration, already
  aligned, no encrypted records, or blocked legacy-key recovery
- `/api/admin/secret-management` now also includes a backend dry-run preview
  of the bulk rewrite itself, with per-area and total counts for records that
  would be updated, secret values that would be rewritten, and whether those
  updates would use full plaintext re-encryption or metadata rewrap
- queued and completed secret re-encryption requests now persist those
  snapshots too, so the admin UI can still show the queued preview plus the
  latest execution totals, per-area result breakdown, follow-up cleanup
  counts, and verification summary after a page reload
- `GET /api/admin/secret-management/report` now exports that latest
  secret-management snapshot as a downloadable JSON report, including the
  current status payload plus an export timestamp so operators can archive the
  active provider state, key-usage view, and persisted queued/completed
  re-encryption evidence before retiring legacy key material
- `/api/admin/secret-management` now also includes backend-verified legacy-key
  retirement requirements so the admin UI can tell operators whether it is
  currently safe to remove old key ids, transit credentials, or other legacy
  provider material from the deployment
- the admin UI now exposes that retirement review in its own dialog, with the
  current key-usage summary, latest request status, backend retirement checks,
  and an explicit operator procedure of export report -> remove obsolete legacy
  config -> redeploy -> re-check status before considering legacy material
  fully retired
- `POST /api/admin/secret-management/retirement-review` now persists an
  operator-reviewed audit snapshot in `system_secret_retirement_review`,
  capturing the reviewer identity, active key/provider summary, remaining
  blocking retirement checks, and the exact status payload reviewed at that
  time
- `/api/admin/secret-management` now also returns the latest recorded
  retirement review plus a short recent-review history so the UI can show when
  an operator last recorded a backend-verified retirement decision
- `POST /api/admin/secret-management/retirement-complete` now performs the
  post-cleanup verification step after the operator removes legacy
  provider/key material and redeploys; the backend compares the live
  provider/key summary against the latest recorded retirement review, persists
  the verifier identity plus any still-unsatisfied checks, and stores a second
  completion snapshot so the deployment can prove cleanup actually survived a
  restart
- the current implementation now also detects provider-side transit key
  rollovers for active `OPENBAO_TRANSIT`, `VAULT_TRANSIT`, and split-key outer
  envelopes; when only the provider's internal key version is stale, the
  status reports a metadata-rewrap plan and the bulk action can refresh the
  outer transit ciphertext without decrypting plaintext
- bulk re-encryption results now distinguish how many secrets were processed
  through full plaintext re-encryption versus metadata rewrap, so operators
  can audit what kind of rotation actually ran
- after completion, the UI shows verification messages plus a list of items the
  operator should save before retiring any legacy key material
- the retirement dialog now also exposes the latest post-cleanup completion
  verification, including the last verified timestamp, verifier identity,
  completion status, and a dedicated `Verify post-cleanup completion` action

### 2c. Require fresh step-up verification for sensitive browser actions

Cooldowns help against rushed misuse of a stolen admin session, but they do not
prove that the person currently clicking the button is still the legitimate
administrator.

InboxBridge should therefore also require a recent step-up verification for the
browser session that requests secret re-encryption.

Current implementation status:

- `SECURITY_SECRET_REENCRYPTION_REAUTHENTICATION_TTL` controls how long a
  sensitive-session verification remains valid (default `PT10M`)
- `POST /api/admin/secret-management/re-auth/password` lets the current admin
  re-verify the browser session with their current password
- `POST /api/admin/secret-management/re-auth/passkey/options` and
  `POST /api/admin/secret-management/re-auth/passkey/verify` let the same
  browser session satisfy that step-up check with a passkey
- the backend stores the verification timestamp on the current `user_session`
  row and exposes the remaining validity window through
  `/api/admin/secret-management`
- scheduled re-encryption requests require the step-up check only when the
  request is queued; the later cooldown-triggered execution does not need an
  interactive browser session

Operational guidance:

- keep the step-up TTL short enough that an abandoned admin browser session
  cannot silently schedule high-impact secret changes much later
- prefer passkey verification when available, but keep the current-password
  path as a recovery-friendly fallback for admins who have not enrolled a
  passkey yet
- document the expected TTL and the permitted step-up methods in the operator
  runbook for the deployment

This keeps the operator workflow explicit:

1. Confirm the backend says the current provider/key path is healthy.
2. Confirm all stored records are still decryptable.
3. Queue or run the re-encryption.
4. Save the resulting verification output.
5. Only then remove legacy key material.

### 3. Use envelope encryption internally

InboxBridge should standardize on envelope encryption for UI-managed secrets.

Logical model:

1. Generate a per-record or per-secret-group data encryption key (DEK)
2. Encrypt the payload with AES-GCM locally
3. Wrap the DEK through the configured root provider
4. Store:
   - ciphertext
   - nonce
   - AAD context
   - wrapped DEK or key reference
   - key version metadata
   - provider metadata

Benefits:

- consistent local payload format across modes
- easier provider changes later
- easier key rotation
- cleaner split between payload encryption and master-key custody

### 4. Introduce a pluggable secret-provider abstraction

The backend should add a narrow interface, owned by the security package, for
example:

- `SecretKeyProvider`
- `KeyEnvelopeService`
- `WrappedKeyService`

Core operations:

- generate or request a DEK
- wrap a DEK
- unwrap a DEK
- report provider identity and key version
- perform health checks

Possible implementations:

- local static key provider
- OpenBao Transit provider
- Vault Transit provider
- cloud KMS envelope provider

The rest of the app should not know which provider is active.

### 5. Preserve explicit metadata on every encrypted record

Every ciphertext-bearing record should include enough metadata for future
rotation and migration.

Recommended stored metadata:

- encryption provider type
- key ID or key label
- wrapping mode
- schema version
- AAD context version
- encrypted-at timestamp

That keeps migrations explicit and auditable.

### 6. Treat passkeys as auth, not as unattended encryption keys

InboxBridge should not attempt to derive mailbox-decryption keys directly from
user passkeys.

Reasons:

- passkey private keys stay on the authenticator or platform wallet
- unattended polling would still need a server-side decrypt path
- user-presence expectations conflict with daemon execution
- platform passkey portability is designed for authentication ceremonies, not
  for long-term application key escrow

Passkeys should continue to protect sign-in and privileged UI actions, not
replace server-side secret management.

### 7. Do not use Google Drive or OneDrive as the primary key store

Per-user cloud-drive storage sounds attractive because it appears to keep the
operator from seeing a user's secrets, but it is not a good primary design for
InboxBridge.

Problems:

- they are file stores, not KMS systems
- unattended mailbox polling would still need automated access
- if the runtime can fetch the decryption file automatically, a compromised
  runtime can usually fetch it too
- token refresh, offline access, and revocation become fragile
- user availability or cloud-drive issues could block polling entirely

Cloud-drive-based export or escrow could exist as an optional backup or
recovery mechanism later, but not as the primary secret-custody design.

## Preferred Providers

### OpenBao Transit

Recommended first-class open-source option.

Why:

- open-source-first fit
- Vault-compatible direction
- good match for self-hosted operators
- supports the transit/encrypt-decrypt model InboxBridge needs

Recommended use:

- primary recommended hardened mode for self-hosted operators
- especially for homelab or VPS deployments that want stronger protection
  without depending on a closed-source cloud

### HashiCorp Vault Transit

Recommended compatibility target.

Why:

- mature transit model
- common in self-hosted and enterprise environments
- many operators already know it

Important note:

- Transit is available in Vault Community Edition, so InboxBridge should not
  treat Vault Transit as enterprise-only

### Cloud KMS

Useful when the operator already has a cloud dependency or does not want to run
Vault/OpenBao.

Candidate providers:

- AWS KMS
- Google Cloud KMS
- Azure Key Vault / Managed HSM
- Oracle Cloud Vault

Recommended product positioning:

- supported, but not the project's first recommendation
- useful for operators who already have an account and trust that cloud

Practical free-tier note:

- Oracle Cloud is the most plausible long-lived free hosted option for some
  self-hosters
- other cloud KMS products may offer credits, trials, or limited free usage,
  but should not be presented as permanently free by default

## Proposed Configuration Model

InboxBridge should replace the single implicit encryption mode with an explicit
secret-provider configuration block.

Conceptual example:

```dotenv
SECRET_PROVIDER_MODE=LOCAL
SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
```

```dotenv
SECRET_PROVIDER_MODE=OPENBAO_TRANSIT
SECRET_PROVIDER_OPENBAO_URL=https://openbao.example.internal:8200
SECRET_PROVIDER_OPENBAO_TOKEN=<operator-supplied-token>
SECRET_PROVIDER_OPENBAO_MOUNT=transit
SECRET_PROVIDER_OPENBAO_KEY=inboxbridge
```

```dotenv
SECRET_PROVIDER_MODE=VAULT_TRANSIT
SECRET_PROVIDER_VAULT_URL=https://vault.example.internal:8200
SECRET_PROVIDER_VAULT_TOKEN=<operator-supplied-token>
SECRET_PROVIDER_VAULT_MOUNT=transit
SECRET_PROVIDER_VAULT_KEY=inboxbridge
```

```dotenv
SECRET_PROVIDER_MODE=SPLIT_KEY
SECRET_PROVIDER_SPLIT_SECONDARY_MODE=OPENBAO_TRANSIT
SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
SECRET_PROVIDER_OPENBAO_URL=https://openbao.example.internal:8200
SECRET_PROVIDER_OPENBAO_TOKEN=<operator-supplied-token>
SECRET_PROVIDER_OPENBAO_MOUNT=transit
SECRET_PROVIDER_OPENBAO_KEY=inboxbridge
```

## Backend Changes

The backend should gain a dedicated secret-management boundary under
`dev.inboxbridge.service.security`.

Suggested components:

- `SecretKeyProvider`
- `EnvelopeEncryptionService`
- `SecretProviderHealthService`
- `SecretMigrationService`
- `SecretManagementModeResolver`

The existing `SecretEncryptionService` can either evolve into that abstraction
or become the local-key implementation behind it.

Current repository status:

- `LOCAL` mode is implemented with AES-GCM plus legacy-key rotation support
- `OPENBAO_TRANSIT` and `VAULT_TRANSIT` are now implemented through the
  Vault-compatible transit HTTP API for provider health plus encrypt/decrypt
  operations
- `SPLIT_KEY` now has an initial production implementation that uses the local
  AES-GCM key as the inner layer and an OpenBao/Vault transit provider as the
  outer layer while preserving the current database schema

Responsibilities:

- encrypt/decrypt UI-managed secrets
- attach and validate AAD context
- expose provider-specific health status
- support rewrap and rotation workflows
- fail closed when the configured provider is unavailable for writes

## UI and Admin Changes

The admin UI should eventually expose the active secret-management mode in a
clear but implementation-neutral way.

Recommended UX:

- keep normal mailbox and OAuth screens wording neutral, for example
  `Encrypted storage`
- add a deployment-level security/settings area that shows:
  - active secret mode
  - provider health
  - current key label / version
  - whether legacy local-key mode is still in use
  - whether env-managed mailbox credentials are still enabled

Potential admin actions:

- rotate wrapped-key metadata
- re-encrypt all stored secrets under a new provider or key version
- revoke all extension sessions after a secret-provider migration if needed
- run a provider connectivity check

## Migration Strategy

### Phase 1: Document and recommend

- add this design doc
- keep current runtime behavior unchanged
- recommend UI-managed secrets over `.env` mailbox secrets

### Phase 2: Provider abstraction

- introduce the secret-provider interface
- keep `LOCAL` mode as the default implementation
- ensure ciphertext metadata is provider-aware

Current repository status:

- done for the local provider path
- provider mode is now explicit through `SECRET_PROVIDER_MODE`, defaulting to
  `LOCAL`
- new writes store `LOCAL:<keyId>` metadata
- older plain local key ids such as `v1` still decrypt as legacy local records
- local-key rotation can keep previous keys readable through
  `SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS`
- backend provider resolution now reports mode-aware health so the admin
  status endpoint can fail closed instead of silently falling back to local
- admin visibility has started through `/api/admin/secret-management`, which
  reports active mode, provider health, provider-component diagnostics,
  configured legacy key ids, stored key-version usage, and a provider-aware
  rotation-plan preview
- the admin UI now surfaces that status inside its own `Administration ->
  Secret management` section, with a dedicated high-friction re-encryption
  modal that explains prerequisites, risks, acknowledgements, and optional
  follow-up cleanup before the action can run
- the first concrete rotation action now exists through
  `/api/admin/secret-management/re-encrypt`, which rewrites database-stored
  encrypted secrets under the active local key version

### Phase 3: OpenBao / Vault Transit support

- add transit-backed unwrap/wrap support
- extend the current explicit mode/health scaffolding into real OpenBao and
  Vault transit connectivity checks plus cryptographic operations
- allow migration from `LOCAL` to `OPENBAO_TRANSIT` or `VAULT_TRANSIT`

### Phase 4: UI and operational tooling

- add admin visibility into the active secret mode
- add safe rewrap / re-encryption workflow
- add provider migration diagnostics
- current status:
  - active mode visibility exists
  - backend-verified re-encryption readiness exists
  - cooldown plus step-up verification exists
  - provider-component diagnostics exist
  - backend-assessed migration-target previews now exist for `LOCAL`,
    `OPENBAO_TRANSIT`, `VAULT_TRANSIT`, and `SPLIT_KEY`, so operators can
    compare readiness, writable state, active key metadata, config references,
    and remediation steps for each supported mode before they switch
    `SECRET_PROVIDER_MODE`
  - backend-generated migration checklists now exist for each supported target
    mode, surfacing preflight checks plus before-switch, switch, and
    post-switch steps in the admin UI without trying to mutate deployment
    configuration from the browser
  - provider-aware rotation-plan preview exists
  - metadata-only rewrap now exists for active transit-provider key rollovers
    where the stored InboxBridge key target has not changed and only the
    provider-side ciphertext version is stale
  - full plaintext re-encryption is still required for local-key changes,
    provider changes, split-key local-inner-key changes, and any migration
    that changes the stored InboxBridge key target itself

### Phase 5: Split-key mode

- add advanced dual-trust-domain support
- define recovery procedures and failure handling
- current status:
  - backend-generated recovery checklists now exist for failed re-encryption
    runs and verification-warning completions, so operators can preserve the
    last-known-good trust path, capture the required evidence, and follow an
    explicit rollback-and-validation procedure from the admin UI
  - recovery acknowledgement is now persisted as its own operator review
    snapshot, and InboxBridge blocks re-encryption retries plus legacy-key
    retirement progression until the latest failed or warning-state request has
    a recorded recovery review
  - cooldown-window re-encryption requests now pause at a server-tracked
    approval checkpoint once the cooldown elapses, so InboxBridge will not run
    the queued migration until an operator explicitly approves it through the
    admin UI with a fresh step-up verified browser session
  - queued cooldown-window requests now persist the exact target they were
    reviewed against; if the active mode/provider/key target changes before
    approval or execution, InboxBridge blocks the stale request and forces the
    operator to submit a new one against the new target
  - that stale-request block is intentionally fail-closed for local-key
    rotations too: if the previous active local key is no longer available as a
    legacy decrypt path after the target changes, InboxBridge keeps
    re-encryption readiness blocked until the operator restores decryptability
    for the still-encrypted older records
  - the recovery flow remains read-only by design: InboxBridge does not try to
    mutate `SECRET_PROVIDER_MODE` or infer a rollback target automatically,
    because that decision must stay anchored in the operator's recorded
    last-known-good deployment state

### Phase 6: Deprecate plaintext env mailbox credentials

- warn operators when `.env` still contains mailbox credentials
- optionally add a hardening flag to disable env-managed mailbox secrets
- keep generic `.env` configuration support

## Failure Semantics

The stronger modes should fail closed.

Rules:

- if the provider is unavailable, InboxBridge must not silently fall back to a
  weaker local mode
- writes that require encryption should fail explicitly
- reads that require decryption should fail explicitly
- health endpoints and admin UI should surface the provider problem clearly

Optional operational choice:

- polling can remain running for already-resolved in-memory secrets during a
  brief outage, but no new decrypts should be attempted silently once the
  provider is unavailable

That behavior should be explicit and documented if adopted.

## Rotation Model

The design should support two kinds of rotation.

### Metadata Rewrap

- unwrap the stored DEK with the old root provider
- rewrap it with the new root provider or key version
- payload ciphertext stays unchanged

Use when:

- rotating a transit key
- moving from one key label to another

### Full Re-encryption

- decrypt payload
- generate a new DEK
- re-encrypt payload
- wrap with the new provider

Use when:

- changing envelope format
- changing AAD rules
- responding to deeper compromise concerns

## Secret Rotation Ownership

InboxBridge should treat secret rotation as three related but distinct
operations.

### 1. User Credential Rotation

This happens when a user changes the underlying secret value itself, for
example:

- a source mailbox password
- a destination mailbox password
- an app password
- an OAuth refresh token
- a shared OAuth client secret stored through the UI

Expected behavior:

- InboxBridge validates and stores the new credential
- the new value is encrypted with the currently active secret-management mode
- the old encrypted payload is replaced

This is the most common day-to-day rotation and should be supported anywhere
users already manage those credentials.

### 2. Encryption-Layer Rotation

This happens when InboxBridge rotates the encryption key, wrapping key, or
external provider used to protect stored secrets.

Examples:

- rotating `SECURITY_TOKEN_ENCRYPTION_KEY` in local mode
- rotating the active OpenBao / Vault transit key version
- moving from local mode to transit-backed mode
- moving from one cloud KMS key to another

Expected behavior:

- InboxBridge performs either metadata rewrap or full re-encryption
- ciphertext metadata is updated accordingly
- the underlying mailbox or OAuth credential value does not change

This is a deployment-level security operation and should be treated as
admin-only.

### 3. Session And Derived-Token Rotation

This happens when InboxBridge revokes secrets or tokens derived from the
protected secret layer, for example:

- browser-extension sessions
- remote-control sessions
- cached OAuth access tokens
- other revocable session material tied to previously trusted secret state

Expected behavior:

- affected sessions or derived tokens are revoked
- clients must refresh or sign in again as appropriate
- revocation can be chained automatically after sensitive key-management
  operations

This is also a deployment-level operation, though some narrow self-service
re-authentication flows can remain user-scoped.

## Rotation UX Direction

InboxBridge should eventually expose rotation in two layers.

### User-Scoped Rotation

Normal users should be able to rotate their own mailbox or OAuth credentials by
editing and saving the relevant configuration again.

That covers:

- changing mailbox passwords
- replacing app passwords
- reconnecting OAuth mailboxes
- replacing user-managed shared provider credentials where permitted

This is credential rotation, not key-management rotation.

### Admin-Scoped Rotation

Administrators should eventually get a deployment-level security area that can
trigger broader secret-management workflows.

Recommended actions:

- `Rotate encrypted secret wrapping`
- `Re-encrypt all stored secrets`
- `Revoke browser extension sessions`
- `Revoke remote control sessions`
- `Invalidate cached OAuth access tokens`
- `Run secret-provider health check`

Those operations should be clearly labeled because they affect the deployment
as a whole rather than one user's mailbox settings.

## Recommended Rotation Semantics

The rotation workflow should distinguish between:

- `Rewrap only`
  - faster
  - lower risk
  - good for provider/key-version changes where the payload format stays valid
- `Full re-encryption`
  - slower
  - more invasive
  - appropriate when changing encryption format, AAD rules, or responding to a
    stronger compromise concern

When a security-sensitive rotation happens, InboxBridge should also allow
optional follow-up revocation of derived trust material, especially:

- browser-extension tokens
- remote sessions
- cached OAuth access tokens

That gives operators a clear way to combine key rotation with session cleanup.

Current repository status:

- the admin re-encryption workflow already supports those optional follow-up
  cleanup actions during `POST /api/admin/secret-management/re-encrypt`
- operators can now choose, per run, whether InboxBridge also revokes
  browser-extension sessions, revokes `/remote` sessions, and clears cached
  OAuth access tokens after the stored secrets are rewritten
- this is still a full re-encryption workflow rather than a metadata-only
  rewrap flow

## Operational Recommendations

### For most self-hosted users

Use:

- current local-key mode
- minimal `.env`
- UI-managed mailbox secrets

### For security-conscious self-hosters

Use:

- OpenBao Transit
- UI-managed mailbox secrets only
- no mailbox credentials in `.env`

### For advanced or enterprise-style operators

Use:

- Vault Transit or cloud KMS
- optional split-key mode later
- stricter audit and key-rotation practices

## Open Questions

- Should env-managed mailbox definitions be deprecated globally, or only
  disabled behind an opt-in hardened mode?
- Should the first transit-backed implementation wrap one deployment-wide DEK
  or one DEK per secret-bearing record?
- Should provider health block startup entirely, or only block secret-bearing
  features until recovery?
- How much admin UI should be exposed to non-admin users about the active
  secret mode?

## Recommended Product Wording

When this design becomes implementation work, InboxBridge should describe it in
plain language:

- `Default encrypted storage`: local encryption key managed by the deployment
- `Hardened encrypted storage`: encryption keys protected by an external key
  service
- `Advanced split-key mode`: decryption requires more than one trust boundary

That keeps the UI operator-friendly while preserving accurate documentation in
the technical docs.
