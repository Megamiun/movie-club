package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object RatingOptions : UUIDTable("rating_options") {
    val scaleId = reference("scale_id", RatingScales)
    val label = varchar("label", 64)
    val position = integer("position")
}
