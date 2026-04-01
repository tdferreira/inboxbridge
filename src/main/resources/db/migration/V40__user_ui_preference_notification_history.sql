alter table user_ui_preference
    add column if not exists notification_history text not null default '[]';
