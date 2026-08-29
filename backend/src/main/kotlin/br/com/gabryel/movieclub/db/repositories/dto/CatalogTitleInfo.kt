package br.com.gabryel.movieclub.db.repositories.dto

/** The subset of a Movie/Series global catalog row's fields the frontend's language-aware title resolution
 * (`resolveTitle`, `frontend/src/utils/title.ts`) actually needs -- `originalLanguage`/`translations`, looked up by
 * the catalog row's own `media_item_id` rather than its own id. Used to give a [WatchlistEntryRow] (which
 * references a MediaItem directly, with no per-pick `customTitle`/`displayTitlePreference`/`displayLanguageCode` of
 * its own) enough to resolve a language-aware display title the same way a Movie/Series pick does, minus the
 * per-pick override fields a Watchlist entry has no storage for. */
data class CatalogTitleInfo(
    val originalLanguage: String? = null,
    val translations: List<Translation> = emptyList(),
)
