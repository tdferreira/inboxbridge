alter table user_ui_preference
    add column if not exists user_stats_collapsed boolean not null default false;

alter table user_ui_preference
    add column if not exists global_stats_collapsed boolean not null default false;
