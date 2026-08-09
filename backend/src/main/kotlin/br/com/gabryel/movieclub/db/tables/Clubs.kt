package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object Clubs : UuidTable("clubs") {
    val name = varchar("name", 255)
    val preferredLanguages = array("preferred_languages", VarCharColumnType(8))
    val ignoredLanguages = array("ignored_languages", VarCharColumnType(8))
    val createdAt = timestamp("created_at")
}
