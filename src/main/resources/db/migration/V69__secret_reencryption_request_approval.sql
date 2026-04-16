alter table system_secret_reencryption_request
    add column approval_required boolean not null default false,
    add column approved_at timestamp with time zone,
    add column approved_by_user_id bigint,
    add column approved_by_username varchar(190);
