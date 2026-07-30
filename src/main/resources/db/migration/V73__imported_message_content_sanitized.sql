alter table imported_message
    add column content_sanitized boolean not null default false;
