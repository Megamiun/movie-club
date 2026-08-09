-- DisplayTitlePreference dropped ENGLISH (never actually resolved anywhere -- LANGUAGE + display_language_code is a
-- strict superset). Existing rows can still hold the literal string 'ENGLISH', which Exposed's enumerationByName
-- throws on since it no longer maps to any enum constant. Reset to the default; lossless, since ENGLISH was never
-- functionally different from ORIGINAL before this change.
UPDATE meeting_movies SET display_title_preference = 'ORIGINAL' WHERE display_title_preference = 'ENGLISH';
UPDATE club_series SET display_title_preference = 'ORIGINAL' WHERE display_title_preference = 'ENGLISH';
