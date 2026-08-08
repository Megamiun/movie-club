package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object Members : UuidTable("members") {
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255).nullable()
    val username = varchar("username", 32).nullable().uniqueIndex()
    val passwordHash = varchar("password_hash", 255).nullable()
    val inviteToken = uuid("invite_token").nullable().uniqueIndex()
    val createdAt = timestamp("created_at")
}
