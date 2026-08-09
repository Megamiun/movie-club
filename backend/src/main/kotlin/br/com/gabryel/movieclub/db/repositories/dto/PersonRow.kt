package br.com.gabryel.movieclub.db.repositories.dto

import kotlin.uuid.Uuid

data class PersonRow(
    val id: Uuid,
    val name: String,
    val imdbId: String? = null,
    val tmdbId: String? = null,
)
