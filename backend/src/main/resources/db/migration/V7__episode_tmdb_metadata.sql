ALTER TABLE episodes
    ADD COLUMN air_date          DATE,
    ADD COLUMN overview          TEXT,
    ADD COLUMN runtime_minutes   INTEGER,
    ADD COLUMN director          VARCHAR(512),
    ADD COLUMN tmdb_rating       DECIMAL(4, 1),
    ADD COLUMN metadata_fetched_at TIMESTAMP WITH TIME ZONE;
