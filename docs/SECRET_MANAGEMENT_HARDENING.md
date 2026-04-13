# Stronger Secret Management Design

This document turns InboxBridge's current encrypted-at-rest secret handling into
a concrete design for stronger deployments where possession of the database plus
the normal `.env` file should not be sufficient to decrypt UI-managed secrets.

It is intentionally written as a design target, though the repository now
implements the first local provider-abstraction step and rotation-friendly
local key metadata described later in this document.

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
6. Add split-key mode after the provider abstraction is proven stable

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

- a deployment policy flag may disable new env-managed mailbox credentials
  entirely while still allowing generic config in `.env`

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
SECRET_PROVIDER_SPLIT_PRIMARY_MODE=LOCAL_WRAPPED
SECRET_PROVIDER_SPLIT_SECONDARY_MODE=OPENBAO_TRANSIT
...
```

These names are illustrative. Final naming should stay compact and operator
friendly.

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
- new writes store `LOCAL:<keyId>` metadata
- older plain local key ids such as `v1` still decrypt as legacy local records
- local-key rotation can keep previous keys readable through
  `SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS`
- admin visibility has started through `/api/admin/secret-management`, which
  reports active mode, configured legacy key ids, and stored key-version usage

### Phase 3: OpenBao / Vault Transit support

- add transit-backed unwrap/wrap support
- add startup/provider health validation
- allow migration from `LOCAL` to `OPENBAO_TRANSIT` or `VAULT_TRANSIT`

### Phase 4: UI and operational tooling

- add admin visibility into the active secret mode
- add safe rewrap / re-encryption workflow
- add provider migration diagnostics

### Phase 5: Split-key mode

- add advanced dual-trust-domain support
- define recovery procedures and failure handling

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
