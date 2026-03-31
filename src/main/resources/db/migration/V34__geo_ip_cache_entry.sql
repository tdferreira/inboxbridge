create table if not exists geo_ip_cache_entry (
    ip_address varchar(128) primary key,
    provider varchar(40),
    location_label varchar(160),
    resolution_status varchar(32) not null,
    expires_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_geo_ip_cache_entry_expires_at on geo_ip_cache_entry (expires_at);
