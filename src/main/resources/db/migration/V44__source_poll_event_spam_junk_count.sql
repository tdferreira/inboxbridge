ALTER TABLE source_poll_event
    ADD COLUMN spam_junk_message_count INTEGER NOT NULL DEFAULT 0;
