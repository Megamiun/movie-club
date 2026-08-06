package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.kotlin.datetime.timestamp

object Clubs : UUIDTable("clubs") {
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at")
}
