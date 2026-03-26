# InboxBridge Context

## Purpose

InboxBridge is a self-hosted bridge that pulls messages from external mailboxes over IMAP or POP3 and imports them into a Gmail mailbox using the Gmail API.

The project is intentionally a starter-oriented service rather than a hosted SaaS product. The goal is to keep the old "one Gmail inbox, many external sources" workflow alive with a small containerized application that remains understandable and hackable.

## Product Goal

The intended user experience is:

1. Keep using external mailboxes such as Outlook, Yahoo, ISP mail, or custom domains.
2. Poll those mailboxes over IMAP(S) or POP3(S).
3. Import new messages into Gmail without SMTP forwarding.
4. Preserve original MIME structure as much as possible.
5. Avoid duplicate imports across polls and restarts.

This avoids the main drawbacks of SMTP auto-forwarding:

- sender reputation damage
- Gmail treating forwarded spam as coming from the source mailbox
- loss of the original "single Gmail inbox" workflow

## Current Technical Stack

- Java 21
- Quarkus 3.32.3
- PostgreSQL 16
- Flyway for schema migration
- Hibernate ORM Panache for persistence
- Angus Mail 2.0.4 for IMAP/POP access
- Java `HttpClient` for Google and Microsoft OAuth / API HTTP calls
- Docker multi-stage build
- Docker Compose for app + database

## Core Architecture

InboxBridge is a single Quarkus application with PostgreSQL as its only durable state store.

High-level ingestion flow:

1. `PollingService` is triggered by the scheduler or a manual REST call.
2. Each enabled source account from config is processed sequentially.
3. `MailSourceClient` fetches candidate messages from the source mailbox.
4. Each message is materialized as raw MIME bytes.
5. `ImportDeduplicationService` checks whether it was already imported.
6. `GmailLabelService` resolves `INBOX`, `UNREAD`, and any custom source label.
7. `GmailImportService` sends the raw MIME to Gmail `users.messages.import`.
8. `ImportDeduplicationService` records the successful import in PostgreSQL.
9. `PollRunResult` summarizes fetched, imported, duplicate, and error counts.

High-level OAuth flow:

1. Helper endpoints build provider-specific authorization URLs.
2. The browser completes the provider consent step.
3. InboxBridge exchanges the authorization code for tokens.
4. If secure token storage is configured, refresh and access tokens are stored encrypted in PostgreSQL.
5. Otherwise the exchange response falls back to returning the refresh token for env-based local setups.
6. Source polling and Gmail import flows reuse the stored refresh token to renew short-lived access tokens automatically.

## Main Architectural Decisions

### 1. Quarkus instead of a larger framework

Quarkus is used for:

- CDI / dependency injection
- REST endpoints
- scheduler integration
- YAML config mapping
- PostgreSQL access
- Flyway migrations
- small container footprint

This keeps the service simple and container-friendly.

### 2. Gmail `messages.import` instead of forwarding

The bridge imports raw MIME messages into Gmail through `users.messages.import`.

Why:

- preserves the original message better than SMTP forwarding
- avoids sender reputation problems
- keeps attachments and headers intact
- better matches what Gmail mail fetching used to do

Current import behavior:

- `internalDateSource=dateHeader`
- always applies `INBOX`
- always applies `UNREAD`
- optionally applies a custom per-source label
- can set `neverMarkSpam`
- can set `processForCalendar`

### 3. Env-first runtime config with DB-backed durable state

Mailbox sources are still configured through environment variables or `.env`, using indexed keys such as:

- `BRIDGE_SOURCES_0__ID`
- `BRIDGE_SOURCES_0__HOST`
- `BRIDGE_SOURCES_0__USERNAME`
- `BRIDGE_SOURCES_0__PASSWORD`

`application.yaml` only provides env-backed placeholder shape and safe defaults.

Durable runtime state lives in PostgreSQL:

- imported-message dedupe state
- encrypted OAuth credentials

Why this split was chosen:

- sources stay easy to define for local/self-hosted users
- credentials do not need to live in committed YAML
- durable operational state survives restarts
- a future admin UI can replace the env-defined source layer without rethinking the persistence model

Tradeoffs:

- `.env` is still plaintext for mailbox passwords and any OAuth fallback tokens
- mailbox credentials are not yet DB-backed or encrypted at rest
- the current model is strong enough for a serious hobby/self-hosted v1, but not yet equivalent to a managed secrets platform

