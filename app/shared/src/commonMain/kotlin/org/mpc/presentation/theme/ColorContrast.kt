package org.mpc.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

internal const val MINIMUM_TEXT_CONTRAST = 4.5f
private const val WCAG_LUMINANCE_OFFSET = 0.05f
private const val CONTRAST_SEARCH_STEPS = 16

/** Blends in gamma-encoded sRGB, the way UIKit and design tools lay a tint over a surface. */
internal fun Color.mix(other: Color, amount: Float) = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = alpha + (other.alpha - alpha) * amount,
)

/** WCAG 2 contrast ratio between two opaque colors. */
internal fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (maxOf(firstLuminance, secondLuminance) + WCAG_LUMINANCE_OFFSET) /
        (minOf(firstLuminance, secondLuminance) + WCAG_LUMINANCE_OFFSET)
}

/**
 * Returns this color unchanged when it already reaches [MINIMUM_TEXT_CONTRAST] on the opaque [background];
 * otherwise the least shift toward black or white that does.
 */
internal fun Color.withTextContrastAgainst(background: Color): Color {
    val text = compositeOver(background)
    if (contrastRatio(text, background) >= MINIMUM_TEXT_CONTRAST) return this

    val target =
        if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White
    var lowerAmount = 0f
    var upperAmount = 1f
    repeat(CONTRAST_SEARCH_STEPS) {
        val candidateAmount = (lowerAmount + upperAmount) / 2f
        if (contrastRatio(text.mix(target, candidateAmount), background) >= MINIMUM_TEXT_CONTRAST) {
            upperAmount = candidateAmount
        } else {
            lowerAmount = candidateAmount
        }
    }
    return text.mix(target, upperAmount)
}
