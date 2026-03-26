# InboxBridge

InboxBridge is a self-hosted starter project that polls external mailboxes over IMAP or POP3 and imports messages into Gmail through the Gmail API.

This repository is intentionally a **clean starter scaffold**, not a finished SaaS clone. It already contains the core moving parts:

- Quarkus 3.x application
- Docker / Docker Compose setup
- PostgreSQL persistence for import deduplication
- Gmail OAuth helper endpoints
- Gmail `messages.import` integration
- IMAP and POP3 polling with TLS
- Custom per-source labels in Gmail
- Manual and scheduled polling

## What this starter already does

1. Connects to configured external source accounts
2. Pulls recent messages from IMAP or POP3
3. Dedupe-checks them against PostgreSQL
4. Imports them into Gmail
5. Applies `INBOX`, `UNREAD`, and an optional per-source custom label
6. Stores the import state so the same message is not imported twice

## What this starter does not yet do

This is a strong base, but some features are still intentionally left for you to extend:

- encrypted-at-rest storage of mailbox credentials
- admin UI
- provider-specific OAuth flows beyond Microsoft / Gmail
- IMAP IDLE / push-style fetching
- sophisticated mailbox cursors for very large mailboxes
- metrics, rate-limits, retries, and dead-letter handling
- secret vault integration

## Architecture

- `MailSourceClient` fetches source messages using Jakarta / Angus Mail
- `GmailImportService` calls Gmail `users.messages.import`
- `GmailLabelService` resolves or creates Gmail labels
- `ImportDeduplicationService` prevents duplicate imports
- `PollingService` orchestrates scheduled/manual polling
- `ImportedMessage` stores durable import history

## Project structure

```text
src/main/java/dev/connexa/inboxbridge
├── config
├── domain
├── dto
├── persistence
├── service
└── web
```

## Requirements

- Java 21
- Maven 3.9+
- Docker + Docker Compose
- A Google Cloud project with the Gmail API enabled
- OAuth client credentials for the destination Gmail account
- External mailbox credentials (preferably app passwords)

## Gmail API notes

This starter is built around **message import**, not SMTP forwarding.

That keeps the original message shape and avoids the usual forwarding drawbacks. The implementation uses:

- Gmail OAuth token refresh
- `gmail.insert` scope for message import
- `gmail.labels` scope for creating / resolving custom labels

## Quick start

### 1. Prepare environment file

```bash
cp .env.example .env
```

