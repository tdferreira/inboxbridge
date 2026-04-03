ALTER TABLE source_poll_event
    ADD COLUMN actor_username VARCHAR(120);

ALTER TABLE source_poll_event
    ADD COLUMN execution_surface VARCHAR(40);
