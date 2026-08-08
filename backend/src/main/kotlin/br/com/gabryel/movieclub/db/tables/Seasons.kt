package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/** Global season catalog, deduplicated by ([seriesId], [number]) -- shared by every club following that series. */
object Seasons : UuidTable("seasons") {
    val seriesId = reference("series_id", Series)
    val number = integer("number")
    val title = varchar("title", 512).nullable()

    init {
        uniqueIndex(seriesId, number)
    }
}
