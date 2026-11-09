package org.mpc.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformMpcTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialMpcTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
