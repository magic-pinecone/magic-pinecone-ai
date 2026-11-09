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
public val groups: ImageVector
    get() {
        if (_groups != null) {
            return _groups!!
        }
        _groups =
            ImageVector
                .Builder(
                    name = "groups",
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
                        moveTo(0f, 15f)
                        verticalLineTo(13.77f)
                        quadTo(0f, 12.71f, 0.94f, 12.1f)
                        reflectiveQuadTo(3.5f, 11.5f)
                        quadToRelative(0.31f, 0f, 0.63f, 0.03f)
                        reflectiveQuadToRelative(0.63f, 0.09f)
                        quadTo(4.4f, 12.04f, 4.2f, 12.56f)
                        reflectiveQuadTo(4f, 13.62f)
                        verticalLineTo(15f)
                        horizontalLineTo(0f)
                        close()
                        moveToRelative(5f, 0f)
                        verticalLineTo(13.73f)
                        quadTo(5f, 13.15f, 5.3f, 12.67f)
                        reflectiveQuadTo(6.1f, 11.94f)
                        quadTo(7.02f, 11.48f, 8f, 11.24f)
                        reflectiveQuadTo(9.99f, 11f)
                        quadToRelative(1.03f, 0f, 2.01f, 0.24f)
                        reflectiveQuadToRelative(1.9f, 0.7f)
                        quadToRelative(0.5f, 0.25f, 0.8f, 0.73f)
                        reflectiveQuadTo(15f, 13.73f)
                        verticalLineTo(15f)
                        horizontalLineTo(5f)
                        close()
                        moveToRelative(11f, 0f)
                        verticalLineTo(13.6f)
                        quadToRelative(0f, -0.56f, -0.2f, -1.06f)
                        reflectiveQuadTo(15.25f, 11.63f)
                        quadToRelative(0.35f, -0.06f, 0.66f, -0.09f)
                        reflectiveQuadTo(16.5f, 11.5f)
                        quadToRelative(1.63f, 0f, 2.56f, 0.6f)
                        reflectiveQuadTo(20f, 13.77f)
                        verticalLineTo(15f)
                        horizontalLineTo(16f)
                        close()
                        moveTo(6.54f, 13.5f)
                        horizontalLineToRelative(6.92f)
                        quadTo(13.31f, 13.15f, 12.22f, 12.82f)
                        reflectiveQuadTo(10f, 12.5f)
                        reflectiveQuadTo(7.78f, 12.82f)
                        reflectiveQuadTo(6.54f, 13.5f)
                        close()
                        moveTo(3.5f, 10.5f)
                        quadToRelative(-0.62f, 0f, -1.06f, -0.44f)
                        reflectiveQuadTo(2f, 9.01f)
                        quadTo(2f, 8.38f, 2.44f, 7.94f)
                        reflectiveQuadTo(3.49f, 7.5f)
                        quadToRelative(0.63f, 0f, 1.07f, 0.43f)
                        reflectiveQuadTo(5f, 9f)
                        quadTo(5f, 9.63f, 4.57f, 10.06f)
                        reflectiveQuadTo(3.5f, 10.5f)
                        close()
                        moveToRelative(13f, 0f)
                        quadToRelative(-0.62f, 0f, -1.06f, -0.44f)
                        reflectiveQuadTo(15f, 9.01f)
                        quadTo(15f, 8.38f, 15.44f, 7.94f)
                        reflectiveQuadTo(16.49f, 7.5f)
                        quadToRelative(0.63f, 0f, 1.07f, 0.43f)
                        reflectiveQuadTo(18f, 9f)
                        quadToRelative(0f, 0.62f, -0.43f, 1.06f)
                        reflectiveQuadTo(16.5f, 10.5f)
                        close()
                        moveTo(9.99f, 10f)
                        quadTo(8.96f, 10f, 8.23f, 9.27f)
                        reflectiveQuadTo(7.5f, 7.5f)
                        reflectiveQuadTo(8.23f, 5.73f)
                        reflectiveQuadTo(10f, 5f)
                        reflectiveQuadToRelative(1.77f, 0.73f)
                        reflectiveQuadTo(12.5f, 7.51f)
                        quadToRelative(0f, 1.03f, -0.73f, 1.76f)
                        reflectiveQuadTo(9.99f, 10f)
                        close()
                        moveTo(10f, 8.5f)
                        quadToRelative(0.42f, 0f, 0.71f, -0.29f)
                        reflectiveQuadTo(11f, 7.5f)
                        reflectiveQuadTo(10.71f, 6.79f)
                        quadTo(10.42f, 6.5f, 10f, 6.5f)
                        reflectiveQuadTo(9.29f, 6.79f)
                        reflectiveQuadTo(9f, 7.5f)
                        reflectiveQuadTo(9.29f, 8.21f)
                        quadTo(9.58f, 8.5f, 10f, 8.5f)
                        close()
                        moveToRelative(0f, 5f)
                        close()
                        moveToRelative(0f, -6f)
                        close()
                    }
                }.build()
        return _groups!!
    }

private var _groups: ImageVector? = null
