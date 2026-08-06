package br.com.gabryel.movieclub

import br.com.gabryel.movieclub.plugins.configureAuthentication
import br.com.gabryel.movieclub.plugins.configureCORS
import br.com.gabryel.movieclub.plugins.configureDatabase
import br.com.gabryel.movieclub.plugins.configureRouting
import br.com.gabryel.movieclub.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureDatabase()
    configureSerialization()
    configureCORS()
    configureAuthentication()
    configureRouting()
}
