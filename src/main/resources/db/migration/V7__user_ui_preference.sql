CREATE TABLE user_ui_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    persist_layout BOOLEAN NOT NULL,
    quick_setup_collapsed BOOLEAN NOT NULL,
    gmail_destination_collapsed BOOLEAN NOT NULL,
    source_bridges_collapsed BOOLEAN NOT NULL,
    system_dashboard_collapsed BOOLEAN NOT NULL,
    user_management_collapsed BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_user_ui_preference_user
    ON user_ui_preference (user_id);

CREATE INDEX idx_user_ui_preference_user
    ON user_ui_preference (user_id);
