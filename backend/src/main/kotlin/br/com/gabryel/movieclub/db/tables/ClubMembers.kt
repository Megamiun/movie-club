package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.ClubRole
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ClubMembers : Table("club_members") {
    val clubId = reference("club_id", Clubs)
    val memberId = reference("member_id", Members)
    val role = enumerationByName<ClubRole>("role", 32)
    val rotationOrder = integer("rotation_order")
    val color = varchar("color", 7).nullable()
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(clubId, memberId)
}
