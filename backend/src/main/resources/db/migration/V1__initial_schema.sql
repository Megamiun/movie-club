CREATE TABLE members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_id   VARCHAR(128)              NOT NULL UNIQUE,
    email       VARCHAR(255)              NOT NULL UNIQUE,
    name        VARCHAR(255)              NOT NULL,
    avatar_url  VARCHAR(1024),
    created_at  TIMESTAMP WITH TIME ZONE  NOT NULL
);

CREATE TABLE clubs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE  NOT NULL
);

CREATE TABLE club_members (
    club_id         UUID        NOT NULL REFERENCES clubs(id),
    member_id       UUID        NOT NULL REFERENCES members(id),
    role            VARCHAR(32) NOT NULL,
    rotation_order  INTEGER     NOT NULL,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (club_id, member_id)
);

CREATE TABLE rating_scales (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id  UUID        NOT NULL REFERENCES clubs(id),
    type     VARCHAR(32) NOT NULL
);

CREATE TABLE rating_options (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scale_id  UUID         NOT NULL REFERENCES rating_scales(id),
    label     VARCHAR(64)  NOT NULL,
    position  INTEGER      NOT NULL
);

CREATE TABLE meetings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id             UUID  NOT NULL REFERENCES clubs(id),
    date                DATE  NOT NULL,
    assigned_member_id  UUID  REFERENCES members(id)
);

CREATE TABLE movies (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id                UUID          NOT NULL REFERENCES meetings(id),
    chosen_by_id              UUID          NOT NULL REFERENCES members(id),
    imdb_id                   VARCHAR(16)   NOT NULL,
    tmdb_id                   VARCHAR(16),
    original_title            VARCHAR(512)  NOT NULL,
    english_title             VARCHAR(512),
    custom_title              VARCHAR(512),
    display_title_preference  VARCHAR(16)   NOT NULL DEFAULT 'ORIGINAL',
    year                      INTEGER,
    director                  VARCHAR(512),
    runtime_minutes           INTEGER,
    genre                     VARCHAR(512),
    country                   VARCHAR(512),
    tmdb_rating               DECIMAL(4, 1),
    poster_s3_key             VARCHAR(512),
    watch_link                VARCHAR(2048),
    metadata_fetched_at       TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE member_movie_reviews (
    movie_id            UUID  NOT NULL REFERENCES movies(id),
    member_id           UUID  NOT NULL REFERENCES members(id),
    quality_option_id   UUID  REFERENCES rating_options(id),
    sentiment_option_id UUID  REFERENCES rating_options(id),
    comment             TEXT,
    PRIMARY KEY (movie_id, member_id)
);

CREATE TABLE series (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id                   UUID         NOT NULL REFERENCES clubs(id),
    chosen_by_id              UUID         NOT NULL REFERENCES members(id),
    imdb_id                   VARCHAR(16)  NOT NULL,
    tmdb_id                   VARCHAR(16),
    original_title            VARCHAR(512) NOT NULL,
    english_title             VARCHAR(512),
    custom_title              VARCHAR(512),
    display_title_preference  VARCHAR(16)  NOT NULL DEFAULT 'ORIGINAL',
    poster_s3_key             VARCHAR(512),
    metadata_fetched_at       TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE seasons (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id  UUID         NOT NULL REFERENCES series(id),
    number     INTEGER      NOT NULL,
    title      VARCHAR(512)
);

CREATE TABLE episodes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id   UUID     NOT NULL REFERENCES seasons(id),
    number      INTEGER  NOT NULL,
    title       VARCHAR(512),
    meeting_id  UUID     REFERENCES meetings(id)
);

CREATE TABLE member_series_reviews (
    series_id           UUID  NOT NULL REFERENCES series(id),
    member_id           UUID  NOT NULL REFERENCES members(id),
    quality_option_id   UUID  REFERENCES rating_options(id),
    sentiment_option_id UUID  REFERENCES rating_options(id),
    comment             TEXT,
    PRIMARY KEY (series_id, member_id)
);

CREATE TABLE member_season_reviews (
    season_id           UUID  NOT NULL REFERENCES seasons(id),
    member_id           UUID  NOT NULL REFERENCES members(id),
    quality_option_id   UUID  REFERENCES rating_options(id),
    sentiment_option_id UUID  REFERENCES rating_options(id),
    comment             TEXT,
    PRIMARY KEY (season_id, member_id)
);

CREATE TABLE member_episode_reviews (
    episode_id          UUID  NOT NULL REFERENCES episodes(id),
    member_id           UUID  NOT NULL REFERENCES members(id),
    quality_option_id   UUID  REFERENCES rating_options(id),
    sentiment_option_id UUID  REFERENCES rating_options(id),
    comment             TEXT,
    PRIMARY KEY (episode_id, member_id)
);

CREATE TABLE watchlist_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     UUID          NOT NULL REFERENCES clubs(id),
    member_id   UUID          NOT NULL REFERENCES members(id),
    title       VARCHAR(512)  NOT NULL,
    imdb_url    VARCHAR(1024),
    notes       TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
