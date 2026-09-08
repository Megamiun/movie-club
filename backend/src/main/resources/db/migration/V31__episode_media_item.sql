ALTER TABLE episodes
    ADD COLUMN media_item_id UUID REFERENCES media_items (id);

-- Backfills episodes whose imdb_id was already resolved by a prior refresh -- everything else picks up its
-- media_item_id the next time it's refreshed (see EpisodeService.refreshCatalogMetadata), same as a brand new row.
INSERT INTO media_items (id, type, imdb_id, title, tmdb_id, year, poster_url, imdb_rating, created_at)
SELECT gen_random_uuid(), 'EPISODE', e.imdb_id, e.title, NULL, NULL, NULL, e.imdb_rating, now()
FROM episodes e
WHERE e.imdb_id IS NOT NULL AND e.title IS NOT NULL
ON CONFLICT (imdb_id) DO NOTHING;

UPDATE episodes
SET media_item_id = media_items.id
FROM media_items
WHERE episodes.imdb_id = media_items.imdb_id
  AND episodes.imdb_id IS NOT NULL;
