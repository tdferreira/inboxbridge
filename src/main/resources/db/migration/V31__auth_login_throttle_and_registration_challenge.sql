create table if not exists auth_login_throttle_state (
    id bigserial primary key,
    client_key varchar(180) not null unique,
    failure_count integer not null default 0,
    lockout_count integer not null default 0,
    blocked_until timestamp with time zone,
    updated_at timestamp with time zone not null
);

create index if not exists idx_auth_login_throttle_blocked_until on auth_login_throttle_state(blocked_until);

create table if not exists registration_challenge (
    id bigserial primary key,
    challenge_token varchar(80) not null unique,
    prompt varchar(255) not null,
    answer_hash varchar(128) not null,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null
);

create index if not exists idx_registration_challenge_expires_at on registration_challenge(expires_at);
