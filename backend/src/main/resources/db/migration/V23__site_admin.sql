ALTER TABLE members ADD COLUMN is_site_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE members SET is_site_admin = TRUE
WHERE id = (SELECT id FROM members ORDER BY created_at ASC LIMIT 1);
