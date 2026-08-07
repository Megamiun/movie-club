package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.RatingScaleType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object RatingScales : UuidTable("rating_scales") {
    val clubId = reference("club_id", Clubs)
    val type = enumerationByName<RatingScaleType>("type", 32)
}
