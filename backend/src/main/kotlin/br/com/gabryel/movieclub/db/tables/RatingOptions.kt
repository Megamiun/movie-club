package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object RatingOptions : UuidTable("rating_options") {
    val scaleId = reference("scale_id", RatingScales)
    val label = varchar("label", 64)
    val position = integer("position")
}
