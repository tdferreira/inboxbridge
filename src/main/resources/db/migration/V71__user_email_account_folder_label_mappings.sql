ALTER TABLE user_email_account
    ADD COLUMN IF NOT EXISTS folder_label_mappings VARCHAR(2048);
