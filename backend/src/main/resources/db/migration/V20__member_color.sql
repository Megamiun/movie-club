ALTER TABLE club_members ADD COLUMN color VARCHAR(7);

WITH palette (idx, hex) AS (
    VALUES (0, '#EF5350'), (1, '#42A5F5'), (2, '#66BB6A'), (3, '#FFCA28'),
           (4, '#AB47BC'), (5, '#26A69A'), (6, '#EC407A'), (7, '#8D6E63')
),
ranked AS (
    SELECT club_id, member_id, ROW_NUMBER() OVER (PARTITION BY club_id ORDER BY rotation_order) - 1 AS rn
    FROM club_members
)
UPDATE club_members cm
SET color = palette.hex
FROM ranked r
JOIN palette ON palette.idx = r.rn % 8
WHERE cm.club_id = r.club_id AND cm.member_id = r.member_id;
