CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    role VARCHAR(20) NOT NULL,
    must_change_password BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_app_user_username
    ON app_user (username);

CREATE INDEX idx_app_user_role
    ON app_user (role);

CREATE TABLE user_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_user_session_token_hash
    ON user_session (token_hash);

CREATE INDEX idx_user_session_user
    ON user_session (user_id);

CREATE INDEX idx_user_session_expires
    ON user_session (expires_at);

CREATE TABLE user_gmail_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    destination_user VARCHAR(255) NOT NULL,
    client_id_ciphertext VARCHAR(4096),
    client_id_nonce VARCHAR(64),
    client_secret_ciphertext VARCHAR(4096),
    client_secret_nonce VARCHAR(64),
    refresh_token_ciphertext VARCHAR(4096),
    refresh_token_nonce VARCHAR(64),
    redirect_uri VARCHAR(500) NOT NULL,
    create_missing_labels BOOLEAN NOT NULL,
    never_mark_spam BOOLEAN NOT NULL,
    process_for_calendar BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_user_gmail_config_user
    ON user_gmail_config (user_id);

CREATE INDEX idx_user_gmail_config_user
    ON user_gmail_config (user_id);

CREATE TABLE user_bridge (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    bridge_id VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    tls BOOLEAN NOT NULL,
    auth_method VARCHAR(20) NOT NULL,
    oauth_provider VARCHAR(20) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_ciphertext VARCHAR(4096),
    password_nonce VARCHAR(64),
    oauth_refresh_token_ciphertext VARCHAR(4096),
    oauth_refresh_token_nonce VARCHAR(64),
    folder_name VARCHAR(255),
    unread_only BOOLEAN NOT NULL,
    custom_label VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_user_bridge_bridge_id
    ON user_bridge (bridge_id);

CREATE INDEX idx_user_bridge_user
    ON user_bridge (user_id);

CREATE INDEX idx_user_bridge_enabled
    ON user_bridge (enabled);
