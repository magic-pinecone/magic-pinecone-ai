package org.mpc.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MpcColorsAndroidHostTest {
    private val stockSchemes = mapOf(
        "light" to lightColorScheme().toMpcColors(),
        "dark" to darkColorScheme().toMpcColors(),
    )

    @Test
    fun textRolesAreReadableOnTheirContainers() {
        stockSchemes.forEach { (name, colors) ->
            listOf(
                "label on surface" to (colors.label to colors.surface),
                "label on selected surface" to (colors.label to colors.surfaceSelected),
                "onAccent on accent" to (colors.onAccent to colors.accent),
                "onAccentContainer on accentContainer" to (colors.onAccentContainer to colors.accentContainer),
                "onDestructiveContainer on destructiveContainer" to
                    (colors.onDestructiveContainer to colors.destructiveContainer),
                "onFillQuiet on fillQuiet" to (colors.onFillQuiet to colors.fillQuiet),
            ).forEach { (pair, colorsPair) ->
                val (text, background) = colorsPair
                val ratio = contrastRatio(text.compositeOver(background), background)
                assertTrue(ratio >= MINIMUM_TEXT_CONTRAST, "$name: $pair is $ratio:1")
            }
        }
    }

    @Test
    fun selectedCardIsDistinctFromRestingCard() {
        stockSchemes.forEach { (name, colors) ->
            assertNotEquals(colors.surface, colors.surfaceSelected, name)
            assertNotEquals(colors.separator, colors.borderStrong, name)
        }
    }
}
