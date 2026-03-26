ALTER TABLE passkey_ceremony
    ADD COLUMN password_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_ui_preference
    ADD COLUMN language VARCHAR(32) NOT NULL DEFAULT 'en';
