create table system_secret_reencryption_request (
    id bigint primary key,
    status varchar(32) not null,
    requested_at timestamp with time zone,
    requested_by_user_id bigint,
    execute_after timestamp with time zone,
    immediate_execution_override boolean not null default false,
    revoke_browser_extension_sessions boolean not null default false,
    revoke_remote_sessions boolean not null default false,
    clear_cached_oauth_access_tokens boolean not null default false,
    last_started_at timestamp with time zone,
    last_completed_at timestamp with time zone,
    last_failed_at timestamp with time zone,
    last_error_message varchar(500),
    last_result_message varchar(500),
    last_verification_passed boolean
);
