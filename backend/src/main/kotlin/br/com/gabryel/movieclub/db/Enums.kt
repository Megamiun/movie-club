package br.com.gabryel.movieclub.db

enum class ClubRole { ADMIN, MEMBER }

enum class RatingScaleType { QUALITY, SENTIMENT }

/** LANGUAGE resolves against a pick's own `displayLanguageCode` (a specific TMDB translation, chosen by the club --
 * see [br.com.gabryel.movieclub.db.repositories.dto.Translation]) rather than a fixed language like the old
 * ENGLISH value did; ENGLISH was removed since it was never actually resolved anywhere, LANGUAGE supersedes it. */
enum class DisplayTitlePreference { ORIGINAL, CUSTOM, LANGUAGE }

enum class MediaItemType { MOVIE, SERIES, EPISODE }
