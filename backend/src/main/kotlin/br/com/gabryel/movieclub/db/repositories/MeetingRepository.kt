package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

interface MeetingRepository {
    fun create(clubId: Uuid, date: LocalDate, assignedMemberId: Uuid? = null): MeetingRow

    fun findById(id: Uuid): MeetingRow?

    fun findByClubAndDate(clubId: Uuid, date: LocalDate): MeetingRow?

    fun listByClub(clubId: Uuid): List<MeetingRow>

    fun updateDate(id: Uuid, date: LocalDate): MeetingRow

    fun updateAssignedMember(id: Uuid, assignedMemberId: Uuid? = null): MeetingRow

    fun delete(id: Uuid)
}
