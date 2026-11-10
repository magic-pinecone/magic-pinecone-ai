package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mpc.domain.model.AppLanguage
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.repository.AppSettingsRepository
import kotlin.test.assertEquals

private class SettingsStore : AppSettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    override suspend fun setLanguage(language: AppLanguage) {
        settings.value = settings.value.copy(language = language)
    }
    override suspend fun setThemeMode(themeMode: AppThemeMode) = Unit
    override suspend fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode) = Unit
    override suspend fun setTimetableColorMode(colorMode: TimetableColorMode) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
val appSettingsViewModelTests by testSuite {
    test("language selection is persisted and exposed in settings") {
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = SettingsStore()
                val viewModel = AppSettingsViewModel(repository)
                viewModel.setLanguage(AppLanguage.VIETNAMESE)
                testScheduler.advanceUntilIdle()
                assertEquals(AppLanguage.VIETNAMESE, repository.settings.value.language)
                assertEquals(AppLanguage.VIETNAMESE, viewModel.settings.value.language)
            } finally {
                Dispatchers.resetMain()
            }
        }
    }
}
