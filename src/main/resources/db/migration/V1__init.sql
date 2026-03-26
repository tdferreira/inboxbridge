CREATE TABLE imported_message (
    id BIGSERIAL PRIMARY KEY,
    source_account_id VARCHAR(100) NOT NULL,
    source_message_key VARCHAR(500) NOT NULL,
    message_id_header VARCHAR(1000),
    raw_sha256 VARCHAR(64) NOT NULL,
    gmail_message_id VARCHAR(255) NOT NULL,
    gmail_thread_id VARCHAR(255),
    imported_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_imported_message_source_key
    ON imported_message (source_account_id, source_message_key);

CREATE UNIQUE INDEX uk_imported_message_sha
    ON imported_message (raw_sha256);

CREATE INDEX idx_imported_message_account
    ON imported_message (source_account_id);

CREATE INDEX idx_imported_message_gmail
    ON imported_message (gmail_message_id);
