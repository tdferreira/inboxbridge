create table if not exists source_polling_setting (
    id bigserial primary key,
    source_id varchar(120) not null,
    owner_user_id bigint null,
    poll_enabled_override boolean null,
    poll_interval_override varchar(40) null,
    fetch_window_override integer null,
    updated_at timestamptz not null,
    constraint uk_source_polling_setting_source unique (source_id)
);

create index if not exists idx_source_polling_setting_source on source_polling_setting(source_id);
create index if not exists idx_source_polling_setting_user on source_polling_setting(owner_user_id);
