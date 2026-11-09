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
public val key: ImageVector
    get() {
        if (_key != null) {
            return _key!!
        }
        _key =
            ImageVector
                .Builder(
                    name = "key",
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
                        moveTo(4.58f, 11.42f)
                        quadTo(4f, 10.83f, 4f, 10f)
                        reflectiveQuadTo(4.58f, 8.58f)
                        reflectiveQuadTo(6f, 8f)
                        reflectiveQuadTo(7.42f, 8.58f)
                        reflectiveQuadTo(8f, 10f)
                        reflectiveQuadTo(7.42f, 11.42f)
                        reflectiveQuadTo(6f, 12f)
                        reflectiveQuadTo(4.58f, 11.42f)
                        close()
                        moveTo(6f, 15f)
                        quadTo(3.92f, 15f, 2.46f, 13.54f)
                        reflectiveQuadTo(1f, 10f)
                        reflectiveQuadTo(2.46f, 6.46f)
                        reflectiveQuadTo(6f, 5f)
                        quadTo(7.35f, 5f, 8.5f, 5.68f)
                        reflectiveQuadTo(10.33f, 7.5f)
                        horizontalLineTo(17.5f)
                        lineTo(20f, 10f)
                        lineToRelative(-3.75f, 3.5f)
                        lineTo(14.5f, 12.25f)
                        lineTo(13f, 13.5f)
                        lineTo(11f, 12f)
                        horizontalLineTo(10.58f)
                        quadToRelative(-0.5f, 1.42f, -1.78f, 2.21f)
                        reflectiveQuadTo(6f, 15f)
                        close()
                        moveTo(6f, 13.5f)
                        quadToRelative(1.31f, 0f, 2.31f, -0.84f)
                        reflectiveQuadTo(9.46f, 10.5f)
                        horizontalLineTo(11.5f)
                        lineToRelative(1.46f, 1.08f)
                        lineToRelative(1.48f, -1.23f)
                        lineToRelative(1.69f, 1.21f)
                        lineTo(17.83f, 9.98f)
                        lineTo(16.88f, 9f)
                        horizontalLineTo(9.35f)
                        quadTo(8.96f, 7.9f, 8.05f, 7.2f)
                        reflectiveQuadTo(6f, 6.5f)
                        quadTo(4.54f, 6.5f, 3.52f, 7.52f)
                        reflectiveQuadTo(2.5f, 10f)
                        reflectiveQuadToRelative(1.02f, 2.48f)
                        reflectiveQuadTo(6f, 13.5f)
                        close()
                    }
                }.build()
        return _key!!
    }

private var _key: ImageVector? = null
