package br.com.gabryel.movieclub.db.repositories.dto

import kotlin.uuid.Uuid

/** Global season catalog row, deduplicated by ([seriesId], [number]) -- shared by every club following that series. */
data class SeasonRow(
    val id: Uuid,
    val seriesId: Uuid,
    val number: Int,
    val title: String? = null,
)

data class SeasonReviewRow(
    val seasonId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid? = null,
    val sentimentOptionId: Uuid? = null,
    val comment: String? = null,
)
