alter table source_polling_state
    add column if not exists imap_checkpoint_destination_key varchar(160);

alter table source_polling_state
    add column if not exists pop_checkpoint_destination_key varchar(160);
