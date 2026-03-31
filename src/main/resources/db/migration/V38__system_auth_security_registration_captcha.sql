alter table system_auth_security_setting
    add column if not exists registration_challenge_provider_override varchar(40),
    add column if not exists registration_turnstile_site_key_override varchar(255),
    add column if not exists registration_turnstile_secret_ciphertext text,
    add column if not exists registration_turnstile_secret_nonce text,
    add column if not exists registration_hcaptcha_site_key_override varchar(255),
    add column if not exists registration_hcaptcha_secret_ciphertext text,
    add column if not exists registration_hcaptcha_secret_nonce text;
