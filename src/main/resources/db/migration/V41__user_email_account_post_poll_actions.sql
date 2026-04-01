alter table user_email_account
    add column if not exists mark_read_after_poll boolean not null default false;

alter table user_email_account
    add column if not exists post_poll_action varchar(20) not null default 'NONE';

alter table user_email_account
    add column if not exists post_poll_target_folder varchar(255);
