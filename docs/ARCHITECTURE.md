# Architecture notes

## Main flow

1. `PollingService` runs on schedule or manually via REST
2. Each enabled source in config is polled
3. `MailSourceClient` fetches recent messages over IMAP or POP3
4. `ImportDeduplicationService` checks whether a message was already imported
5. `GmailLabelService` resolves the Gmail label IDs
6. `GmailImportService` calls Gmail `messages.import`
7. Import metadata is stored in PostgreSQL

## Why this starter uses Gmail import

`messages.import` is the right API for this use case because it imports a raw MIME message into the mailbox without re-sending it to the world. That preserves the original sender, headers, dates, attachments, and threading signals much better than forwarding.

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
- the running poller merges both at runtime

That is deliberately simple for a first self-hosted version. The next evolution should be:

- persistent IMAP UID / UIDVALIDITY checkpoints
- POP UIDL checkpoints
- finer retry rules
- source throttling

## Package map

- `config`: config mapping interfaces
- `domain`: small internal domain objects
- `dto`: request / response DTOs
- `persistence`: database entities and repositories
- `service`: fetch, import, dedupe, label, and OAuth logic
- `web`: REST resources

## Admin UI layout

The React admin UI now separates:

- `My Email Fetchers`: a unified operational list of DB-managed and env-managed fetchers, with add/edit work happening in a modal dialog
- `Poller Settings`: global polling controls, health metrics, and runtime overrides
