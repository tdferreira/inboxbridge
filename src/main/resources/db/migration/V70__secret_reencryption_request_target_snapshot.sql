alter table system_secret_reencryption_request
    add column requested_mode varchar(64),
    add column requested_provider_id varchar(190),
    add column requested_active_key_version varchar(255),
    add column requested_active_key_id varchar(255);
