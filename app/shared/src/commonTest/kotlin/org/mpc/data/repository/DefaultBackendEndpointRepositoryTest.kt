package org.mpc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class BackendEndpointPreferencesStore : DataStore<Preferences> {
    private val storedPreferences = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = storedPreferences

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(storedPreferences.value).also { updatedPreferences ->
        storedPreferences.value = updatedPreferences
    }
}

val backendEndpointRepositoryTests by testSuite {
    test("no backend is configured until one is stored") {
        runTest {
            assertNull(DefaultBackendEndpointRepository(BackendEndpointPreferencesStore()).baseUrl.first())
        }
    }

    test("a stored backend survives reloading and can be cleared again") {
        runTest {
            val dataStore = BackendEndpointPreferencesStore()

            DefaultBackendEndpointRepository(dataStore).setBaseUrl("http://10.0.2.2:8000")
            assertEquals("http://10.0.2.2:8000", DefaultBackendEndpointRepository(dataStore).baseUrl.first())

            DefaultBackendEndpointRepository(dataStore).setBaseUrl(null)
            assertNull(DefaultBackendEndpointRepository(dataStore).baseUrl.first())
        }
    }
}
