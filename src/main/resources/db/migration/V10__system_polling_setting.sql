CREATE TABLE system_polling_setting (
    id BIGINT PRIMARY KEY,
    poll_enabled_override BOOLEAN,
    poll_interval_override VARCHAR(32),
    fetch_window_override INTEGER,
    updated_at TIMESTAMPTZ NOT NULL
);
