package org.mpc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.repository.AppSettingsRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { preferences -> preferences.toAppSettings() }
        .distinctUntilChanged()

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[themeModeKey] = themeMode.name
        }
    }

    override suspend fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode) {
        dataStore.edit { preferences ->
            preferences[portalAuthenticationModeKey] = authenticationMode.name
        }
    }

    override suspend fun setTimetableColorMode(colorMode: TimetableColorMode) {
        dataStore.edit { preferences ->
            preferences[timetableColorModeKey] = colorMode.name
        }
    }
}

private val themeModeKey = stringPreferencesKey("appearance.theme_mode")
private val portalAuthenticationModeKey = stringPreferencesKey("portal.authentication_mode")
private val timetableColorModeKey = stringPreferencesKey("timetable.color_mode")

private fun Preferences.toAppSettings(): AppSettings = AppSettings(
    themeMode = this[themeModeKey].toEnumOrDefault(AppThemeMode.SYSTEM),
    // Fall back to manual sign-in until platform credential adapters are available.
    portalAuthenticationMode = PortalAuthenticationMode.MANUAL_PORTAL,
    timetableColorMode = this[timetableColorModeKey].toEnumOrDefault(TimetableColorMode.CATEGORY),
)

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T = this?.let { value ->
    enumValues<T>().firstOrNull { it.name == value }
} ?: default
