package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.RatingScaleType
import kotlin.uuid.Uuid

data class RatingScaleRow(
    val id: Uuid,
    val clubId: Uuid,
    val type: RatingScaleType,
)

data class RatingOptionRow(
    val id: Uuid,
    val scaleId: Uuid,
    val label: String,
    val position: Int,
    val color: String,
)
