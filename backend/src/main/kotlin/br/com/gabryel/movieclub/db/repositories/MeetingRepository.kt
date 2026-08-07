package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.tables.Meetings
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder.ASC
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

data class MeetingRow(
    val id: Uuid,
    val clubId: Uuid,
    val date: LocalDate,
    val assignedMemberId: Uuid?,
)

interface MeetingRepository {
    fun create(clubId: Uuid, date: LocalDate, assignedMemberId: Uuid?): MeetingRow

    fun findById(id: Uuid): MeetingRow?

    fun findByClubAndDate(clubId: Uuid, date: LocalDate): MeetingRow?

    fun listByClub(clubId: Uuid): List<MeetingRow>

    fun updateDate(id: Uuid, date: LocalDate): MeetingRow

    fun updateAssignedMember(id: Uuid, assignedMemberId: Uuid?): MeetingRow

    fun delete(id: Uuid)
}

class ExposedMeetingRepository : MeetingRepository {
    override fun create(clubId: Uuid, date: LocalDate, assignedMemberId: Uuid?): MeetingRow =
        transaction {
            val result = Meetings.insert {
                it[Meetings.clubId] = clubId
                it[Meetings.date] = date
                it[Meetings.assignedMemberId] = assignedMemberId
            }
            toRow(result.resultedValues!!.single())
        }

    override fun findById(id: Uuid): MeetingRow? =
        transaction {
            Meetings
                .selectAll()
                .where { Meetings.id eq id }
                .map(::toRow)
                .singleOrNull()
        }

    override fun findByClubAndDate(clubId: Uuid, date: LocalDate): MeetingRow? =
        transaction {
            Meetings
                .selectAll()
                .where { (Meetings.clubId eq clubId) and (Meetings.date eq date) }
                .map(::toRow)
                .singleOrNull()
        }

    override fun listByClub(clubId: Uuid): List<MeetingRow> =
        transaction {
            Meetings
                .selectAll()
                .where { Meetings.clubId eq clubId }
                .orderBy(Meetings.date to ASC)
                .map(::toRow)
        }

    override fun updateDate(id: Uuid, date: LocalDate): MeetingRow =
        transaction {
            Meetings.update({ Meetings.id eq id }) {
                it[Meetings.date] = date
            }
            findById(id)!!
        }

    override fun updateAssignedMember(id: Uuid, assignedMemberId: Uuid?): MeetingRow =
        transaction {
            Meetings.update({ Meetings.id eq id }) {
                it[Meetings.assignedMemberId] = assignedMemberId
            }
            findById(id)!!
        }

    override fun delete(id: Uuid) {
        transaction {
            Meetings.deleteWhere { Meetings.id eq id }
        }
    }

    private fun toRow(row: ResultRow) =
        MeetingRow(
            id = row[Meetings.id].value,
            clubId = row[Meetings.clubId].value,
            date = row[Meetings.date],
            assignedMemberId = row[Meetings.assignedMemberId]?.value,
        )
}
