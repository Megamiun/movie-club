-- Application-level checks already reject a second add of the same pick, but a plain select-then-insert leaves a
-- race window (e.g. a double-submitted request) where two rows can still slip through. These unique constraints
-- close that gap for good, matching the same pattern already used by movies.imdb_id, series.imdb_id,
-- seasons(series_id, number), and episodes(season_id, number).

-- Keep the earliest row (by created_at, tie-broken by id) of each duplicate group, drop the rest.
DELETE FROM club_series a USING club_series b
WHERE a.club_id = b.club_id
  AND a.series_id = b.series_id
  AND (a.created_at, a.id) > (b.created_at, b.id);

ALTER TABLE club_series ADD CONSTRAINT club_series_club_id_series_id_key UNIQUE (club_id, series_id);

DELETE FROM meeting_movies a USING meeting_movies b
WHERE a.meeting_id = b.meeting_id
  AND a.movie_id = b.movie_id
  AND (a.created_at, a.id) > (b.created_at, b.id);

ALTER TABLE meeting_movies ADD CONSTRAINT meeting_movies_meeting_id_movie_id_key UNIQUE (meeting_id, movie_id);

DELETE FROM watchlist_entries a USING watchlist_entries b
WHERE a.club_id = b.club_id
  AND a.member_id = b.member_id
  AND a.media_item_id = b.media_item_id
  AND (a.created_at, a.id) > (b.created_at, b.id);

ALTER TABLE watchlist_entries
    ADD CONSTRAINT watchlist_entries_club_id_member_id_media_item_id_key UNIQUE (club_id, member_id, media_item_id);
