package br.com.gabryel.movieclub.db.repositories.dto

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class WatchlistEntryRow(
    val id: Uuid,
    val clubId: Uuid,
    val memberId: Uuid,
    val title: String,
    val imdbUrl: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
)
