create table if not exists extension_session (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    label varchar(120) not null,
    browser_family varchar(32) not null,
    extension_version varchar(32) not null,
    token_hash varchar(128) not null,
    token_prefix varchar(24) not null,
    created_at timestamp with time zone not null,
    last_used_at timestamp with time zone,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone
);

create unique index if not exists uk_extension_session_token_hash
    on extension_session(token_hash);

create index if not exists idx_extension_session_user
    on extension_session(user_id);

create index if not exists idx_extension_session_revoked
    on extension_session(revoked_at);
