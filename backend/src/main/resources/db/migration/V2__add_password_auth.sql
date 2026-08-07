ALTER TABLE members
    ADD COLUMN password_hash VARCHAR(255),
    DROP COLUMN google_id;
