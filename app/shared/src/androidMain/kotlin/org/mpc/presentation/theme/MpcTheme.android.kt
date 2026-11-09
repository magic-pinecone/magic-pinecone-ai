package org.mpc.presentation.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

@Composable
internal actual fun PlatformMpcTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        CompositionLocalProvider(
            LocalMpcColors provides colorScheme.toMpcColors(),
            LocalMpcTypography provides MaterialTheme.typography.toMpcTypography(),
            content = content,
        )
    }
}

internal fun ColorScheme.toMpcColors() = MpcColors(
    background = surface,
    surface = surfaceContainerLow,
    surfaceSelected = surfaceContainerHigh,
    fillQuiet = secondaryContainer,
    onFillQuiet = onSecondaryContainer,
    label = onSurface,
    labelSecondary = onSurfaceVariant,
    labelTertiary = outline,
    separator = outlineVariant,
    borderStrong = outline,
    accent = primary,
    onAccent = onPrimary,
    accentContainer = primaryContainer,
    onAccentContainer = onPrimaryContainer,
    destructive = error,
    destructiveContainer = errorContainer,
    onDestructiveContainer = onErrorContainer,
)

private fun Typography.toMpcTypography() = MpcTypography(
    screenTitle = headlineSmall,
    sectionTitle = titleLarge,
    headline = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    body = bodyLarge,
    supporting = bodyMedium,
    caption = bodySmall,
    label = labelMedium,
    micro = labelSmall,
)
