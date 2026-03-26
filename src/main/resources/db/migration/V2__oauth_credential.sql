CREATE TABLE oauth_credential (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    subject_key VARCHAR(200) NOT NULL,
    key_version VARCHAR(40) NOT NULL,
    refresh_token_ciphertext VARCHAR(4096),
    refresh_token_nonce VARCHAR(64),
    access_token_ciphertext VARCHAR(4096),
    access_token_nonce VARCHAR(64),
    access_expires_at TIMESTAMPTZ,
    token_scope VARCHAR(2000),
    token_type VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_refreshed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_oauth_credential_provider_subject
    ON oauth_credential (provider, subject_key);

CREATE INDEX idx_oauth_credential_provider
    ON oauth_credential (provider);

CREATE INDEX idx_oauth_credential_access_expires
    ON oauth_credential (access_expires_at);
