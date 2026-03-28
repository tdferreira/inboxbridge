alter table system_oauth_app_settings
    add column if not exists multi_user_enabled_override boolean;
