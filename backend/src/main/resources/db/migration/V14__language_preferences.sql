-- Club-level language preferences used to resolve a display title when a pick doesn't have an explicit CUSTOM
-- title or LANGUAGE override (see DisplayTitlePreference).
ALTER TABLE clubs ADD COLUMN preferred_languages TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE clubs ADD COLUMN ignored_languages TEXT[] NOT NULL DEFAULT '{}';

-- alternative_titles (TMDB's per-country title list) is replaced by translations (TMDB's per-language title list,
-- keyed the way title resolution actually needs -- by language, not country). Existing values are stale/unused
-- either way (nothing ever read alternative_titles for resolution), so a straight rename + reinterpretation of the
-- JSON shape is safe; both refresh on next TMDB fetch.
ALTER TABLE movies RENAME COLUMN alternative_titles TO translations;
ALTER TABLE series RENAME COLUMN alternative_titles TO translations;

ALTER TABLE movies ADD COLUMN original_language VARCHAR(8);
ALTER TABLE series ADD COLUMN original_language VARCHAR(8);

-- Per-pick "show me this specific language's title" override, used when display_title_preference = LANGUAGE.
ALTER TABLE meeting_movies ADD COLUMN display_language_code VARCHAR(8);
ALTER TABLE club_series ADD COLUMN display_language_code VARCHAR(8);
