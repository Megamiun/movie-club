ALTER TABLE series
    ADD COLUMN year INTEGER,
    ADD COLUMN genre        VARCHAR(255)[],
    ADD COLUMN country      VARCHAR(255)[],
    ADD COLUMN tmdb_rating  DECIMAL(4, 1),
    ADD COLUMN creator      VARCHAR(512);
