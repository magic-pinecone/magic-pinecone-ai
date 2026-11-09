package org.mpc.presentation.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.TimetableColorMode

@Composable
fun AndroidMpcTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    timetableColorMode: TimetableColorMode = TimetableColorMode.CATEGORY,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val activity = LocalActivity.current
    val view = LocalView.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MpcTheme(themeMode = themeMode, timetableColorMode = timetableColorMode, content = content)
}
