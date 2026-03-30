alter table system_polling_setting
    add column if not exists source_host_min_spacing_override varchar(40),
    add column if not exists source_host_max_concurrency_override integer,
    add column if not exists destination_provider_min_spacing_override varchar(40),
    add column if not exists destination_provider_max_concurrency_override integer,
    add column if not exists throttle_lease_ttl_override varchar(40),
    add column if not exists adaptive_throttle_max_multiplier_override integer,
    add column if not exists success_jitter_ratio_override double precision,
    add column if not exists max_success_jitter_override varchar(40);
