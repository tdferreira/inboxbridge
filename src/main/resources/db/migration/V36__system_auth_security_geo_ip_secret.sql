alter table if exists system_auth_security_setting
    add column if not exists geo_ip_ipinfo_token_ciphertext varchar(4096),
    add column if not exists geo_ip_ipinfo_token_nonce varchar(64),
    add column if not exists key_version varchar(32);