### 4. Narrow OAuth scopes

Google OAuth scopes are intentionally limited to:

- `https://www.googleapis.com/auth/gmail.insert`
- `https://www.googleapis.com/auth/gmail.labels`

Microsoft scopes are protocol-specific:

- `offline_access`
- `https://outlook.office.com/IMAP.AccessAsUser.All`
- `https://outlook.office.com/POP.AccessAsUser.All`

This avoids requesting broad mail read/write access when the app only needs import and source-authentication capabilities.

### 5. Jakarta / Angus Mail for source mailbox access

The bridge uses Angus Mail through the Jakarta Mail API for:

- IMAP / IMAPS
- POP3 / POP3S
- raw MIME extraction with `message.writeTo(...)`
- IMAP UID lookup via `UIDFolder` when available

Connection behavior currently includes:

- TLS when configured
- hostname verification via `ssl.checkserveridentity=true`
- connection timeout `20000ms`
- read timeout `20000ms`
- Microsoft `XOAUTH2` support for IMAP and POP when a source is configured with `AUTH_METHOD=OAUTH2`

### 6. PostgreSQL-backed dedupe state

The app persists successful imports in PostgreSQL.

Why:

- dedupe survives restarts
- avoids in-memory-only state
- easy to inspect and extend
- good fit for a small self-hosted daemon

Current duplicate checks:

1. `(sourceAccountId, sourceMessageKey)`
2. raw MIME SHA-256

Important behavior:

- the SHA-based duplicate check is global
- if two different source mailboxes deliver byte-identical MIME, the second one will be skipped as a duplicate

### 7. Encrypted OAuth token storage in PostgreSQL

OAuth refresh and access tokens can now be stored encrypted at rest in PostgreSQL.

Implementation choices:

- AES-GCM with a 32-byte application-managed key
- Additional Authenticated Data bound to provider, subject, and token type
- key version stored with each record
- no authorization-code persistence after exchange
- access-token expiry metadata stored so valid tokens can be reused without unnecessary refresh calls

Why this design was chosen:

- removes refresh tokens from normal day-to-day operator workflows
- supports automatic access-token renewal
- keeps the secure path available without introducing an external vault dependency
- allows local self-hosted deployments to remain simple

Current limitation:

- key management is still application-managed via env var, not KMS/HSM-backed
- key rotation support is version-labelled but not yet a full multi-key ring
- the feature is enabled by setting `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` to a valid base64-encoded 32-byte key and then running the OAuth exchange flow again

### 8. Keep the polling logic simple in v1

The current implementation does not maintain mailbox checkpoints such as IMAP UIDVALIDITY or POP UIDL state.

Instead, it scans the latest `N` messages from each mailbox, where:

- `N = bridge.fetch-window`

This keeps the first version easy to reason about, but it has an important limitation:

- if more than `fetch-window` new messages arrive between polls, older unseen messages can be skipped

## Security Posture

### Implemented now

- TLS-capable mailbox connections
- hostname verification for mail connections
- encrypted-at-rest storage for Google and Microsoft OAuth tokens when `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` is configured
- automatic access-token refresh using encrypted stored refresh tokens
- no authorization-code persistence after exchange
- explicit minimal OAuth scopes for Google and protocol-specific scopes for Microsoft

### Not implemented yet

- encrypted-at-rest storage for mailbox passwords
- KMS/HSM-backed key management
- structured audit log for OAuth and polling events
- strict retry backoff / provider lockout protection
- comprehensive metrics and alerting
- circuit breakers for repeated provider auth failures
- full secret redaction guarantees across every future log path

## Current Runtime Model

### Scheduler

Polling is controlled by:

- `bridge.poll-enabled`
- `bridge.poll-interval`

Default poll interval:

- `5m`

### Overlap protection

`PollingService` uses an `AtomicBoolean` to prevent concurrent poll runs.

If a second poll starts while one is already running:

- the second run returns a `PollRunResult` with an error

### Failure behavior

- each source is handled independently
- a failure in one source is added to the result and logged
- the run continues with the next enabled source
- unexpected runtime errors are surfaced in the result

### Current observability

The project currently exposes only basic runtime visibility:

- `GET /api/health/summary`
  - returns `status`
  - returns total `importedMessages`
- `POST /api/poll/run`
  - triggers a manual poll
  - returns fetched/imported/duplicate/error counts for that run
- container logs
  - currently the main way to diagnose provider auth and polling failures

