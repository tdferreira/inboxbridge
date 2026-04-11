create table if not exists auth_registration_throttle_state (
    id bigserial primary key,
    client_key varchar(180) not null unique,
    failure_count integer not null default 0,
    lockout_count integer not null default 0,
    blocked_until timestamp with time zone,
    updated_at timestamp with time zone not null
);

create index if not exists idx_auth_registration_throttle_blocked_until
    on auth_registration_throttle_state(blocked_until);
