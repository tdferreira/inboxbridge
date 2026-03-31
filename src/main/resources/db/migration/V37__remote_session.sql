create table remote_session (
    id bigserial primary key,
    user_id bigint not null references app_user (id) on delete cascade,
    token_hash varchar(128) not null,
    csrf_token_hash varchar(128) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    client_ip varchar(128),
    location_label varchar(160),
    user_agent varchar(512),
    login_method varchar(32) not null,
    revoked_at timestamp with time zone
);

create unique index uk_remote_session_token_hash on remote_session (token_hash);
create index idx_remote_session_user on remote_session (user_id);
create index idx_remote_session_expires on remote_session (expires_at);
create index idx_remote_session_revoked on remote_session (revoked_at);
