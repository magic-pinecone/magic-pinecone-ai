package org.mpc.presentation.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val schedule: ImageVector
    get() {
        if (_schedule != null) {
            return _schedule!!
        }
        _schedule =
            ImageVector
                .Builder(
                    name = "schedule",
                    defaultWidth = 20.dp,
                    defaultHeight = 20.dp,
                    viewportWidth = 20f,
                    viewportHeight = 20f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.Companion.NonZero,
                    ) {
                        moveTo(12.79f, 13.54f)
                        lineToRelative(1.06f, -1.06f)
                        lineToRelative(-3.1f, -3.1f)
                        verticalLineTo(5f)
                        horizontalLineTo(9.25f)
                        verticalLineToRelative(5f)
                        lineToRelative(3.54f, 3.54f)
                        close()
                        moveTo(10f, 18f)
                        quadTo(8.35f, 18f, 6.89f, 17.38f)
                        reflectiveQuadTo(4.34f, 15.66f)
                        reflectiveQuadTo(2.63f, 13.11f)
                        reflectiveQuadTo(2f, 9.99f)
                        reflectiveQuadTo(2.63f, 6.88f)
                        reflectiveQuadTo(4.34f, 4.33f)
                        reflectiveQuadTo(6.89f, 2.63f)
                        reflectiveQuadTo(10.01f, 2f)
                        reflectiveQuadToRelative(3.12f, 0.63f)
                        reflectiveQuadToRelative(2.54f, 1.71f)
                        reflectiveQuadToRelative(1.71f, 2.54f)
                        reflectiveQuadTo(18f, 10f)
                        quadToRelative(0f, 1.65f, -0.63f, 3.11f)
                        reflectiveQuadToRelative(-1.71f, 2.55f)
                        reflectiveQuadToRelative(-2.54f, 1.72f)
                        reflectiveQuadTo(10f, 18f)
                        close()
                        moveToRelative(0f, -8f)
                        close()
                        moveToRelative(0.01f, 6.5f)
                        quadToRelative(2.7f, 0f, 4.59f, -1.91f)
                        reflectiveQuadToRelative(1.9f, -4.6f)
                        reflectiveQuadTo(14.6f, 5.4f)
                        reflectiveQuadTo(10.01f, 3.5f)
                        reflectiveQuadTo(5.41f, 5.4f)
                        reflectiveQuadTo(3.5f, 9.99f)
                        reflectiveQuadToRelative(1.91f, 4.6f)
                        reflectiveQuadToRelative(4.6f, 1.91f)
                        close()
                    }
                }.build()
        return _schedule!!
    }

private var _schedule: ImageVector? = null
