ALTER TABLE source_polling_state
    ADD COLUMN IF NOT EXISTS pop_last_seen_uidl VARCHAR(255);
