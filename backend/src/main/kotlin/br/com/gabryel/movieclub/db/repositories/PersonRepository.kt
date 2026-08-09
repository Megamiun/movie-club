package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.PersonRow

interface PersonRepository {
    /** Finds an existing [PersonRow] by [tmdbId] if given (preferred -- always known from TMDB credits, even before
     * an IMDB id is resolved), else by [imdbId] if given, refreshing its [name]/ids; otherwise creates one. Mirrors
     * [MediaItemRepository.findOrCreate]'s find-or-create-by-id pattern. */
    fun findOrCreate(name: String, tmdbId: String? = null, imdbId: String? = null): PersonRow
}
