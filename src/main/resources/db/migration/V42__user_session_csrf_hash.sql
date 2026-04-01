ALTER TABLE user_session
    ADD COLUMN csrf_token_hash VARCHAR(128);

UPDATE user_session
SET csrf_token_hash = token_hash
WHERE csrf_token_hash IS NULL;

ALTER TABLE user_session
    ALTER COLUMN csrf_token_hash SET NOT NULL;
