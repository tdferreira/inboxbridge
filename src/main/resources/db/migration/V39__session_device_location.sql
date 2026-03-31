alter table user_session
    add column if not exists device_latitude double precision,
    add column if not exists device_longitude double precision,
    add column if not exists device_accuracy_meters double precision,
    add column if not exists device_location_label varchar(160),
    add column if not exists device_location_captured_at timestamp with time zone;

alter table remote_session
    add column if not exists device_latitude double precision,
    add column if not exists device_longitude double precision,
    add column if not exists device_accuracy_meters double precision,
    add column if not exists device_location_label varchar(160),
    add column if not exists device_location_captured_at timestamp with time zone;
