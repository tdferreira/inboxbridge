alter table extension_session
    add column if not exists access_expires_at timestamp with time zone;

alter table extension_session
    add column if not exists refresh_token_hash varchar(128);

create unique index if not exists uk_extension_session_refresh_token_hash
    on extension_session(refresh_token_hash)
    where refresh_token_hash is not null;

create index if not exists idx_extension_session_access_expires
    on extension_session(access_expires_at);

create index if not exists idx_extension_session_expires
    on extension_session(expires_at);
