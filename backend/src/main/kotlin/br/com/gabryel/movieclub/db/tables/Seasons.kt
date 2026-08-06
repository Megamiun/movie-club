package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object Seasons : UUIDTable("seasons") {
    val seriesId = reference("series_id", Series)
    val number = integer("number")
    val title = varchar("title", 512).nullable()
}
