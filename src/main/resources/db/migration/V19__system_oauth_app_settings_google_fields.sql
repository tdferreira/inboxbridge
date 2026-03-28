alter table system_oauth_app_settings
    add column google_destination_user varchar(255),
    add column google_redirect_uri varchar(1024),
    add column google_refresh_token_ciphertext varchar(2048),
    add column google_refresh_token_nonce varchar(255);