Fill in:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REFRESH_TOKEN`
- `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` if you want OAuth refresh tokens encrypted in PostgreSQL
- `BRIDGE_SOURCES_<index>__...` for each external mailbox

### 2. Configure source accounts

Keep real source accounts in `.env`, not in `src/main/resources/application.yaml`.

The repository now uses env-backed indexed source settings so credentials do not need to live in committed YAML. The first source uses `BRIDGE_SOURCES_0__...`, the second uses `BRIDGE_SOURCES_1__...`, and so on.

Example IMAP source in `.env`:

```dotenv
BRIDGE_SOURCES_0__ID=outlook-main
BRIDGE_SOURCES_0__ENABLED=true
BRIDGE_SOURCES_0__PROTOCOL=IMAP
BRIDGE_SOURCES_0__HOST=outlook.office365.com
BRIDGE_SOURCES_0__PORT=993
BRIDGE_SOURCES_0__TLS=true
BRIDGE_SOURCES_0__AUTH_METHOD=PASSWORD
BRIDGE_SOURCES_0__USERNAME=you@example.com
BRIDGE_SOURCES_0__PASSWORD=your-app-password
BRIDGE_SOURCES_0__FOLDER=INBOX
BRIDGE_SOURCES_0__UNREAD_ONLY=false
BRIDGE_SOURCES_0__CUSTOM_LABEL=Imported/Outlook
```

Example POP3 source in `.env`:

```dotenv
BRIDGE_SOURCES_1__ID=legacy-pop
BRIDGE_SOURCES_1__ENABLED=true
BRIDGE_SOURCES_1__PROTOCOL=POP3
BRIDGE_SOURCES_1__HOST=pop.example.com
BRIDGE_SOURCES_1__PORT=995
BRIDGE_SOURCES_1__TLS=true
BRIDGE_SOURCES_1__AUTH_METHOD=PASSWORD
BRIDGE_SOURCES_1__USERNAME=you@example.com
BRIDGE_SOURCES_1__PASSWORD=your-app-password
BRIDGE_SOURCES_1__UNREAD_ONLY=false
BRIDGE_SOURCES_1__CUSTOM_LABEL=Imported/LegacyPOP
```

`.env` is gitignored, but it is still plaintext. For stronger protection, move these values to Docker secrets or an external secret manager later.

For encrypted OAuth token storage, generate a 32-byte base64 key and set:

```bash
openssl rand -base64 32
```

Then place the output in `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY`. When that key is configured, InboxBridge stores Google and Microsoft OAuth refresh tokens encrypted in PostgreSQL and automatically refreshes short-lived access tokens.

In short, secure token storage is enabled by:

1. generating a key with `openssl rand -base64 32`
2. setting `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` in `.env`
3. restarting the app
4. running the OAuth exchange again so the token is written into PostgreSQL

### 3. Build and run locally

```bash
mvn quarkus:dev
```

### 4. Or run with Docker Compose

```bash
docker compose up --build
```

## OAuth helper flow

The project exposes helper endpoints:

- `GET /api/google-oauth/url`
- `POST /api/google-oauth/exchange`
- `GET /api/google-oauth/callback`
- `GET /api/microsoft-oauth/url?sourceId=<source-id>`
- `GET /api/microsoft-oauth/start?sourceId=<source-id>`
- `POST /api/microsoft-oauth/exchange`
- `GET /api/microsoft-oauth/callback`
- `GET /oauth/microsoft/`

Suggested flow:

1. Open `/api/google-oauth/url`
2. Authenticate with Google
3. Grant access
4. Copy the returned authorization code
5. POST it to `/api/google-oauth/exchange`
6. If secure storage is enabled, InboxBridge stores the refresh token encrypted in PostgreSQL. Otherwise store the returned refresh token in `.env`.

Example exchange request:

```bash
curl -X POST http://localhost:8080/api/google-oauth/exchange \
  -H 'Content-Type: application/json' \
  -d '{"code":"REPLACE_ME"}'
