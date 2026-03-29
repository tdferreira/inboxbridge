ALTER TABLE user_bridge
    RENAME TO user_email_account;

ALTER TABLE user_email_account
    RENAME COLUMN bridge_id TO email_account_id;

ALTER INDEX IF EXISTS uk_user_bridge_bridge_id
    RENAME TO uk_user_email_account_email_account_id;

ALTER INDEX IF EXISTS idx_user_bridge_user
    RENAME TO idx_user_email_account_user;

ALTER INDEX IF EXISTS idx_user_bridge_enabled
    RENAME TO idx_user_email_account_enabled;
