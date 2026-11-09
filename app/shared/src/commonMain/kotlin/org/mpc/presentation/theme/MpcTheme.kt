package org.mpc.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.TimetableColorMode

internal val LocalMpcColors = staticCompositionLocalOf<MpcColors> { error("MpcColors read outside MpcTheme") }
internal val LocalMpcTypography = staticCompositionLocalOf<MpcTypography> { error("MpcTypography read outside MpcTheme") }

object MpcTheme {
    val colors: MpcColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMpcColors.current

    val typography: MpcTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMpcTypography.current
}

/**
 * Shared code names color and type roles; each platform resolves them to native values
 * and fills `MaterialTheme` from the same values so Material and Calf defaults agree.
 */
@Composable
fun MpcTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    timetableColorMode: TimetableColorMode = TimetableColorMode.CATEGORY,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    PlatformMpcTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalTimetableColorMode provides timetableColorMode, content = content)
    }
}

/** Provides `MaterialTheme` plus [LocalMpcColors] and [LocalMpcTypography] resolved from platform values. */
@Composable
internal expect fun PlatformMpcTheme(darkTheme: Boolean, content: @Composable () -> Unit)
