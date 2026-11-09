package org.mpc.data.session

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

// OkHttp keeps no cookies unless given a CookieJar, so PlatformCookieJar stays the only jar.
internal actual fun createSessionHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    configure()
}
