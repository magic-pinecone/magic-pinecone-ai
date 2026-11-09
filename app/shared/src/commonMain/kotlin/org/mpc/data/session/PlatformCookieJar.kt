package org.mpc.data.session

import kotlin.jvm.JvmInline

/** A `Cookie` request header. Redacted when printed so it cannot leak through logs or crash reports. */
@JvmInline
internal value class CookieHeader(val value: String) {
    override fun toString(): String = "CookieHeader(***)"
}

/**
 * The platform WebView cookie store: Android's process-wide `CookieManager` and iOS's default
 * `WKWebsiteDataStore` cookie store, which Calf's WebView also uses. It is the only cookie jar, so a sign-in in
 * the WebView is visible to native requests and the reverse, HttpOnly cookies included.
 */
internal interface PlatformCookieJar {
    /** The cookies the store would send to [url], or null when there are none. */
    suspend fun cookieHeader(url: String): CookieHeader?

    /** Stores raw `Set-Cookie` lines from a response to [url], keeping every attribute the server sent. */
    suspend fun storeSetCookies(url: String, values: List<String>)

    /**
     * Removes the cookies scoped to [url]'s host, both host-only and `Domain=host` cookies. Cookies shared through a
     * parent domain are kept, because other campus sites use them, and so are cookies named in [keep]. Removing an
     * absent cookie does nothing.
     */
    suspend fun clear(url: String, keep: Set<String> = emptySet())
}

internal expect fun createPlatformCookieJar(): PlatformCookieJar
