ALTER TABLE app_user
    ADD COLUMN user_handle VARCHAR(128);

UPDATE app_user
SET user_handle = md5(username || ':' || id::text || ':' || created_at::text)
WHERE user_handle IS NULL;

ALTER TABLE app_user
    ALTER COLUMN user_handle SET NOT NULL;

CREATE UNIQUE INDEX uk_app_user_user_handle
    ON app_user (user_handle);

CREATE TABLE user_passkey (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label VARCHAR(160) NOT NULL,
    credential_id VARCHAR(1024) NOT NULL,
    public_key_cose VARCHAR(4096) NOT NULL,
    signature_count BIGINT NOT NULL,
    aaguid VARCHAR(64),
    transports VARCHAR(512),
    discoverable BOOLEAN NOT NULL,
    backup_eligible BOOLEAN NOT NULL,
    backed_up BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_user_passkey_credential
    ON user_passkey (credential_id);

CREATE INDEX idx_user_passkey_user
    ON user_passkey (user_id);

CREATE TABLE passkey_ceremony (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE,
    ceremony_type VARCHAR(32) NOT NULL,
    request_json TEXT NOT NULL,
    label VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_passkey_ceremony_user
    ON passkey_ceremony (user_id);

CREATE INDEX idx_passkey_ceremony_expires
    ON passkey_ceremony (expires_at);
