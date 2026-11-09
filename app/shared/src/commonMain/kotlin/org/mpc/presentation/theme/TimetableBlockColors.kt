package org.mpc.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class TimetableBlockColors(
    val container: Color,
    val content: Color,
    val leadingAccent: Color? = null,
)

/** Returns the stable BLUE, GREEN, ORANGE, PURPLE, TEAL, INDIGO, PINK, MINT, RED, CYAN, YELLOW order. */
@Composable
expect fun platformTimetablePalette(darkTheme: Boolean): List<TimetableBlockColors>
