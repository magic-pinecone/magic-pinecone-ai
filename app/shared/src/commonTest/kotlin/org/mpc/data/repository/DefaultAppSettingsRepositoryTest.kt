package org.mpc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.TimetableColorMode
import kotlin.test.assertEquals

private class InMemoryPreferencesDataStore(
    initialPreferences: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val storedPreferences = MutableStateFlow(initialPreferences)

    override val data: Flow<Preferences> = storedPreferences

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(storedPreferences.value).also { updatedPreferences ->
        storedPreferences.value = updatedPreferences
    }
}

private val timetableColorModeKey = stringPreferencesKey("timetable.color_mode")

val appSettingsRepositoryTests by testSuite {
    test("settings use defaults when the preference store is empty") {
        runTest {
            val repository = DefaultAppSettingsRepository(InMemoryPreferencesDataStore())

            assertEquals(AppSettings(), repository.settings.first())
        }
    }

    test("unknown timetable color modes fall back to category colors") {
        runTest {
            val dataStore = InMemoryPreferencesDataStore(
                mutablePreferencesOf(timetableColorModeKey to "FUTURE_MODE"),
            )
            val repository = DefaultAppSettingsRepository(dataStore)

            assertEquals(TimetableColorMode.CATEGORY, repository.settings.first().timetableColorMode)
        }
    }

    test("changing timetable colors preserves theme and reloads from the same store") {
        runTest {
            val dataStore = InMemoryPreferencesDataStore()
            val repository = DefaultAppSettingsRepository(dataStore)

            repository.setThemeMode(AppThemeMode.DARK)
            repository.setTimetableColorMode(TimetableColorMode.SHUFFLED)

            val reloadedSettings = DefaultAppSettingsRepository(dataStore).settings.first()

            assertEquals(AppThemeMode.DARK, reloadedSettings.themeMode)
            assertEquals(TimetableColorMode.SHUFFLED, reloadedSettings.timetableColorMode)
        }
    }
}
