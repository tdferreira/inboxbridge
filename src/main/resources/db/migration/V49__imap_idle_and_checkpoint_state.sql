ALTER TABLE user_email_account
    ADD COLUMN IF NOT EXISTS fetch_mode VARCHAR(20) NOT NULL DEFAULT 'POLLING';

ALTER TABLE source_polling_state
    ADD COLUMN IF NOT EXISTS imap_folder_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS imap_uid_validity BIGINT,
    ADD COLUMN IF NOT EXISTS imap_last_seen_uid BIGINT;
