ALTER TABLE app_user
    ADD COLUMN approved BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_app_user_approved
    ON app_user (approved);
