package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.kotlin.datetime.timestamp

object Members : UUIDTable("members") {
    val googleId = varchar("google_id", 128).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val avatarUrl = varchar("avatar_url", 1024).nullable()
    val createdAt = timestamp("created_at")
}
