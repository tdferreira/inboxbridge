CREATE TABLE source_poll_event (
    id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(100) NOT NULL,
    trigger_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    fetched_count INTEGER NOT NULL,
    imported_count INTEGER NOT NULL,
    duplicate_count INTEGER NOT NULL,
    error_message VARCHAR(4000)
);

CREATE INDEX idx_source_poll_event_source
    ON source_poll_event (source_id);

CREATE INDEX idx_source_poll_event_finished
    ON source_poll_event (finished_at);
