# InboxBridge Context

## Purpose

InboxBridge is a self-hosted bridge that pulls messages from external mailboxes over IMAP or POP3 and imports them into a Gmail mailbox using the Gmail API.

The project is deliberately a starter scaffold, not a production-ready MailBridge clone. It is meant to preserve the old Gmailify / POP fetching workflow with a small containerized service that you can run yourself.

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
- Java `HttpClient` for Gmail and Google OAuth HTTP calls
- Docker multi-stage build
- Docker Compose for app + database

## Core Architecture

InboxBridge is a single Quarkus application with PostgreSQL as its only durable state store.

High-level flow:

1. `PollingService` is triggered by the scheduler or a manual REST call.
2. Each enabled source account from config is processed sequentially.
3. `MailSourceClient` fetches candidate messages from the source mailbox.
4. Each message is materialized as raw MIME bytes.
5. `ImportDeduplicationService` checks whether it was already imported.
6. `GmailLabelService` resolves `INBOX`, `UNREAD`, and any custom source label.
7. `GmailImportService` sends the raw MIME to Gmail `users.messages.import`.
8. `ImportDeduplicationService` records the successful import in PostgreSQL.
9. `PollRunResult` summarizes fetched, imported, duplicate, and error counts.

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

### 3. Narrow Gmail OAuth scopes

The OAuth helper currently requests:

- `https://www.googleapis.com/auth/gmail.insert`
- `https://www.googleapis.com/auth/gmail.labels`

This is intentionally narrower than full Gmail read/write access.

### 4. Jakarta / Angus Mail for source mailbox access

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

### 5. PostgreSQL-backed dedupe state

The app persists successful imports in PostgreSQL.

Why:

- dedupe survives restarts
- avoids in-memory-only state
- easy to inspect and extend
- good fit for a small self-hosted daemon

### 6. Env-first source definition

Mailbox sources are configured through environment variables or `.env`, using indexed keys such as:

- `BRIDGE_SOURCES_0__ID`
- `BRIDGE_SOURCES_0__HOST`
- `BRIDGE_SOURCES_0__USERNAME`
- `BRIDGE_SOURCES_0__PASSWORD`

`application.yaml` only provides an env-backed placeholder shape and safe defaults.

Why this was chosen:

- avoids committing mailbox credentials to source-controlled YAML
- keeps the starter easy to run without a database-backed admin plane
- leaves room for a future web UI without changing the runtime model

Tradeoff:

- `.env` is still plaintext
- this is safer than committed YAML, but not production-grade secret handling
- a DB-backed `source_account` model or secret-store integration is still a likely next step

### 7. Keep the polling logic simple in v1

The current implementation does not maintain mailbox checkpoints such as IMAP UIDVALIDITY or POP UIDL state.

Instead, it scans the latest `N` messages from each mailbox, where:

- `N = bridge.fetch-window`

This keeps the first version easy to reason about, but it has an important limitation:

- if more than `fetch-window` new messages arrive between polls, older unseen messages can be skipped

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

### Processing model

- sources are processed sequentially
- messages within a source are processed sequentially
- there is no worker queue, retry queue, or backoff policy yet

## Build and Deployment

### Host build

The project now builds successfully on the host with Maven after these fixes:

- Quarkus version set to `3.32.3`
- Quarkus Maven plugin group corrected to `io.quarkus`
- Quarkus BOM kept under `io.quarkus.platform`
- `quarkus-junit5` replaced with `quarkus-junit`
- Jakarta Mail header access fixed in `MailSourceClient`

Current host result:

- `mvn test` succeeds
- there are no test classes yet, so no automated tests are executed

### Docker build

The Docker image uses a multi-stage build:

1. `maven:3.9.9-eclipse-temurin-21` builds the Quarkus app
2. `eclipse-temurin:21-jre` runs the built app

The current Compose setup requires host networking during image build:

- `docker-compose.yml` sets `build.network: host`

This was added because dependency resolution inside Docker failed without host networking in the current environment.

### Compose services

`docker-compose.yml` defines:

- `inboxbridge`
- `postgres`

Postgres details:

- image: `postgres:16`
- database: `inboxbridge`
- user: `inboxbridge`
- password: `inboxbridge`

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
- `BRIDGE_POLL_ENABLED`
- `BRIDGE_POLL_INTERVAL`
- `BRIDGE_FETCH_WINDOW`
- `BRIDGE_GMAIL_DESTINATION_USER`
- `BRIDGE_GMAIL_CREATE_MISSING_LABELS`
- `BRIDGE_GMAIL_NEVER_MARK_SPAM`
- `BRIDGE_GMAIL_PROCESS_FOR_CALENDAR`

