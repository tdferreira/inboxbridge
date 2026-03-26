ALTER TABLE user_gmail_config
    ADD COLUMN key_version VARCHAR(64);

ALTER TABLE user_bridge
    ADD COLUMN key_version VARCHAR(64);

ALTER TABLE imported_message
    ADD COLUMN destination_key VARCHAR(160) NOT NULL DEFAULT 'gmail-destination';

DROP INDEX uk_imported_message_sha;

CREATE UNIQUE INDEX uk_imported_message_destination_sha
    ON imported_message (destination_key, raw_sha256);

CREATE INDEX idx_imported_message_destination
    ON imported_message (destination_key);
