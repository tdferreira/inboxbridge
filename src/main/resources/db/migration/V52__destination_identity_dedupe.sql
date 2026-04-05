alter table imported_message
    add column if not exists destination_identity_key varchar(160);

update imported_message
set destination_identity_key = destination_key
where destination_identity_key is null;

alter table imported_message
    alter column destination_identity_key set not null;

alter table imported_message
    drop constraint if exists uk_imported_message_destination_source_key;

alter table imported_message
    drop constraint if exists uk_imported_message_destination_sha;

alter table imported_message
    add constraint uk_imported_message_destination_identity_source_key
        unique (destination_identity_key, source_account_id, source_message_key);

alter table imported_message
    add constraint uk_imported_message_destination_identity_sha
        unique (destination_identity_key, raw_sha256);

create index if not exists idx_imported_message_destination_identity
    on imported_message(destination_identity_key);
