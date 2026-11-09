package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(themeMode: AppThemeMode)

    suspend fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode)

    suspend fun setTimetableColorMode(colorMode: TimetableColorMode)
}
