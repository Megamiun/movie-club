package br.com.gabryel.movieclub.plugins

import br.com.gabryel.movieclub.exception.DomainException
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Errors")

fun Application.configureErrors() {
    install(StatusPages) {
        exception<DomainException> { call, ex ->
            logger.debug("{} {}: {}", call.request.httpMethod.value, call.request.uri, ex.message)
            call.respond(ex.status, ex.message ?: "An error occurred")
        }

        exception<Throwable> { call, ex ->
            logger.error("Unhandled exception on {} {}", call.request.httpMethod.value, call.request.uri, ex)
            call.respond(InternalServerError, "Internal server error")
        }
    }
}
