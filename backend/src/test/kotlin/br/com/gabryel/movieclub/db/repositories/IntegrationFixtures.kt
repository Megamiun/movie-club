package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.tables.ClubMembers
import br.com.gabryel.movieclub.db.tables.Clubs
import br.com.gabryel.movieclub.db.tables.Meetings
import br.com.gabryel.movieclub.db.tables.Members
import br.com.gabryel.movieclub.db.tables.Series
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Minimal real-row fixtures shared by repository integration tests -- inserts directly via Exposed rather than
 * through services, since these tests exercise the repository layer itself, not the access-control/seeding logic
 * services add on top. Every insert uses a random-suffixed identifier so parallel/rerun test runs don't collide. */
internal object IntegrationFixtures {
    fun insertMember(): Uuid = transaction {
        Members.insert {
            it[Members.email] = "integration-${Uuid.random()}@example.com"
            it[Members.createdAt] = Clock.System.now()
        }[Members.id].value
    }

    fun insertClub(): Uuid = transaction {
        Clubs.insert {
            it[Clubs.name] = "Integration Test Club ${Uuid.random()}"
            it[Clubs.createdAt] = Clock.System.now()
        }[Clubs.id].value
    }

    fun insertClubMembership(clubId: Uuid, memberId: Uuid) {
        transaction {
            ClubMembers.insert {
                it[ClubMembers.clubId] = clubId
                it[ClubMembers.memberId] = memberId
                it[ClubMembers.role] = MEMBER
                it[ClubMembers.rotationOrder] = 0
                it[ClubMembers.joinedAt] = Clock.System.now()
            }
        }
    }

    fun insertMeeting(clubId: Uuid, date: LocalDate = LocalDate(2026, 1, 1)): Uuid = transaction {
        Meetings.insert {
            it[Meetings.clubId] = clubId
            it[Meetings.date] = date
        }[Meetings.id].value
    }

    /** Inserts a bare global [Series] catalog row directly -- used by Season/Episode tests, which only need a
     * `seriesId` to hang off, not the club-pick machinery [SeriesRepository] wraps around it. */
    fun insertSeries(): Uuid = transaction {
        Series.insert {
            it[Series.imdbId] = "tt${Uuid.random().toString().take(7)}"
            it[Series.originalTitle] = "Integration Test Series"
            it[Series.alternativeTitles] = emptyList()
            it[Series.createdAt] = Clock.System.now()
        }[Series.id].value
    }
}
