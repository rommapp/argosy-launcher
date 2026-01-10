package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class StarburstShape(
    private val points: Int = 12,
    private val innerRadiusRatio: Float = 0.65f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadiusX = size.width / 2f
        val outerRadiusY = size.height / 2f
        val innerRadiusX = outerRadiusX * innerRadiusRatio
        val innerRadiusY = outerRadiusY * innerRadiusRatio

        val angleStep = (2 * PI / points).toFloat()
        val startAngle = (-PI / 2).toFloat()

        path.moveTo(
            centerX + outerRadiusX * cos(startAngle),
            centerY + outerRadiusY * sin(startAngle)
        )

        for (i in 0 until points) {
            val outerAngle = startAngle + i * angleStep
            val innerAngle = outerAngle + angleStep / 2

            path.lineTo(
                centerX + outerRadiusX * cos(outerAngle),
                centerY + outerRadiusY * sin(outerAngle)
            )
            path.lineTo(
                centerX + innerRadiusX * cos(innerAngle),
                centerY + innerRadiusY * sin(innerAngle)
            )
        }

        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun NewBadge(
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    height: Dp = 28.dp,
    rotation: Float = 15f,
    backgroundColor: Color = Color(0xFFE53935),
    textColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
            }
            .width(width)
            .height(height)
            .background(
                color = backgroundColor,
                shape = StarburstShape(points = 12, innerRadiusRatio = 0.72f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "NEW",
            color = textColor,
            fontSize = (height.value * 0.32f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
    }
}
