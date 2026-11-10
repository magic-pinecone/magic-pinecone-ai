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
import org.mpc.data.portal.defaultPortalFavoriteIds
import org.mpc.domain.repository.PortalFavoritesRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultPortalFavoritesRepository(
    private val dataStore: DataStore<Preferences>,
) : PortalFavoritesRepository {
    override val favoriteIds: Flow<List<String>> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { preferences -> preferences.favoriteIds() }
        .distinctUntilChanged()

    override suspend fun updateFavoriteIds(transform: (List<String>) -> List<String>) {
        dataStore.edit { preferences ->
            preferences[favoriteIdsKey] = transform(preferences.favoriteIds()).joinToString(FAVORITE_ID_SEPARATOR)
        }
    }
}

private val favoriteIdsKey = stringPreferencesKey("portal.favorite_ids")

// Ids are URLs, which never contain a line break.
private const val FAVORITE_ID_SEPARATOR = "\n"

// A missing key means the user never customized favorites; an empty value means they removed them all.
private fun Preferences.favoriteIds(): List<String> = this[favoriteIdsKey]
    ?.split(FAVORITE_ID_SEPARATOR)
    ?.filter(String::isNotEmpty)
    ?: defaultPortalFavoriteIds
