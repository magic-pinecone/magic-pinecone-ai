package org.mpc.presentation.theme

import androidx.compose.ui.graphics.Color
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SystemRed = Color(red = 1f, green = 0.231f, blue = 0.188f)
private val LightSurface = Color.White
private val DarkSurface = Color(red = 0.11f, green = 0.11f, blue = 0.118f)

val colorContrastTests by testSuite {
    test("low-contrast text on a light container is darkened to at least 4.5:1") {
        val container = LightSurface.mix(SystemRed, 0.14f)
        assertTrue(contrastRatio(SystemRed, container) < MINIMUM_TEXT_CONTRAST)

        val text = SystemRed.withTextContrastAgainst(container)

        assertTrue(contrastRatio(text, container) >= MINIMUM_TEXT_CONTRAST)
        assertTrue(text.red < SystemRed.red, "pushed toward black")
    }

    test("low-contrast text on a dark container is lightened to at least 4.5:1") {
        val blue = Color(red = 0.04f, green = 0.52f, blue = 1f)
        val container = DarkSurface.mix(blue, 0.22f)
        assertTrue(contrastRatio(blue, container) < MINIMUM_TEXT_CONTRAST)

        val text = blue.withTextContrastAgainst(container)

        assertTrue(contrastRatio(text, container) >= MINIMUM_TEXT_CONTRAST)
        assertTrue(text.green > blue.green, "pushed toward white")
    }

    test("text that already reaches 4.5:1 is returned unchanged") {
        val text = Color(red = 0.1f, green = 0.1f, blue = 0.1f)

        assertEquals(text, text.withTextContrastAgainst(LightSurface))
    }

    test("contrast ratio matches WCAG for black on white") {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), absoluteTolerance = 0.01f)
    }
}
