package org.mpc.data.session

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

// NSURLSession would otherwise read and write the shared NSHTTPCookieStorage, a second jar beside the WebView's.
internal actual fun createSessionHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            HTTPCookieStorage = null
            HTTPShouldSetCookies = false
        }
    }
    configure()
}
