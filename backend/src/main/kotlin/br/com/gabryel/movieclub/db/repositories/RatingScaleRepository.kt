package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.RatingScaleType
import br.com.gabryel.movieclub.db.repositories.dto.RatingOptionRow
import br.com.gabryel.movieclub.db.repositories.dto.RatingScaleRow
import kotlin.uuid.Uuid

interface RatingScaleRepository {
    fun createScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow

    fun createOption(scaleId: Uuid, label: String, position: Int, color: String): RatingOptionRow

    fun findScales(clubId: Uuid): List<RatingScaleRow>

    fun findScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow?

    fun findOptions(scaleId: Uuid): List<RatingOptionRow>

    fun findOptionById(id: Uuid): RatingOptionRow?

    fun findOptionByLabel(scaleId: Uuid, label: String): RatingOptionRow?
}
