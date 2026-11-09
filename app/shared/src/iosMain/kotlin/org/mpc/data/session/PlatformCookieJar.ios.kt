package org.mpc.data.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume

internal actual fun createPlatformCookieJar(): PlatformCookieJar = IosPlatformCookieJar()

private class IosPlatformCookieJar : PlatformCookieJar {
    private val cookieStore: WKHTTPCookieStore
        get() = WKWebsiteDataStore.defaultDataStore().httpCookieStore

    override suspend fun cookieHeader(url: String): CookieHeader? = withContext(Dispatchers.Main.immediate) {
        val requestUrl = NSURL(string = url)
        val host = requestUrl.host?.lowercase() ?: return@withContext null
        val path = requestUrl.path?.ifEmpty { "/" } ?: "/"
        val currentTime = NSDate().timeIntervalSince1970
        val matchingCookies = allCookies().filter { cookie ->
            val domain = cookie.domain.removePrefix(".").lowercase()
            val domainMatches = host == domain || host.endsWith(".$domain")
            val cookiePath = cookie.path.ifEmpty { "/" }
            val pathMatches = path == cookiePath || isCookiePathPrefix(path, cookiePath)
            val isUnexpired = cookie.expiresDate?.timeIntervalSince1970?.let { it > currentTime } ?: true
            domainMatches && pathMatches && isUnexpired && (!cookie.isSecure() || requestUrl.scheme == "https")
        }.sortedByDescending { it.path.length }
        matchingCookies.takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.name}=${it.value}" }
            ?.let(::CookieHeader)
    }

    override suspend fun storeSetCookies(url: String, values: List<String>) = withContext(Dispatchers.Main.immediate) {
        val requestUrl = NSURL(string = url)
        values.forEach { value ->
            @Suppress("UNCHECKED_CAST")
            val cookies = NSHTTPCookie.cookiesWithResponseHeaderFields(
                mapOf("Set-Cookie" to value),
                forURL = requestUrl,
            ) as? List<NSHTTPCookie>
            cookies.orEmpty().forEach { cookie ->
                suspendCancellableCoroutine { continuation ->
                    cookieStore.setCookie(cookie) { continuation.resume(Unit) }
                }
            }
        }
    }

    override suspend fun clear(url: String, keep: Set<String>) = withContext(Dispatchers.Main.immediate) {
        val host = NSURL(string = url).host?.lowercase() ?: return@withContext
        allCookies()
            .filter { cookie -> cookie.domain.removePrefix(".").lowercase() == host && cookie.name !in keep }
            .forEach { cookie ->
                suspendCancellableCoroutine { continuation ->
                    cookieStore.deleteCookie(cookie) { continuation.resume(Unit) }
                }
            }
    }

    private suspend fun allCookies(): List<NSHTTPCookie> = suspendCancellableCoroutine { continuation ->
        cookieStore.getAllCookies { values ->
            @Suppress("UNCHECKED_CAST")
            continuation.resume((values as? List<NSHTTPCookie>).orEmpty())
        }
    }
}

private fun isCookiePathPrefix(path: String, cookiePath: String): Boolean {
    if (!path.startsWith(cookiePath)) return false
    return cookiePath.endsWith("/") || path.getOrNull(cookiePath.length) == '/'
}
