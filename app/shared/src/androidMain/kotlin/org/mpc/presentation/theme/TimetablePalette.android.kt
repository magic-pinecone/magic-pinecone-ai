package org.mpc.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private data class MaterialTimetableSwatch(
    val lightModeFill: Color,
    val darkModeFill: Color,
)

// Standard Material Design 2014 hues: shade 200 in light mode, shade 700 in dark mode.
// Ordered by the stable timetable slots; Mint uses Material Light Green.
// https://m2.material.io/design/color/the-color-system.html#tools-for-picking-colors
private val AndroidTimetableSwatches = listOf(
    MaterialTimetableSwatch(Color(0xFF90CAF9), Color(0xFF1976D2)), // Blue
    MaterialTimetableSwatch(Color(0xFFA5D6A7), Color(0xFF388E3C)), // Green
    MaterialTimetableSwatch(Color(0xFFFFCC80), Color(0xFFF57C00)), // Orange
    MaterialTimetableSwatch(Color(0xFFCE93D8), Color(0xFF7B1FA2)), // Purple
    MaterialTimetableSwatch(Color(0xFF80CBC4), Color(0xFF00796B)), // Teal
    MaterialTimetableSwatch(Color(0xFF9FA8DA), Color(0xFF303F9F)), // Indigo
    MaterialTimetableSwatch(Color(0xFFF48FB1), Color(0xFFC2185B)), // Pink
    MaterialTimetableSwatch(Color(0xFFC5E1A5), Color(0xFF689F38)), // Mint (Light Green)
    MaterialTimetableSwatch(Color(0xFFEF9A9A), Color(0xFFD32F2F)), // Red
    MaterialTimetableSwatch(Color(0xFF80DEEA), Color(0xFF0097A7)), // Cyan
    MaterialTimetableSwatch(Color(0xFFFFF59D), Color(0xFFFBC02D)), // Yellow
)

@Composable
actual fun platformTimetablePalette(darkTheme: Boolean): List<TimetableBlockColors> = AndroidTimetableSwatches.map { swatch ->
    val container = if (darkTheme) swatch.darkModeFill else swatch.lightModeFill
    val luminance = container.luminance()
    val blackContrast = (luminance + WCAG_LUMINANCE_OFFSET) / WCAG_LUMINANCE_OFFSET
    val whiteContrast = (1f + WCAG_LUMINANCE_OFFSET) / (luminance + WCAG_LUMINANCE_OFFSET)
    TimetableBlockColors(
        container = container,
        content = if (whiteContrast > blackContrast) Color.White else Color.Black,
    )
}

private const val WCAG_LUMINANCE_OFFSET = 0.05f
