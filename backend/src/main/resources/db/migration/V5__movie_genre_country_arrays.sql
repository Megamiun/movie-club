ALTER TABLE movies
    ALTER COLUMN genre TYPE VARCHAR(255)[] USING CASE WHEN genre IS NULL THEN NULL ELSE string_to_array(genre, ', ') END,
    ALTER COLUMN country TYPE VARCHAR(255)[] USING CASE WHEN country IS NULL THEN NULL ELSE string_to_array(country, ', ') END;