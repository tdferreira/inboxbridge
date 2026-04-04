ALTER TABLE source_poll_event
    ADD COLUMN imported_bytes BIGINT NOT NULL DEFAULT 0;
