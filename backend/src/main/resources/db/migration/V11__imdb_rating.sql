ALTER TABLE movies
    ADD COLUMN imdb_rating DECIMAL(4, 1);

ALTER TABLE series
    ADD COLUMN imdb_rating DECIMAL(4, 1);
