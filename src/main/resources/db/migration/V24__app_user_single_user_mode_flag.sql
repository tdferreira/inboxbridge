alter table app_user
    add column if not exists disabled_by_single_user_mode boolean not null default false;