There is no built-in admin dashboard yet for:

- per-source import totals
- last successful sync time
- recent errors by source
- OAuth / source configuration visualization
- add/update/remove bridge management

### Processing model

- sources are processed sequentially
- messages within a source are processed sequentially
- there is no worker queue, retry queue, or backoff policy yet

## Build and Deployment

### Docker-first local workflow

The intended local path is Docker Compose:

```bash
docker compose up --build
```

`docker-compose.yml` defines:

- `inboxbridge`
- `postgres`

Postgres details:

- image: `postgres:16`
- database: `inboxbridge`
- user: `inboxbridge`
- password: `inboxbridge`

### Host build

Host Maven builds also work and are useful for tests:

- `mvn test` succeeds

### App runtime defaults

Important environment variables:

- `HTTP_PORT`
- `JDBC_URL`
- `JDBC_USERNAME`
- `JDBC_PASSWORD`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REFRESH_TOKEN`
- `GOOGLE_REDIRECT_URI`
- `MICROSOFT_TENANT`
- `MICROSOFT_CLIENT_ID`
- `MICROSOFT_CLIENT_SECRET`
- `MICROSOFT_REDIRECT_URI`
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID`
- `BRIDGE_POLL_ENABLED`
- `BRIDGE_POLL_INTERVAL`
- `BRIDGE_FETCH_WINDOW`
- `BRIDGE_GMAIL_DESTINATION_USER`
- `BRIDGE_GMAIL_CREATE_MISSING_LABELS`
- `BRIDGE_GMAIL_NEVER_MARK_SPAM`
- `BRIDGE_GMAIL_PROCESS_FOR_CALENDAR`

## Current Validation Status

Validated as of 2026-03-26:

- `mvn test` passes with unit coverage for Microsoft OAuth flow, encrypted token storage, and helper-resource behavior
- Docker Compose build works
- Quarkus app starts successfully in the container
- Flyway migrations `V1` and `V2` run successfully
- `GET /oauth/microsoft/` returns `200 OK`
- `GET /api/microsoft-oauth/sources` returns the configured Microsoft OAuth sources
- the browser-first Microsoft OAuth helper flow is present in the running app

Known live runtime issue from the current local configuration:

- the configured Outlook source is currently failing refresh with Microsoft `invalid_grant`
- this indicates a token / consent / provider-side issue, not a build or application boot issue

## Code Structure

Root package:

```text
dev.connexa.inboxbridge
```

Current package layout:

```text
src/main/java/dev/connexa/inboxbridge
├── config
│   └── BridgeConfig.java
├── domain
│   └── FetchedMessage.java
├── dto
│   ├── GmailImportResponse.java
│   ├── GoogleOAuthCodeRequest.java
│   ├── GoogleTokenExchangeResponse.java
│   ├── GoogleTokenResponse.java
│   ├── MicrosoftOAuthCodeRequest.java
│   ├── MicrosoftOAuthSourceOption.java
│   ├── MicrosoftTokenExchangeResponse.java
│   ├── MicrosoftTokenResponse.java
│   ├── OAuthUrlResponse.java
│   └── PollRunResult.java
├── persistence
│   ├── ImportedMessage.java
│   ├── ImportedMessageRepository.java
│   ├── OAuthCredential.java
│   └── OAuthCredentialRepository.java
├── service
│   ├── GmailImportService.java
│   ├── GmailLabelService.java
│   ├── GoogleOAuthService.java
│   ├── ImportDeduplicationService.java
│   ├── MailSourceClient.java
│   ├── MicrosoftOAuthService.java
│   ├── MimeHashService.java
│   ├── OAuthCredentialService.java
│   ├── PollingService.java
│   └── SecretEncryptionService.java
└── web
    ├── GoogleOAuthResource.java
    ├── HealthResource.java
    ├── MicrosoftOAuthResource.java
    └── PollingResource.java
```

Resources:

```text
src/main/resources
├── META-INF/resources/oauth/microsoft/index.html
├── application.yaml
└── db/migration
    ├── V1__init.sql
    └── V2__oauth_credential.sql
```

Tests:

```text
src/test/java/dev/connexa/inboxbridge
├── service
│   ├── MicrosoftOAuthServiceTest.java
│   ├── OAuthCredentialServiceTest.java
│   └── SecretEncryptionServiceTest.java
└── web
    └── MicrosoftOAuthResourceTest.java
```

## Package Details

### `config`

