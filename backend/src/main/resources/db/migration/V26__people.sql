CREATE TABLE people (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(512) NOT NULL,
    imdb_id VARCHAR(16) UNIQUE,
    tmdb_id VARCHAR(16) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Backfill from every existing director/creator. A director with a resolved imdb_id gets one row per imdb_id;
-- everything else (directors never refreshed since V19, and every series creator -- TMDB creator ids weren't
-- captured before this migration) gets one row per distinct name. Early-stage dev data, so name-based dedup for
-- the no-imdb-id case is an accepted approximation, not a concern (same tradeoff V12's MediaItem backfill made).
INSERT INTO people (id, name, imdb_id, created_at)
SELECT gen_random_uuid(), director, director_imdb_id, now()
FROM movies
WHERE director IS NOT NULL AND director_imdb_id IS NOT NULL
ON CONFLICT (imdb_id) DO NOTHING;

INSERT INTO people (id, name, imdb_id, created_at)
SELECT gen_random_uuid(), director, director_imdb_id, now()
FROM episodes
WHERE director IS NOT NULL AND director_imdb_id IS NOT NULL
ON CONFLICT (imdb_id) DO NOTHING;

INSERT INTO people (id, name, created_at)
SELECT gen_random_uuid(), d.director, now()
FROM (
    SELECT DISTINCT director FROM movies WHERE director IS NOT NULL AND director_imdb_id IS NULL
    UNION
    SELECT DISTINCT director FROM episodes WHERE director IS NOT NULL AND director_imdb_id IS NULL
) d
WHERE NOT EXISTS (SELECT 1 FROM people p WHERE p.name = d.director);

INSERT INTO people (id, name, created_at)
SELECT gen_random_uuid(), s.creator, now()
FROM (SELECT DISTINCT creator FROM series WHERE creator IS NOT NULL) s
WHERE NOT EXISTS (SELECT 1 FROM people p WHERE p.name = s.creator);

ALTER TABLE movies ADD COLUMN director_person_id UUID REFERENCES people (id);
UPDATE movies
SET director_person_id = people.id
FROM people
WHERE movies.director IS NOT NULL
  AND (
    (movies.director_imdb_id IS NOT NULL AND people.imdb_id = movies.director_imdb_id)
    OR (movies.director_imdb_id IS NULL AND people.name = movies.director AND people.imdb_id IS NULL)
  );
ALTER TABLE movies DROP COLUMN director, DROP COLUMN director_imdb_id;

ALTER TABLE episodes ADD COLUMN director_person_id UUID REFERENCES people (id);
UPDATE episodes
SET director_person_id = people.id
FROM people
WHERE episodes.director IS NOT NULL
  AND (
    (episodes.director_imdb_id IS NOT NULL AND people.imdb_id = episodes.director_imdb_id)
    OR (episodes.director_imdb_id IS NULL AND people.name = episodes.director AND people.imdb_id IS NULL)
  );
ALTER TABLE episodes DROP COLUMN director, DROP COLUMN director_imdb_id;

ALTER TABLE series ADD COLUMN creator_person_id UUID REFERENCES people (id);
UPDATE series
SET creator_person_id = people.id
FROM people
WHERE series.creator IS NOT NULL AND people.name = series.creator AND people.imdb_id IS NULL;
ALTER TABLE series DROP COLUMN creator;
