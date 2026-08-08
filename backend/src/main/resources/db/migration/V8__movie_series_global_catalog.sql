-- Splits movies/series into a global catalog (deduplicated by imdb_id) plus a per-club "pick" row, so the same
-- real movie/series/season/episode picked by two different clubs shares one set of TMDB data instead of
-- duplicating it. This is a destructive rebuild (dev DB, no production data to preserve). Each table is dropped
-- explicitly, leaf-to-root -- DROP TABLE ... CASCADE on a parent only drops the *FK constraint* on a child table,
-- not the child table itself, so relying on cascading from just `movies`/`series` would leave the review/season/
-- episode tables behind (and then fail to recreate them, since they'd already exist).
DROP TABLE member_movie_reviews CASCADE;
DROP TABLE movies CASCADE;
DROP TABLE member_episode_reviews CASCADE;
DROP TABLE episodes CASCADE;
DROP TABLE member_season_reviews CASCADE;
DROP TABLE seasons CASCADE;
DROP TABLE member_series_reviews CASCADE;
DROP TABLE series CASCADE;

-- Global movie catalog, deduplicated by imdb_id
CREATE TABLE movies (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imdb_id               VARCHAR(16)   NOT NULL UNIQUE,
    tmdb_id               VARCHAR(16),
    original_title        VARCHAR(512)  NOT NULL,
    alternative_titles    JSONB         NOT NULL DEFAULT '[]',
    year                  INTEGER,
    director              VARCHAR(512),
    runtime_minutes       INTEGER,
    genre                 VARCHAR(255)[],
    origin_country        VARCHAR(255)[],
    production_countries  VARCHAR(255)[],
    tmdb_rating           DECIMAL(4, 1),
    poster_s3_key         VARCHAR(512),
    metadata_fetched_at   TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One row per (meeting, movie) pick -- the club-specific "chose this movie for this meeting" fact
CREATE TABLE meeting_movies (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id                UUID          NOT NULL REFERENCES meetings(id),
    movie_id                  UUID          NOT NULL REFERENCES movies(id),
    chosen_by_id              UUID          NOT NULL REFERENCES members(id),
    custom_title              VARCHAR(512),
    display_title_preference  VARCHAR(16)   NOT NULL DEFAULT 'ORIGINAL',
    watch_link                VARCHAR(2048),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE member_movie_reviews (
    meeting_movie_id     UUID  NOT NULL REFERENCES meeting_movies(id),
    member_id            UUID  NOT NULL REFERENCES members(id),
    quality_option_id    UUID  REFERENCES rating_options(id),
    sentiment_option_id  UUID  REFERENCES rating_options(id),
    comment              TEXT,
    PRIMARY KEY (meeting_movie_id, member_id)
);

-- Global series catalog, deduplicated by imdb_id
CREATE TABLE series (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imdb_id               VARCHAR(16)   NOT NULL UNIQUE,
    tmdb_id               VARCHAR(16),
    original_title        VARCHAR(512)  NOT NULL,
    alternative_titles    JSONB         NOT NULL DEFAULT '[]',
    year                  INTEGER,
    genre                 VARCHAR(255)[],
    origin_country        VARCHAR(255)[],
    production_countries  VARCHAR(255)[],
    tmdb_rating           DECIMAL(4, 1),
    creator               VARCHAR(512),
    poster_s3_key         VARCHAR(512),
    metadata_fetched_at   TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One row per (club, series) pick
CREATE TABLE club_series (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id                   UUID          NOT NULL REFERENCES clubs(id),
    series_id                 UUID          NOT NULL REFERENCES series(id),
    chosen_by_id              UUID          NOT NULL REFERENCES members(id),
    custom_title              VARCHAR(512),
    display_title_preference  VARCHAR(16)   NOT NULL DEFAULT 'ORIGINAL',
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One review per member per global series, regardless of which club's pick they watched it through
CREATE TABLE member_series_reviews (
    series_id            UUID  NOT NULL REFERENCES series(id),
    member_id            UUID  NOT NULL REFERENCES members(id),
    quality_option_id    UUID  REFERENCES rating_options(id),
    sentiment_option_id  UUID  REFERENCES rating_options(id),
    comment              TEXT,
    PRIMARY KEY (series_id, member_id)
);

-- Global season catalog, deduplicated by (series_id, number)
CREATE TABLE seasons (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id  UUID    NOT NULL REFERENCES series(id),
    number     INTEGER NOT NULL,
    title      VARCHAR(512),
    UNIQUE (series_id, number)
);

CREATE TABLE member_season_reviews (
    season_id            UUID  NOT NULL REFERENCES seasons(id),
    member_id            UUID  NOT NULL REFERENCES members(id),
    quality_option_id    UUID  REFERENCES rating_options(id),
    sentiment_option_id  UUID  REFERENCES rating_options(id),
    comment              TEXT,
    PRIMARY KEY (season_id, member_id)
);

-- Global episode catalog, deduplicated by (season_id, number)
CREATE TABLE episodes (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id             UUID    NOT NULL REFERENCES seasons(id),
    number                INTEGER NOT NULL,
    title                 VARCHAR(512),
    air_date              DATE,
    overview              TEXT,
    runtime_minutes       INTEGER,
    director              VARCHAR(512),
    tmdb_rating           DECIMAL(4, 1),
    metadata_fetched_at   TIMESTAMP WITH TIME ZONE,
    UNIQUE (season_id, number)
);

-- One row per club's meeting assignment of a global episode -- multiple clubs can independently schedule the
-- same episode to their own (different) meetings
CREATE TABLE meeting_episodes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id  UUID  NOT NULL REFERENCES meetings(id),
    episode_id  UUID  NOT NULL REFERENCES episodes(id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (meeting_id, episode_id)
);

CREATE TABLE member_episode_reviews (
    episode_id            UUID  NOT NULL REFERENCES episodes(id),
    member_id             UUID  NOT NULL REFERENCES members(id),
    quality_option_id     UUID  REFERENCES rating_options(id),
    sentiment_option_id   UUID  REFERENCES rating_options(id),
    comment               TEXT,
    PRIMARY KEY (episode_id, member_id)
);