#### `BridgeConfig`

Typed Quarkus `@ConfigMapping` for all bridge configuration.

Main sections:

- global polling settings
- fetch window
- Gmail OAuth and import options
- Microsoft OAuth options
- list of source mailbox definitions

Nested config types:

- `BridgeConfig.Gmail`
- `BridgeConfig.Microsoft`
- `BridgeConfig.Source`
- `BridgeConfig.Protocol`
- `BridgeConfig.AuthMethod`
- `BridgeConfig.OAuthProvider`

Supported protocols:

- `IMAP`
- `POP3`

### `domain`

#### `FetchedMessage`

Immutable handoff object between mailbox fetch and Gmail import.

Fields:

- `sourceAccountId`
- `sourceMessageKey`
- `messageIdHeader`
- `messageInstant`
- `rawMessage`

### `dto`

Boundary DTOs used by REST resources and HTTP integrations.

Key DTO groups:

- Gmail import responses
- Google OAuth code and token exchange payloads
- Microsoft OAuth source-selection and token-exchange payloads
- generic authorization URL payloads
- poll result payloads

### `persistence`

#### `ImportedMessage`

Panache entity representing a successful Gmail import.

Table:

- `imported_message`

Used for durable dedupe across restarts and repeated polls.

#### `OAuthCredential`

Panache entity representing encrypted OAuth material and token metadata.

Table:

- `oauth_credential`

Fields include:

- provider
- subject key
- key version
- encrypted refresh token
- encrypted access token
- access expiry
- scope
- token type
- created / updated / refreshed timestamps

### `service`

#### `MailSourceClient`

Fetches mail from source accounts over IMAP(S) or POP3(S).

Key behavior:

- chooses protocol based on source config
- opens mailbox read-only
- supports IMAP unread-only search
- otherwise scans the last `fetch-window` messages
- sorts messages by received date, then sent date fallback, else epoch
- materializes each message as raw MIME bytes
- uses Microsoft `XOAUTH2` when a source is configured for Microsoft OAuth

Current source message key strategy:

1. IMAP UID via `UIDFolder`
2. `Message-ID` header
3. fallback `accountId:sha:<sha256>`

#### `GoogleOAuthService`

Handles Gmail OAuth flows.

Responsibilities:

- generate the Google authorization URL
- exchange an authorization code for tokens
- refresh access tokens using the refresh token
- cache the access token in memory until near expiry
- optionally store Google OAuth tokens encrypted in PostgreSQL

#### `MicrosoftOAuthService`

Handles Outlook / Microsoft OAuth flows for source accounts.

Responsibilities:

- list configured Microsoft OAuth-capable sources
- generate source-specific authorization URLs
- validate callback state
- exchange authorization codes for tokens
- refresh source access tokens
- reuse cached or encrypted stored access tokens when valid
- store refreshed credentials when secure storage is enabled

#### `OAuthCredentialService`

Owns persistence and retrieval of encrypted OAuth credentials.

Responsibilities:

- encrypt refresh/access tokens before persistence
- decrypt stored tokens for runtime use
- keep provider and subject contexts separate
- preserve refresh tokens when a provider refresh call returns only a new access token

#### `SecretEncryptionService`

Performs low-level AES-GCM encryption and decryption for OAuth credentials.

Responsibilities:

- validate key configuration
- generate per-secret nonces
- bind ciphertext to provider/subject/token-type context via AAD
- enforce matching key-version use on decrypt

### `web`

#### `GoogleOAuthResource`

Small REST resource for Google OAuth helper endpoints.

#### `MicrosoftOAuthResource`

REST resource for Microsoft OAuth helper endpoints and browser callback UX.

Endpoints:

- `/api/microsoft-oauth/url`
- `/api/microsoft-oauth/start`
- `/api/microsoft-oauth/sources`
- `/api/microsoft-oauth/exchange`
- `/api/microsoft-oauth/callback`

Static helper UI:

- `/oauth/microsoft/`

## Documentation Discipline

Repository maintenance expectation going forward:

- `CONTEXT.md` should be updated whenever technical behavior, architecture, or validation status changes
- existing user-facing docs should be kept in sync with code changes
- non-obvious code paths, especially security-sensitive ones, should carry enough inline documentation to explain intent and constraints
- operator-facing setup docs should include any provider account-registration steps needed to make the feature usable end to end, such as Microsoft Entra app-registration guidance for Outlook.com OAuth
