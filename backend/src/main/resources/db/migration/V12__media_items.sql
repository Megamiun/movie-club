CREATE TABLE media_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(16) NOT NULL,
    imdb_id VARCHAR(16) NOT NULL UNIQUE,
    tmdb_id VARCHAR(16),
    title VARCHAR(512) NOT NULL,
    year INTEGER,
    poster_url VARCHAR(1024),
    tmdb_rating DECIMAL(4, 1),
    imdb_rating DECIMAL(4, 1),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Backfill from the existing Movie/Series catalogs so every already-picked movie/series gets a MediaItem too.
INSERT INTO media_items (id, type, imdb_id, tmdb_id, title, year, tmdb_rating, imdb_rating, created_at)
SELECT gen_random_uuid(), 'MOVIE', imdb_id, tmdb_id, original_title, year, tmdb_rating, imdb_rating, created_at
FROM movies
ON CONFLICT (imdb_id) DO NOTHING;

INSERT INTO media_items (id, type, imdb_id, tmdb_id, title, year, tmdb_rating, imdb_rating, created_at)
SELECT gen_random_uuid(), 'SERIES', imdb_id, tmdb_id, original_title, year, tmdb_rating, imdb_rating, created_at
FROM series
ON CONFLICT (imdb_id) DO NOTHING;

ALTER TABLE movies
    ADD COLUMN media_item_id UUID REFERENCES media_items (id);

UPDATE movies
SET media_item_id = media_items.id
FROM media_items
WHERE media_items.imdb_id = movies.imdb_id
  AND media_items.type = 'MOVIE';

ALTER TABLE series
    ADD COLUMN media_item_id UUID REFERENCES media_items (id);

UPDATE series
SET media_item_id = media_items.id
FROM media_items
WHERE media_items.imdb_id = series.imdb_id
  AND media_items.type = 'SERIES';

-- Watchlist entries move from freeform title/url to referencing a MediaItem directly. Existing entries were
-- freeform (pre-dating this feature) and can't be reliably matched to a MediaItem, so they don't carry forward --
-- this is early-stage dev data, not a real user's list.
DELETE FROM watchlist_entries;

ALTER TABLE watchlist_entries
    DROP COLUMN title,
    DROP COLUMN imdb_url;

ALTER TABLE watchlist_entries
    ADD COLUMN media_item_id UUID NOT NULL REFERENCES media_items (id),
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0;
