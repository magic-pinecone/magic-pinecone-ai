package org.mpc.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/** Keeps [PortalGuard] across launches, so a restart cannot lift a block on automatic Portal sign-in. */
internal interface PortalGuardStore {
    suspend fun read(): PortalGuard

    suspend fun write(guard: PortalGuard)
}

@ContributesBinding(AppScope::class)
@Inject
internal class DataStorePortalGuardStore(
    private val dataStore: DataStore<Preferences>,
) : PortalGuardStore {
    override suspend fun read(): PortalGuard {
        val preferences = dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()
        return PortalGuard(
            block = preferences[blockKey]?.let { name -> PortalBlock.entries.firstOrNull { it.name == name } },
            lastAutomaticSignInAt = preferences[lastAutomaticSignInKey]?.let(Instant::fromEpochMilliseconds),
        )
    }

    override suspend fun write(guard: PortalGuard) {
        dataStore.edit { preferences ->
            guard.block?.let { preferences[blockKey] = it.name } ?: preferences.remove(blockKey)
            guard.lastAutomaticSignInAt?.let { preferences[lastAutomaticSignInKey] = it.toEpochMilliseconds() }
                ?: preferences.remove(lastAutomaticSignInKey)
        }
    }
}

private val blockKey = stringPreferencesKey("session.portal_block")
private val lastAutomaticSignInKey = longPreferencesKey("session.portal_last_automatic_sign_in")
