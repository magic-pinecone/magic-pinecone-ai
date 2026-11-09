package org.mpc.data.session

// The browser's fetch owns cookies and hides HttpOnly ones from script, so the web preview keeps none of its own.
internal actual fun createPlatformCookieJar(): PlatformCookieJar = object : PlatformCookieJar {
    override suspend fun cookieHeader(url: String): CookieHeader? = null

    override suspend fun storeSetCookies(url: String, values: List<String>) = Unit

    override suspend fun clear(url: String, keep: Set<String>) = Unit
}
