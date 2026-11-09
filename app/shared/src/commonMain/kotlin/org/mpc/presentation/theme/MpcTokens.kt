package org.mpc.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Immutable
data class MpcColors(
    val background: Color,
    val surface: Color,
    val surfaceSelected: Color,
    val fillQuiet: Color,
    val onFillQuiet: Color,
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val separator: Color,
    val borderStrong: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val destructive: Color,
    val destructiveContainer: Color,
    val onDestructiveContainer: Color,
)

@Immutable
data class MpcTypography(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val supporting: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val micro: TextStyle,
)

object MpcSpacing {
    val hairline = 1.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val card = 14.dp
    val space4 = 16.dp
    val space6 = 24.dp
    val space8 = 32.dp
}

object MpcShapes {
    val swatch = RoundedCornerShape(3.dp)
    val block = RoundedCornerShape(4.dp)
    val badge = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
