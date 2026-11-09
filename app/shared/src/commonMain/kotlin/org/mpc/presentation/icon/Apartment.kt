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
public val apartment: ImageVector
    get() {
        if (_apartment != null) {
            return _apartment!!
        }
        _apartment =
            ImageVector
                .Builder(
                    name = "apartment",
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
                        moveTo(3f, 17f)
                        verticalLineTo(6f)
                        horizontalLineTo(6f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(7.5f)
                        verticalLineTo(9f)
                        horizontalLineTo(17f)
                        verticalLineToRelative(8f)
                        horizontalLineTo(11f)
                        verticalLineTo(14f)
                        horizontalLineTo(9f)
                        verticalLineToRelative(3f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(4.5f, 15.5f)
                        horizontalLineTo(6f)
                        verticalLineTo(14f)
                        horizontalLineTo(4.5f)
                        verticalLineToRelative(1.5f)
                        close()
                        moveToRelative(0f, -3.25f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineTo(4.5f)
                        verticalLineToRelative(1.5f)
                        close()
                        moveTo(4.5f, 9f)
                        horizontalLineTo(6f)
                        verticalLineTo(7.5f)
                        horizontalLineTo(4.5f)
                        verticalLineTo(9f)
                        close()
                        moveToRelative(3f, 3.25f)
                        horizontalLineTo(9f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineTo(7.5f)
                        verticalLineToRelative(1.5f)
                        close()
                        moveTo(7.5f, 9f)
                        horizontalLineTo(9f)
                        verticalLineTo(7.5f)
                        horizontalLineTo(7.5f)
                        verticalLineTo(9f)
                        close()
                        moveToRelative(0f, -3f)
                        horizontalLineTo(9f)
                        verticalLineTo(4.5f)
                        horizontalLineTo(7.5f)
                        verticalLineTo(6f)
                        close()
                        moveToRelative(3f, 6.25f)
                        horizontalLineTo(12f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineTo(10.5f)
                        verticalLineToRelative(1.5f)
                        close()
                        moveTo(10.5f, 9f)
                        horizontalLineTo(12f)
                        verticalLineTo(7.5f)
                        horizontalLineTo(10.5f)
                        verticalLineTo(9f)
                        close()
                        moveToRelative(0f, -3f)
                        horizontalLineTo(12f)
                        verticalLineTo(4.5f)
                        horizontalLineTo(10.5f)
                        verticalLineTo(6f)
                        close()
                        moveTo(14f, 15.5f)
                        horizontalLineToRelative(1.5f)
                        verticalLineTo(14f)
                        horizontalLineTo(14f)
                        verticalLineToRelative(1.5f)
                        close()
                        moveToRelative(0f, -3.25f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineTo(14f)
                        verticalLineToRelative(1.5f)
                        close()
                    }
                }.build()
        return _apartment!!
    }

private var _apartment: ImageVector? = null
