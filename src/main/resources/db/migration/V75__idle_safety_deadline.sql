ALTER TABLE system_polling_setting ADD COLUMN idle_safety_poll_interval_override VARCHAR(40);
ALTER TABLE source_polling_setting ADD COLUMN idle_safety_poll_interval_override VARCHAR(40);
ALTER TABLE source_polling_setting ADD COLUMN idle_renewal_interval_override VARCHAR(40);