## Current Validation Status

Validated as of 2026-03-26:

- host Maven build works
- Docker Compose build works
- Quarkus app starts successfully in the container
- Flyway migration runs successfully
- `/api/health/summary` returns `200 OK` inside the container
- `/api/poll/run` returns `200 OK` inside the container

Important caveats:

- there are still no automated tests in the repo
- host-side `curl http://localhost:8080/...` was not reliable in this environment even though the app responds correctly inside the container

### Current end-to-end blocker

A previously tested Outlook IMAP source was not fetchable from this environment.

What was confirmed:

- DNS resolution for `outlook.office365.com` works on both host and container
- TCP connection attempts to `outlook.office365.com:993` time out on both host and container
- the failure occurs before authentication

Conclusion:

- the app and Docker setup are working
- outbound IMAP connectivity to Outlook on port `993` is blocked or unreachable from this network
- end-to-end mailbox ingestion cannot be validated until that network issue is resolved

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
│   ├── GoogleTokenResponse.java
│   ├── OAuthUrlResponse.java
│   └── PollRunResult.java
├── persistence
│   ├── ImportedMessage.java
│   └── ImportedMessageRepository.java
├── service
│   ├── GmailImportService.java
│   ├── GmailLabelService.java
│   ├── GoogleOAuthService.java
│   ├── ImportDeduplicationService.java
│   ├── MailSourceClient.java
│   ├── MimeHashService.java
│   └── PollingService.java
└── web
    ├── GoogleOAuthResource.java
    ├── HealthResource.java
    └── PollingResource.java
