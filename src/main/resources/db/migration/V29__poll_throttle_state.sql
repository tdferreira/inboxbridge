create table if not exists poll_throttle_state (
    id bigserial primary key,
    throttle_key varchar(180) not null unique,
    throttle_kind varchar(40) not null,
    next_allowed_at timestamp with time zone,
    adaptive_multiplier integer not null default 1,
    updated_at timestamp with time zone not null
);

create index if not exists idx_poll_throttle_state_key on poll_throttle_state(throttle_key);
create index if not exists idx_poll_throttle_state_next_allowed on poll_throttle_state(next_allowed_at);

create table if not exists poll_throttle_lease (
    id bigserial primary key,
    throttle_key varchar(180) not null,
    lease_token varchar(80) not null unique,
    acquired_at timestamp with time zone not null,
    expires_at timestamp with time zone not null
);

create index if not exists idx_poll_throttle_lease_key on poll_throttle_lease(throttle_key);
create index if not exists idx_poll_throttle_lease_expiry on poll_throttle_lease(expires_at);
