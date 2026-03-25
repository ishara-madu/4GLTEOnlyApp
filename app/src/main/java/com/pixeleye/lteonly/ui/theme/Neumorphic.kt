package com.pixeleye.lteonly.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.neumorphic(
    cornerRadius: Dp = 16.dp,
    lightShadowColor: Color = NeumorphicLightShadow,
    darkShadowColor: Color = NeumorphicDarkShadow,
    backgroundColor: Color = NeumorphicBackground,
    elevation: Dp = 6.dp
) = this.drawBehind {
    val cornerRadiusPx = cornerRadius.toPx()
    val elevationPx = elevation.toPx()

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = backgroundColor.toArgb()

        // Light shadow (top-left)
        frameworkPaint.setShadowLayer(
            elevationPx,
            -elevationPx / 2,
            -elevationPx / 2,
            lightShadowColor.toArgb()
        )
        canvas.drawRoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            radiusX = cornerRadiusPx, radiusY = cornerRadiusPx, paint = paint
        )

        // Dark shadow (bottom-right)
        frameworkPaint.setShadowLayer(
            elevationPx,
            elevationPx / 2,
            elevationPx / 2,
            darkShadowColor.toArgb()
        )
        canvas.drawRoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            radiusX = cornerRadiusPx, radiusY = cornerRadiusPx, paint = paint
        )
    }
}
