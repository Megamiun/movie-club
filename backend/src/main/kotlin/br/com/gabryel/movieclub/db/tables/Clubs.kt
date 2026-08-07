package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object Clubs : UuidTable("clubs") {
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at")
}
