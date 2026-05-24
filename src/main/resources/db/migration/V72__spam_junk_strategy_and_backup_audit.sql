alter table user_email_account
    add column if not exists spam_junk_strategy varchar(40) not null default 'IGNORE';

alter table user_email_account
    add column if not exists spam_junk_source_folder varchar(255);

alter table user_mail_destination_config
    add column if not exists spam_junk_folder_name varchar(255);

create table if not exists config_backup_export_audit (
    id bigserial primary key,
    actor_user_id bigint not null,
    actor_username varchar(255) not null,
    export_type varchar(40) not null,
    encrypted boolean not null,
    public_key_fingerprint varchar(128),
    created_at timestamp with time zone not null
);

create index if not exists idx_config_backup_export_audit_actor
    on config_backup_export_audit(actor_user_id);

create index if not exists idx_config_backup_export_audit_created
    on config_backup_export_audit(created_at);