```

Resources:

```text
src/main/resources
├── application.yaml
└── db/migration/V1__init.sql
```

## Package Details

### `config`

#### `BridgeConfig`

Typed Quarkus `@ConfigMapping` for all bridge configuration.

Main sections:

- global polling settings
- fetch window
- Gmail OAuth and import options
- list of source mailbox definitions

Nested config types:

- `BridgeConfig.Gmail`
- `BridgeConfig.Source`
- `BridgeConfig.Protocol`

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

#### `GmailImportResponse`

Stores Gmail's returned message id and thread id.

#### `GoogleOAuthCodeRequest`

JSON request body for exchanging an OAuth authorization code.

#### `GoogleTokenResponse`

Represents the Google OAuth token endpoint response.

#### `OAuthUrlResponse`

Simple DTO containing the generated Google authorization URL.

#### `PollRunResult`

Mutable result object for a poll run.

Tracks:

- `startedAt`
- `finishedAt`
- `fetched`
- `imported`
- `duplicates`
- `errors`

### `persistence`

#### `ImportedMessage`

Panache entity representing a successful Gmail import.

Table:

- `imported_message`

Fields:

- `id`
- `sourceAccountId`
- `sourceMessageKey`
- `messageIdHeader`
- `rawSha256`
- `gmailMessageId`
- `gmailThreadId`
- `importedAt`

Constraints and indexes:

- unique on `(source_account_id, source_message_key)`
- unique on `raw_sha256`
- index on `source_account_id`
- index on `gmail_message_id`

#### `ImportedMessageRepository`

Panache repository used to:

- check existence by source key
- check existence by raw MIME SHA-256
- retrieve existing rows by source key

### `service`

#### `MimeHashService`

Utility for computing SHA-256 hashes of raw MIME bytes.

Used for:

- durable dedupe
- fallback source message keys

#### `MailSourceClient`

Fetches mail from source accounts over IMAP(S) or POP3(S).

Key behavior:

- chooses protocol based on source config
- opens mailbox read-only
- supports IMAP unread-only search
- otherwise scans the last `fetch-window` messages
- sorts messages by received date, then sent date fallback, else epoch
- materializes each message as raw MIME bytes

Current source message key strategy:

1. IMAP UID via `UIDFolder`
2. `Message-ID` header
3. fallback `accountId:sha:<sha256>`

#### `ImportDeduplicationService`

Determines whether a fetched message is already known and records successful imports.

Current duplicate checks:

1. `(sourceAccountId, sourceMessageKey)`
2. raw MIME SHA-256

Important behavior:

- the SHA-based duplicate check is global
- if two different source mailboxes deliver byte-identical MIME, the second one will be skipped as a duplicate

#### `GoogleOAuthService`

Handles Gmail OAuth flows.

Responsibilities:

- generate the Google authorization URL
- exchange an authorization code for tokens
- refresh access tokens using the refresh token
- cache the access token in memory until near expiry

Caching detail:

- uses `AtomicReference<CachedToken>`
- refreshes when the cached token is within 30 seconds of expiry

#### `GmailLabelService`

Resolves Gmail label ids required for imported messages.

Behavior:

- always includes `INBOX`
- always includes `UNREAD`
- resolves optional custom labels by name
- can auto-create missing custom labels

Implementation detail:

- list labels via Gmail API
- create labels via Gmail API when allowed by config

#### `GmailImportService`

Imports raw MIME messages into Gmail.

Behavior:

- base64url encodes the raw MIME
- sends Gmail `messages.import`
- sets import query parameters from config
- parses the returned Gmail id and thread id

#### `PollingService`

Top-level orchestrator for scheduled and manual polling.

Responsibilities:

- scheduler entrypoint
- manual API entrypoint
- overlap protection with `AtomicBoolean`
- iterate enabled sources
- fetch, dedupe, import, persist
- accumulate a `PollRunResult`

### `web`

#### `GoogleOAuthResource`

Endpoints:

- `GET /api/google-oauth/url`
- `POST /api/google-oauth/exchange`
- `GET /api/google-oauth/callback`

Purpose:

- bootstrap Gmail OAuth credentials
- help obtain a refresh token during setup

#### `PollingResource`

Endpoint:

- `POST /api/poll/run`

Purpose:

- manually trigger a poll cycle

#### `HealthResource`

Endpoint:

- `GET /api/health/summary`

Current response fields:

- `status`
- `importedMessages`

## Database Schema

Flyway migration:

- `src/main/resources/db/migration/V1__init.sql`

Current schema creates one table:

- `imported_message`

Deliberate omission:

- mailbox credentials are not stored in the database
- raw message bodies are not stored in the database

## Security Posture

Good choices already present:

- Gmail OAuth instead of a Gmail password
- narrow Gmail scopes
- TLS support for IMAP / POP
- hostname verification enabled
- raw messages are not persisted in PostgreSQL
- durable state stores only metadata and hashes

Current limitations:

- source mailbox credentials currently live in `.env`
- Gmail refresh token is environment/config driven
- there is no encrypted-at-rest secret storage
- there is no vault integration
- there is no admin auth model
- there is no structured redaction framework for secrets in config

Important current concern:

- `.env` is gitignored, but it is still plaintext
- for production use, secrets should move to Docker secrets or an external secret manager

## Known Limitations

Not yet implemented:

- automated tests
- provider OAuth for source mailboxes
- IMAP checkpoint persistence
- POP UIDL checkpoint persistence
- IMAP IDLE / push ingestion
- retries and exponential backoff
- dead-letter handling
- metrics / tracing
- admin UI
- DB-backed source account management
- secret manager integration
- message retention / delete semantics on source side

## Recommended Next Steps

1. Resolve outbound network access to the source provider's IMAP/POP ports.
2. Validate Gmail OAuth with real destination account credentials.
3. Run a real end-to-end poll with a dedicated test mailbox.
4. Add at least one automated integration test around polling and dedupe.
5. Replace fetch-window scanning with durable mailbox checkpoints.
6. Move mailbox secrets from `.env` to Docker secrets or an external secret manager.

## Quick Reading Order

For a new maintainer or agent, the most useful reading order is:

1. `README.md`
2. `CONTEXT.md`
3. `pom.xml`
4. `docker-compose.yml`
5. `src/main/resources/application.yaml`
6. `src/main/java/dev/connexa/inboxbridge/config/BridgeConfig.java`
7. `src/main/java/dev/connexa/inboxbridge/service/PollingService.java`
8. `src/main/java/dev/connexa/inboxbridge/service/MailSourceClient.java`
9. `src/main/java/dev/connexa/inboxbridge/service/GmailImportService.java`
10. `src/main/java/dev/connexa/inboxbridge/service/ImportDeduplicationService.java`
11. `src/main/java/dev/connexa/inboxbridge/persistence/ImportedMessage.java`
12. `src/main/resources/db/migration/V1__init.sql`

## One-Paragraph Mental Model

InboxBridge is a small Quarkus daemon that periodically scans the latest messages from configured external IMAP or POP mailboxes, converts each candidate into raw MIME bytes, skips anything already seen by source key or MIME hash, imports genuinely new mail into Gmail through `users.messages.import`, applies `INBOX`, `UNREAD`, and optional source labels, and stores only durable import metadata in PostgreSQL so duplicate imports do not reappear across poll runs or restarts.
