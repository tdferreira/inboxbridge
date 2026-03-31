alter table if exists system_auth_security_setting
    add column if not exists geo_ip_enabled_override boolean,
    add column if not exists geo_ip_primary_provider_override varchar(40),
    add column if not exists geo_ip_fallback_providers_override varchar(200),
    add column if not exists geo_ip_cache_ttl_override varchar(40),
    add column if not exists geo_ip_provider_cooldown_override varchar(40),
    add column if not exists geo_ip_request_timeout_override varchar(40);
