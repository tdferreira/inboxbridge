create index if not exists idx_imported_message_destination_identity_message_id
    on imported_message(destination_identity_key, source_account_id, message_id_header);
