create table if not exists source_imap_checkpoint (
    id bigserial primary key,
    source_id varchar(120) not null,
    destination_key varchar(160) not null,
    folder_name varchar(255) not null,
    uid_validity bigint not null,
    last_seen_uid bigint not null,
    updated_at timestamptz not null,
    constraint uk_source_imap_checkpoint_scope unique (source_id, destination_key, folder_name)
);

create index if not exists idx_source_imap_checkpoint_source
    on source_imap_checkpoint(source_id);

create index if not exists idx_source_imap_checkpoint_destination
    on source_imap_checkpoint(destination_key);

insert into source_imap_checkpoint (source_id, destination_key, folder_name, uid_validity, last_seen_uid, updated_at)
select
    source_id,
    imap_checkpoint_destination_key,
    imap_folder_name,
    imap_uid_validity,
    imap_last_seen_uid,
    coalesce(updated_at, now())
from source_polling_state
where imap_checkpoint_destination_key is not null
  and imap_folder_name is not null
  and imap_uid_validity is not null
  and imap_last_seen_uid is not null
on conflict (source_id, destination_key, folder_name) do nothing;
