package br.com.gabryel.movieclub.db.repositories.dto

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data class MeetingRow(
    val id: Uuid,
    val clubId: Uuid,
    val date: LocalDate,
    val assignedMemberId: Uuid? = null,
)
