alter table system_secret_reencryption_request
    add column request_preview_json text,
    add column last_total_records_updated integer not null default 0,
    add column last_total_secret_values_reencrypted integer not null default 0,
    add column last_total_full_reencryption_count integer not null default 0,
    add column last_total_metadata_rewrap_count integer not null default 0,
    add column last_area_results_json text,
    add column last_follow_up_json text,
    add column last_verification_json text;
