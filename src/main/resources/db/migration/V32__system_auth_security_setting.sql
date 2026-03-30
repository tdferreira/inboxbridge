create table if not exists system_auth_security_setting (
    id bigint primary key,
    login_failure_threshold_override integer,
    login_initial_block_override varchar(40),
    login_max_block_override varchar(40),
    registration_challenge_enabled_override boolean,
    registration_challenge_ttl_override varchar(40),
    updated_at timestamptz not null
);
