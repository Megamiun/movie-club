package br.com.gabryel.movieclub.db.repositories

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer

/** Starts a fresh, throwaway Postgres container (via Testcontainers) -- never the shared docker-compose dev
 * database -- migrates it with the app's real Flyway scripts, and points Exposed's default connection at it.
 *
 * Call [startFresh] once per test class, from a `companion object { init { ... } }` block: a companion object's
 * `init` runs exactly once, the first time the class is touched, regardless of how many `@Test` methods (each
 * getting its own JUnit4 instance) run afterward -- so every test in that class shares one container instead of
 * paying container-startup cost per method, while different classes each still get their own fresh instance.
 * Exposed's no-arg `transaction { }` relies on one process-wide "current" database, so test classes using this
 * must not run concurrently in the same JVM -- true of Gradle's default (sequential) `Test` task. */
internal object TestDatabase {
    fun startFresh() {
        val container = PostgreSQLContainer("postgres:17")
        container.start()

        Flyway.configure()
            .dataSource(container.jdbcUrl, container.username, container.password)
            .load()
            .migrate()

        Database.connect(
            url = container.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = container.username,
            password = container.password,
        )
    }
}
