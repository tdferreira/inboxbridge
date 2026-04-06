alter table source_poll_event
    add column if not exists failure_category varchar(40),
    add column if not exists cooldown_backoff_millis bigint,
    add column if not exists cooldown_until timestamptz,
    add column if not exists source_throttle_wait_millis bigint,
    add column if not exists source_throttle_multiplier_after integer,
    add column if not exists source_throttle_next_allowed_at timestamptz,
    add column if not exists destination_throttle_wait_millis bigint,
    add column if not exists destination_throttle_multiplier_after integer,
    add column if not exists destination_throttle_next_allowed_at timestamptz;
