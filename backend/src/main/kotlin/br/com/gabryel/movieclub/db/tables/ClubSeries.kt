package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

/** One row per (club, series) pick -- the club-specific "chose to follow this series" fact, distinct from
 * [Series]' shared TMDB catalog data. This is what the API's external `seriesId` refers to. */
object ClubSeries : UuidTable("club_series") {
    val clubId = reference("club_id", Clubs)
    val seriesId = reference("series_id", Series)
    val chosenById = reference("chosen_by_id", Members)

    val customTitle = varchar("custom_title", 512).nullable()
    val displayTitlePreference = enumerationByName<DisplayTitlePreference>("display_title_preference", 16)
    val createdAt = timestamp("created_at")
}
