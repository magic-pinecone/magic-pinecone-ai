package org.mpc.presentation.viewModel

import androidx.datastore.core.IOException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.repository.AppSettingsRepository

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class AppSettingsViewModel(
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val _settingsWriteFailed = MutableStateFlow(false)

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )
    val settingsWriteFailed: StateFlow<Boolean> = _settingsWriteFailed.asStateFlow()

    fun setThemeMode(themeMode: AppThemeMode) {
        persistSetting {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode) {
        persistSetting {
            settingsRepository.setPortalAuthenticationMode(authenticationMode)
        }
    }

    fun setTimetableColorMode(colorMode: TimetableColorMode) {
        persistSetting {
            settingsRepository.setTimetableColorMode(colorMode)
        }
    }

    private fun persistSetting(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
                _settingsWriteFailed.value = false
            } catch (_: IOException) {
                _settingsWriteFailed.value = true
            }
        }
    }
}
