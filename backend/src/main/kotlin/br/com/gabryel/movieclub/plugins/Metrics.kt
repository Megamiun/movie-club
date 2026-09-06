package br.com.gabryel.movieclub.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** [meterRegistry] is created once in `Application.module` and shared with the `/metrics` route (`Routing.kt`) --
 * the plugin needs it to record request timing/status per call, the route needs the same instance to scrape it. */
fun Application.configureMetrics(meterRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = meterRegistry
    }
}
