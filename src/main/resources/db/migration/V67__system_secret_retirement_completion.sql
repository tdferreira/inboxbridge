alter table system_secret_retirement_review
    add column completion_verified_at timestamp with time zone,
    add column completion_verified_by_user_id bigint,
    add column completion_verified_by_username varchar(120),
    add column completion_status varchar(32),
    add column completion_message varchar(500),
    add column completion_unsatisfied_check_ids_json text,
    add column completion_snapshot_json text;
