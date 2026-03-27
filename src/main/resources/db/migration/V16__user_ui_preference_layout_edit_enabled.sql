alter table user_ui_preference
    add column if not exists layout_edit_enabled boolean not null default false;
