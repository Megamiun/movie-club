-- Watchlist entries move from two independently-ordered lists per member (one for movies, one for series) to one
-- mixed list. Renumbers existing positions per (club, member): movies keep their existing relative order first,
-- then series keep their existing relative order after -- per the user's own call on how to merge the two.
WITH ranked AS (
    SELECT we.id,
           row_number() OVER (
               PARTITION BY we.club_id, we.member_id
               ORDER BY (mi.type = 'SERIES'), we.position
           ) - 1 AS new_position
    FROM watchlist_entries we
             JOIN media_items mi ON mi.id = we.media_item_id
)
UPDATE watchlist_entries
SET position = ranked.new_position
FROM ranked
WHERE watchlist_entries.id = ranked.id;
