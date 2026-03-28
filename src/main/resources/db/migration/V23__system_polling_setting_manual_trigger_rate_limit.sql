alter table system_polling_setting
    add column manual_trigger_limit_count_override integer;

alter table system_polling_setting
    add column manual_trigger_limit_window_seconds_override integer;
