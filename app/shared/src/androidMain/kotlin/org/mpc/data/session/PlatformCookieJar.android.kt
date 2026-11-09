package org.mpc.data.session

import android.webkit.CookieManager
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal actual fun createPlatformCookieJar(): PlatformCookieJar = AndroidPlatformCookieJar()

private class AndroidPlatformCookieJar : PlatformCookieJar {
    private val cookieManager = CookieManager.getInstance()

    override suspend fun cookieHeader(url: String): CookieHeader? = withContext(Dispatchers.Main.immediate) {
        cookieManager.getCookie(url)?.takeIf { it.isNotBlank() }?.let(::CookieHeader)
    }

    override suspend fun storeSetCookies(url: String, values: List<String>) {
        withContext(Dispatchers.Main.immediate) {
            values.forEach { value -> setCookie(url, value) }
            cookieManager.flush()
        }
    }

    // CookieManager cannot list or delete by host, and getCookie returns names without their attributes. Expire
    // each visible name for both host-only and Domain=host scopes; a Max-Age=0 write only replaces a cookie whose
    // domain and path match, so parent-domain cookies are left alone.
    override suspend fun clear(url: String, keep: Set<String>) {
        withContext(Dispatchers.Main.immediate) {
            val host = Url(url).host
            val names = cookieManager.getCookie(url).orEmpty()
                .split(';')
                .map { it.substringBefore('=').trim() }
                .filter { it.isNotEmpty() && it !in keep }
                .distinct()
            names.forEach { name ->
                setCookie(url, "$name=; Max-Age=0; Path=/")
                setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$host")
            }
            cookieManager.flush()
        }
    }

    private suspend fun setCookie(url: String, value: String) {
        suspendCancellableCoroutine { continuation ->
            cookieManager.setCookie(url, value) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
}