```

Example Microsoft exchange request:

```bash
curl -X POST http://localhost:8080/api/microsoft-oauth/exchange \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"outlook-main","code":"REPLACE_ME"}'
```

When `BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY` is configured, the Microsoft helper page stores the refresh token encrypted in PostgreSQL and future access-token renewals happen automatically. Without that key, the exchange response falls back to returning the refresh token so you can keep using `BRIDGE_SOURCES_<index>__OAUTH_REFRESH_TOKEN` in `.env`.

For the full Microsoft cloud setup path, including how to create the Azure / Entra account, register the app, choose the right redirect URI platform, add IMAP / POP delegated permissions, and locate the client ID / tenant / client secret, see [docs/OAUTH_SETUP.md](/Users/tdferreira/Developer/inboxbridge/docs/OAUTH_SETUP.md).

## Trigger a manual poll

```bash
curl -X POST http://localhost:8080/api/poll/run
```

## Health summary

```bash
curl http://localhost:8080/api/health/summary
```

Current behavior:

- returns `status=UP`
- returns `importedMessages`, which is the total number of successfully recorded imports in PostgreSQL

To force an immediate poll and see fetched/imported/duplicate/error counts:

```bash
curl -X POST http://localhost:8080/api/poll/run
```

## Source account configuration

Example IMAP source in `.env`:

```dotenv
BRIDGE_SOURCES_0__ID=outlook-main
BRIDGE_SOURCES_0__ENABLED=true
BRIDGE_SOURCES_0__PROTOCOL=IMAP
BRIDGE_SOURCES_0__HOST=outlook.office365.com
BRIDGE_SOURCES_0__PORT=993
BRIDGE_SOURCES_0__TLS=true
BRIDGE_SOURCES_0__AUTH_METHOD=PASSWORD
BRIDGE_SOURCES_0__USERNAME=you@example.com
BRIDGE_SOURCES_0__PASSWORD=your-app-password
BRIDGE_SOURCES_0__FOLDER=INBOX
BRIDGE_SOURCES_0__UNREAD_ONLY=false
BRIDGE_SOURCES_0__CUSTOM_LABEL=Imported/Outlook
```

Example POP3 source in `.env`:

```dotenv
BRIDGE_SOURCES_1__ID=legacy-pop
BRIDGE_SOURCES_1__ENABLED=true
BRIDGE_SOURCES_1__PROTOCOL=POP3
BRIDGE_SOURCES_1__HOST=pop.example.com
BRIDGE_SOURCES_1__PORT=995
BRIDGE_SOURCES_1__TLS=true
BRIDGE_SOURCES_1__AUTH_METHOD=PASSWORD
BRIDGE_SOURCES_1__USERNAME=you@example.com
BRIDGE_SOURCES_1__PASSWORD=your-password
BRIDGE_SOURCES_1__UNREAD_ONLY=false
BRIDGE_SOURCES_1__CUSTOM_LABEL=Imported/LegacyPOP
```

Example Outlook IMAP source with Microsoft OAuth:

```dotenv
MICROSOFT_TENANT=consumers
MICROSOFT_CLIENT_ID=your-microsoft-app-client-id
MICROSOFT_CLIENT_SECRET=your-microsoft-app-client-secret
MICROSOFT_REDIRECT_URI=http://localhost:8080/api/microsoft-oauth/callback
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY=base64-encoded-32-byte-key
BRIDGE_SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1

BRIDGE_SOURCES_0__ID=outlook-main
BRIDGE_SOURCES_0__ENABLED=true
BRIDGE_SOURCES_0__PROTOCOL=IMAP
BRIDGE_SOURCES_0__HOST=outlook.office365.com
BRIDGE_SOURCES_0__PORT=993
BRIDGE_SOURCES_0__TLS=true
BRIDGE_SOURCES_0__AUTH_METHOD=OAUTH2
BRIDGE_SOURCES_0__OAUTH_PROVIDER=MICROSOFT
BRIDGE_SOURCES_0__USERNAME=you@outlook.com
BRIDGE_SOURCES_0__OAUTH_REFRESH_TOKEN=optional-fallback-if-db-encryption-is-disabled
BRIDGE_SOURCES_0__FOLDER=INBOX
BRIDGE_SOURCES_0__UNREAD_ONLY=false
BRIDGE_SOURCES_0__CUSTOM_LABEL=Imported/Outlook
```

## Important implementation note

For simplicity, this starter currently scans the **most recent N messages** per source account, where `N = bridge.fetch-window`.

That is good enough to get the project running and is easy to reason about, but for large / busy mailboxes you will likely want to evolve it toward:

- IMAP UID checkpoints
- POP UIDL checkpoints
- retry queues
- parallel import workers

## Security recommendations before production use

Before treating this as a personal production bridge, add at least:

- encrypted-at-rest storage for mailbox passwords, not just OAuth tokens
- Docker secrets or an external secret manager
- minimal OAuth scopes and app passwords only where OAuth is unavailable
- structured logging that never emits raw message bodies
- metrics and alerting
- retry / backoff rules
- account disable circuit breaker

## Useful next extensions

- extend the encrypted credential model to mailbox passwords
- add a `source_account` table instead of YAML-only config
- add a small admin UI
- add provider templates for Yahoo / Outlook / iCloud / Fastmail
- add OAuth for external mail providers where available
- add OpenTelemetry / Micrometer metrics
- add IMAP IDLE mode

## Disclaimer

This project is intended as a self-hosted starter and learning base. It is not yet production-grade and has not been hardened or audited.
