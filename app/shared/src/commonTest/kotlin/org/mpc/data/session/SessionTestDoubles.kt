package org.mpc.data.session

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.repository.AppSettingsRepository

/** A cookie jar keyed by exact host, enough to observe what the session layer stores and clears. */
internal class InMemoryCookieJar : PlatformCookieJar {
    val cookies = mutableMapOf<String, MutableMap<String, String>>()

    override suspend fun cookieHeader(url: String): CookieHeader? = cookies[Url(url).host]
        ?.takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString("; ") { "${it.key}=${it.value}" }
        ?.let(::CookieHeader)

    override suspend fun storeSetCookies(url: String, values: List<String>) {
        values.forEach { line ->
            val (name, value) = line.substringBefore(';').split('=', limit = 2)
            cookies.getOrPut(Url(url).host) { mutableMapOf() }[name.trim()] = value
        }
    }

    override suspend fun clear(url: String, keep: Set<String>) {
        cookies[Url(url).host]?.keys?.removeAll { it !in keep }
    }
}

internal class InMemoryPortalGuardStore(var guard: PortalGuard = PortalGuard()) : PortalGuardStore {
    val writes = mutableListOf<PortalGuard>()

    override suspend fun read(): PortalGuard = guard

    override suspend fun write(guard: PortalGuard) {
        this.guard = guard
        writes += guard
    }
}

internal class FakeAppSettingsRepository(
    mode: PortalAuthenticationMode = PortalAuthenticationMode.MANUAL_PORTAL,
) : AppSettingsRepository {
    override val settings = MutableStateFlow(AppSettings(portalAuthenticationMode = mode))

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        settings.value = settings.value.copy(themeMode = themeMode)
    }

    override suspend fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode) {
        settings.value = settings.value.copy(portalAuthenticationMode = authenticationMode)
    }

    override suspend fun setTimetableColorMode(colorMode: TimetableColorMode) {
        settings.value = settings.value.copy(timetableColorMode = colorMode)
    }
}
