DROP INDEX IF EXISTS uk_imported_message_source_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_imported_message_destination_source_key
    ON imported_message (destination_key, source_account_id, source_message_key);
