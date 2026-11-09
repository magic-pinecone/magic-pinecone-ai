package org.mpc.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import platform.UIKit.UIColor
import platform.UIKit.UITraitCollection
import platform.UIKit.labelColor
import platform.UIKit.secondaryLabelColor
import platform.UIKit.secondarySystemGroupedBackgroundColor
import platform.UIKit.separatorColor
import platform.UIKit.systemGroupedBackgroundColor
import platform.UIKit.systemRedColor
import platform.UIKit.tertiaryLabelColor
import platform.UIKit.tertiarySystemFillColor
import platform.UIKit.tertiarySystemGroupedBackgroundColor
import platform.UIKit.tintColor

private const val BORDER_STRONG_ALPHA = 0.14f
private const val CONTAINER_TINT_LIGHT = 0.14f
private const val CONTAINER_TINT_DARK = 0.22f

@Composable
internal actual fun PlatformMpcTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = rememberTraitResolved(darkTheme) { traits -> uiKitMpcColors(traits, darkTheme) }

    MaterialTheme(
        colorScheme = remember(colors, darkTheme) { colors.toMaterialColorScheme(darkTheme) },
        typography = IosMaterialTypography,
    ) {
        CompositionLocalProvider(
            LocalMpcColors provides colors,
            LocalMpcTypography provides IosMpcTypography,
            content = content,
        )
    }
}

private fun uiKitMpcColors(traits: UITraitCollection, darkTheme: Boolean): MpcColors {
    fun UIColor.resolved() = resolvedComposeColor(traits)

    val containerTint = if (darkTheme) CONTAINER_TINT_DARK else CONTAINER_TINT_LIGHT
    val surface = UIColor.secondarySystemGroupedBackgroundColor.resolved()
    val surfaceSelected = UIColor.tertiarySystemGroupedBackgroundColor.resolved()
    val label = UIColor.labelColor.resolved()
    val accent = UIColor.tintColor.resolved()
    val accentContainer = surface.mix(accent, containerTint)
    val destructive = UIColor.systemRedColor.resolved()
    val destructiveContainer = surfaceSelected.mix(destructive, containerTint)
    return MpcColors(
        background = UIColor.systemGroupedBackgroundColor.resolved(),
        surface = surface,
        surfaceSelected = surfaceSelected,
        fillQuiet = UIColor.tertiarySystemFillColor.resolved().compositeOver(surface),
        onFillQuiet = UIColor.secondaryLabelColor.resolved(),
        label = label,
        labelSecondary = UIColor.secondaryLabelColor.resolved(),
        labelTertiary = UIColor.tertiaryLabelColor.resolved(),
        separator = UIColor.separatorColor.resolved(),
        borderStrong = label.copy(alpha = BORDER_STRONG_ALPHA),
        accent = accent,
        onAccent = Color.White,
        accentContainer = accentContainer,
        onAccentContainer = accent.withTextContrastAgainst(accentContainer),
        destructive = destructive,
        destructiveContainer = destructiveContainer,
        onDestructiveContainer = destructive.withTextContrastAgainst(destructiveContainer),
    )
}

/** Material and Calf components read `MaterialTheme`; fill it from the same UIKit values. */
private fun MpcColors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        background = background,
        onBackground = label,
        surface = surface,
        onSurface = label,
        surfaceVariant = fillQuiet,
        onSurfaceVariant = labelSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceSelected,
        surfaceContainerHighest = surfaceSelected,
        secondaryContainer = fillQuiet,
        onSecondaryContainer = onFillQuiet,
        outline = borderStrong,
        outlineVariant = separator,
        error = destructive,
        onError = Color.White,
        errorContainer = destructiveContainer,
        onErrorContainer = onDestructiveContainer,
    )
}

/** Dynamic Type "Large" sizes; Compose applies the user's text size through `fontScale`. */
private val IosMpcTypography = MpcTypography(
    screenTitle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    sectionTitle = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    supporting = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    micro = TextStyle(fontSize = 11.sp, lineHeight = 13.sp),
)

private val IosMaterialTypography = Typography(
    headlineSmall = IosMpcTypography.screenTitle,
    titleLarge = IosMpcTypography.sectionTitle,
    titleMedium = IosMpcTypography.headline,
    bodyLarge = IosMpcTypography.body,
    bodyMedium = IosMpcTypography.supporting,
    bodySmall = IosMpcTypography.caption,
    labelLarge = IosMpcTypography.supporting.copy(fontWeight = FontWeight.Medium),
    labelMedium = IosMpcTypography.label,
    labelSmall = IosMpcTypography.micro,
)
