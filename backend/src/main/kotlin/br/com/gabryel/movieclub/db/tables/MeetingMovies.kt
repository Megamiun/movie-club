package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

/** One row per (meeting, movie) pick -- the club-specific "chose this movie for this meeting" fact, distinct from
 * [Movies]' shared TMDB catalog data. */
object MeetingMovies : UuidTable("meeting_movies") {
    val meetingId = reference("meeting_id", Meetings)
    val movieId = reference("movie_id", Movies)
    val chosenById = reference("chosen_by_id", Members)

    val customTitle = varchar("custom_title", 512).nullable()
    val displayTitlePreference = enumerationByName<DisplayTitlePreference>("display_title_preference", 16)
    val watchLink = varchar("watch_link", 2048).nullable()
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(meetingId, movieId)
    }
}
