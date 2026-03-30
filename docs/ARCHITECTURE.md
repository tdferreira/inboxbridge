# Architecture notes

## Main flow

1. `PollingService` runs on schedule or manually via REST
2. Each enabled source in config is polled
3. `MailSourceClient` fetches recent messages over IMAP or POP3
4. `ImportDeduplicationService` checks whether a message was already imported
5. The configured destination implementation prepares the append/import target
6. `MailDestinationService` dispatches to the active destination provider, such as Gmail API import or IMAP APPEND
7. Import metadata is stored in PostgreSQL

## Why Gmail import is still one destination mode

When the destination provider is Gmail, `messages.import` is the right API for this use case because it imports a raw MIME message into the mailbox without re-sending it to the world. That preserves the original sender, headers, dates, attachments, and threading signals much better than forwarding.

## Dedupe strategy

Current dedupe uses two keys:

- source-message key (`IMAP UID` when available, otherwise `Message-ID`, otherwise SHA-256)
- SHA-256 of the raw MIME message

That gives a strong baseline while keeping the code easy to understand.

## Current compromise for simplicity

This starter scans the latest configured message window rather than maintaining a high-fidelity mailbox cursor.

The fetch window and scheduler interval now come from effective runtime polling settings:

- environment values provide defaults
- PostgreSQL stores optional admin overrides
- PostgreSQL also stores optional per-user overrides for DB-managed source email accounts
- the running poller merges the correct layer at runtime for each source

Each source now has its own persisted polling state:

- next eligible poll time
- active cooldown-until time
- consecutive failure count
- last failure reason and timestamps

The scheduler checks that state on every run so one blocked or throttled mailbox does not stall unrelated source email accounts.

That is deliberately simple for a first self-hosted version. The next evolution should be:

- persistent IMAP UID / UIDVALIDITY checkpoints
- POP UIDL checkpoints
- richer retry classification
- richer metrics and audit logs for poll cooldown decisions

## Package map

- `config`: config mapping interfaces
- `domain`: small internal domain objects
- `dto`: request / response DTOs
- `persistence`: database entities and repositories
- `service`: fetch, import, dedupe, label, and OAuth logic
- `web`: REST resources

## Admin UI layout

The React admin UI now separates:

- `My Poller Settings`: per-user polling overrides for UI-managed source email accounts
- `My Source Email Accounts`: a unified operational list of DB-managed and env-managed source email accounts, with add/edit work happening in a modal dialog and provider-specific actions gated by the selected provider
- `My Destination Mailbox`: provider-neutral destination mailbox configuration for Gmail API or IMAP APPEND destinations, with Gmail always using `Save and Authenticate` and Outlook allowing plain saves only for non-identity edits such as folder changes
- `Global Poller Settings`: global polling controls, health metrics, and runtime overrides

Deployment mode is also configurable:

- multi-user mode shows self-registration and admin user management
- single-user mode hides those surfaces and keeps InboxBridge focused on the bootstrap admin only
