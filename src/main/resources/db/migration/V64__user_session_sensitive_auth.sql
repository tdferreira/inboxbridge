alter table user_session
    add column if not exists last_sensitive_auth_at timestamp with time zone;
