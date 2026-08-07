-- Inserts a fully registered dummy member for local dev.
-- credentials: email=admin@example.com, password=hunter2

INSERT INTO members (id, email, name, password_hash, invite_token, created_at)
VALUES (
    gen_random_uuid(),
    'admin@example.com',
    'Admin',
    '$argon2id$v=19$m=16384,t=2,p=1$Wnhkb2VqUXhMbkMrVEs4OWloVjREUT09$OgHvCEVBRYYm0YXhLxoPY93yucqWQT1+oi2HrUBOE8w',
    null,
    now()
)
ON CONFLICT (email) DO NOTHING;
