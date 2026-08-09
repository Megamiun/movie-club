package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.PersonRepository
import br.com.gabryel.movieclub.db.repositories.dto.PersonRow
import br.com.gabryel.movieclub.db.tables.People
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedPersonRepository : PersonRepository {
    override fun findOrCreate(name: String, tmdbId: String?, imdbId: String?): PersonRow = transaction {
        val existing = findExistingId(tmdbId, imdbId)

        val id = if (existing != null) {
            People.update({ People.id eq existing }) { it.apply(name, tmdbId, imdbId) }
            existing
        } else {
            People.insert {
                it[People.createdAt] = Clock.System.now()
                it.apply(name, tmdbId, imdbId)
            }[People.id].value
        }

        findById(id)!!
    }

    private fun findExistingId(tmdbId: String?, imdbId: String?): Uuid? {
        val byTmdbId = tmdbId?.let { id ->
            People.selectAll().where { People.tmdbId eq id }.map { it[People.id].value }.singleOrNull()
        }
        if (byTmdbId != null) return byTmdbId

        return imdbId?.let { id ->
            People.selectAll().where { People.imdbId eq id }.map { it[People.id].value }.singleOrNull()
        }
    }

    private fun findById(id: Uuid): PersonRow? =
        People.selectAll().where { People.id eq id }.map(::toRow).singleOrNull()

    /** Only overwrites [People.tmdbId]/[People.imdbId] when a non-null value is given -- a later call that only
     * knows the `tmdbId` (e.g. a bulk season import) shouldn't blank out an `imdbId` a previous best-effort lookup
     * already resolved. */
    private fun UpdateBuilder<*>.apply(name: String, tmdbId: String?, imdbId: String?) {
        this[People.name] = name
        if (tmdbId != null) this[People.tmdbId] = tmdbId
        if (imdbId != null) this[People.imdbId] = imdbId
    }

    private fun toRow(row: ResultRow) = PersonRow(
        id = row[People.id].value,
        name = row[People.name],
        imdbId = row[People.imdbId],
        tmdbId = row[People.tmdbId],
    )
}
