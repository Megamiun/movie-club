package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.kotlin.datetime.date

object Meetings : UUIDTable("meetings") {
    val clubId = reference("club_id", Clubs)
    val date = date("date")
    val assignedMemberId = optReference("assigned_member_id", Members)
}
