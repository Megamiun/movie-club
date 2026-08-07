package br.com.gabryel.movieclub.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabase() {
    val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = environment.config.property("database.url").getString()
                driverClassName = "org.postgresql.Driver"
                username = environment.config.property("database.user").getString()
                password = environment.config.property("database.password").getString()
                maximumPoolSize = 10
            },
        )

    Flyway
        .configure()
        .dataSource(dataSource)
        .load()
        .migrate()

    Database.connect(dataSource)
}
