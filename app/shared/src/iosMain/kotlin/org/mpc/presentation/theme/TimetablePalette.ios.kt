package org.mpc.presentation.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityContrastHigh
import platform.UIKit.UIColor
import platform.UIKit.systemBackgroundColor
import platform.UIKit.systemBlueColor
import platform.UIKit.systemCyanColor
import platform.UIKit.systemGreenColor
import platform.UIKit.systemIndigoColor
import platform.UIKit.systemMintColor
import platform.UIKit.systemOrangeColor
import platform.UIKit.systemPinkColor
import platform.UIKit.systemPurpleColor
import platform.UIKit.systemRedColor
import platform.UIKit.systemTealColor
import platform.UIKit.systemYellowColor

private val AppleTimetableColors = listOf(
    UIColor.systemBlueColor,
    UIColor.systemGreenColor,
    UIColor.systemOrangeColor,
    UIColor.systemPurpleColor,
    UIColor.systemTealColor,
    UIColor.systemIndigoColor,
    UIColor.systemPinkColor,
    UIColor.systemMintColor,
    UIColor.systemRedColor,
    UIColor.systemCyanColor,
    UIColor.systemYellowColor,
)

@Composable
actual fun platformTimetablePalette(darkTheme: Boolean): List<TimetableBlockColors> = rememberTraitResolved(darkTheme) { traits ->
    val isHighContrast = traits.accessibilityContrast == UIAccessibilityContrastHigh
    val tint =
        when {
            isHighContrast && darkTheme -> 0.46f
            isHighContrast -> 0.38f
            darkTheme -> 0.34f
            else -> 0.22f
        }
    val background = UIColor.systemBackgroundColor.resolvedComposeColor(traits)

    AppleTimetableColors.map { systemColor ->
        val accent = systemColor.resolvedComposeColor(traits)
        val container = background.mix(accent, tint)
        TimetableBlockColors(
            container = container,
            content = accent.withTextContrastAgainst(container),
            leadingAccent = accent,
        )
    }
}
