-- V14 renamed alternative_titles -> translations, but reinterpreted the JSON shape: alternative_titles was
-- keyed per-country ({isoCode, title, type}), translations is keyed per-language ({languageCode, countryCode,
-- englishName, title}) -- not a valid 1:1 conversion. Existing rows still hold the old shape and fail to
-- deserialize. Reset to empty; every row refreshes on its next TMDB fetch (add/refresh-metadata), same as any
-- other TMDB-sourced field.
UPDATE movies SET translations = '[]';
UPDATE series SET translations = '[]';
