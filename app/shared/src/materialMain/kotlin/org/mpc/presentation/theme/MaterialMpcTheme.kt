package org.mpc.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun MaterialMpcTheme(colorScheme: ColorScheme, content: @Composable () -> Unit) {
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
