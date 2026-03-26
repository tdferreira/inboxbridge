create table if not exists user_polling_setting (
    id bigserial primary key,
    user_id bigint not null unique,
    poll_enabled_override boolean,
    poll_interval_override varchar(40),
    fetch_window_override integer,
    updated_at timestamp with time zone not null
);

create index if not exists idx_user_polling_setting_user on user_polling_setting(user_id);

create table if not exists source_polling_state (
    id bigserial primary key,
    source_id varchar(120) not null unique,
    next_poll_at timestamp with time zone,
    cooldown_until timestamp with time zone,
    consecutive_failures integer not null default 0,
    last_failure_reason varchar(4000),
    last_failure_at timestamp with time zone,
    last_success_at timestamp with time zone,
    updated_at timestamp with time zone not null
);

create index if not exists idx_source_polling_state_source on source_polling_state(source_id);
create index if not exists idx_source_polling_state_next_poll on source_polling_state(next_poll_at);
create index if not exists idx_source_polling_state_cooldown on source_polling_state(cooldown_until);

alter table user_ui_preference
    add column if not exists user_polling_collapsed boolean not null default false;
