ALTER TABLE members
    ADD COLUMN username VARCHAR(32);

ALTER TABLE members
    ADD CONSTRAINT members_username_key UNIQUE (username);
