package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.date

object Meetings : UuidTable("meetings") {
    val clubId = reference("club_id", Clubs)
    val date = date("date")
    val assignedMemberId = optReference("assigned_member_id", Members)
}
