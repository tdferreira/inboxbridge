create table system_oauth_app_settings (
    id bigint primary key,
    google_client_id_ciphertext varchar(2048),
    google_client_id_nonce varchar(255),
    google_client_secret_ciphertext varchar(2048),
    google_client_secret_nonce varchar(255),
    microsoft_client_id_ciphertext varchar(2048),
    microsoft_client_id_nonce varchar(255),
    microsoft_client_secret_ciphertext varchar(2048),
    microsoft_client_secret_nonce varchar(255),
    key_version varchar(32) not null,
    updated_at timestamptz not null
);
