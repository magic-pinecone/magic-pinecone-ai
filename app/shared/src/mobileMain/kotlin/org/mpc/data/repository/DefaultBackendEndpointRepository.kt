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
import org.mpc.domain.repository.BackendEndpointRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultBackendEndpointRepository(
    private val dataStore: DataStore<Preferences>,
) : BackendEndpointRepository {
    override val baseUrl: Flow<String?> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { preferences -> preferences[backendBaseUrlKey] }
        .distinctUntilChanged()

    override suspend fun setBaseUrl(baseUrl: String?) {
        dataStore.edit { preferences ->
            if (baseUrl == null) {
                preferences.remove(backendBaseUrlKey)
            } else {
                preferences[backendBaseUrlKey] = baseUrl
            }
        }
    }
}

private val backendBaseUrlKey = stringPreferencesKey("backend.base_url")
