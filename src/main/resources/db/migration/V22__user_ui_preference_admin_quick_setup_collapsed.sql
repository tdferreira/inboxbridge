alter table user_ui_preference
    add column if not exists admin_quick_setup_collapsed boolean not null default false;
