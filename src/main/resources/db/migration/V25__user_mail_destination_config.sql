CREATE TABLE user_mail_destination_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    host VARCHAR(255),
    port INTEGER,
    tls BOOLEAN NOT NULL DEFAULT TRUE,
    auth_method VARCHAR(20) NOT NULL DEFAULT 'PASSWORD',
    oauth_provider VARCHAR(20) NOT NULL DEFAULT 'NONE',
    username VARCHAR(255),
    password_ciphertext VARCHAR(4096),
    password_nonce VARCHAR(64),
    folder_name VARCHAR(255),
    key_version VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_mail_destination_config_user UNIQUE (user_id)
);

CREATE INDEX idx_user_mail_destination_config_user ON user_mail_destination_config(user_id);