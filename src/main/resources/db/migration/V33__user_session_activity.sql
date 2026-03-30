alter table if exists user_session
    add column if not exists client_ip varchar(128),
    add column if not exists location_label varchar(160),
    add column if not exists user_agent varchar(512),
    add column if not exists login_method varchar(32) not null default 'PASSWORD',
    add column if not exists revoked_at timestamptz;

create index if not exists idx_user_session_revoked on user_session (revoked_at);
