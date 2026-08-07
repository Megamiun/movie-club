package br.com.gabryel.movieclub

import br.com.gabryel.movieclub.service.auth.Argon2PasswordService
import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.db.repositories.ExposedMemberRepository
import br.com.gabryel.movieclub.plugins.configureAuthentication
import br.com.gabryel.movieclub.plugins.configureCORS
import br.com.gabryel.movieclub.plugins.configureCallLogging
import br.com.gabryel.movieclub.plugins.configureDatabase
import br.com.gabryel.movieclub.plugins.configureErrors
import br.com.gabryel.movieclub.plugins.configureRouting
import br.com.gabryel.movieclub.plugins.configureSerialization
import br.com.gabryel.movieclub.service.MemberService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val config = environment.config

    val jwtService = JwtService(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
    )

    configureDatabase()

    val memberService = MemberService(ExposedMemberRepository(), Argon2PasswordService())
    configureCallLogging()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureAuthentication(jwtService)
    configureRouting(jwtService, memberService)
}
