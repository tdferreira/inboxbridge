ALTER TABLE user_ui_preference
    ADD COLUMN admin_quick_setup_dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN admin_quick_setup_pinned_visible BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_ui_preference
SET admin_quick_setup_dismissed = quick_setup_dismissed,
    admin_quick_setup_pinned_visible = quick_setup_pinned_visible
WHERE admin_quick_setup_dismissed = FALSE
  AND admin_quick_setup_pinned_visible = FALSE;
