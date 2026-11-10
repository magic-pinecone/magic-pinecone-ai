package org.mpc.data.session

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

internal actual fun createSessionHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js) {
    configure()
}
