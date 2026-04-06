ALTER TABLE user_email_account
    ADD COLUMN IF NOT EXISTS enable_after_oauth_connect BOOLEAN NOT NULL DEFAULT FALSE;
