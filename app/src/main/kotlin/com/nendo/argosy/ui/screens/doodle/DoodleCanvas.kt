package com.nendo.argosy.ui.screens.doodle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun DoodleCanvas(
    canvasSize: CanvasSize,
    pixels: Map<Pair<Int, Int>, DoodleColor>,
    cursorX: Int,
    cursorY: Int,
    showCursor: Boolean,
    linePreview: List<Pair<Int, Int>>?,
    selectedColor: DoodleColor,
    zoomLevel: ZoomLevel,
    panOffsetX: Float,
    panOffsetY: Float,
    onTap: ((Int, Int) -> Unit)? = null,
    onDrag: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gridPixels = canvasSize.pixels

    Canvas(
        modifier = modifier
            .background(Color.White)
            .then(
                if (onTap != null || onDrag != null) {
                    Modifier.pointerInput(gridPixels, zoomLevel, panOffsetX, panOffsetY) {
                        detectTapGestures { offset ->
                            val cellSize = size.width.toFloat() / gridPixels / zoomLevel.scale
                            val adjustedX = (offset.x - panOffsetX) / zoomLevel.scale
                            val adjustedY = (offset.y - panOffsetY) / zoomLevel.scale
                            val gridX = (adjustedX / cellSize).toInt().coerceIn(0, gridPixels - 1)
                            val gridY = (adjustedY / cellSize).toInt().coerceIn(0, gridPixels - 1)
                            onTap?.invoke(gridX, gridY)
                        }
                    }.pointerInput(gridPixels, zoomLevel, panOffsetX, panOffsetY) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val cellSize = size.width.toFloat() / gridPixels / zoomLevel.scale
                            val adjustedX = (change.position.x - panOffsetX) / zoomLevel.scale
                            val adjustedY = (change.position.y - panOffsetY) / zoomLevel.scale
                            val gridX = (adjustedX / cellSize).toInt().coerceIn(0, gridPixels - 1)
                            val gridY = (adjustedY / cellSize).toInt().coerceIn(0, gridPixels - 1)
                            onDrag?.invoke(gridX, gridY)
                        }
                    }
                } else Modifier
            )
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val baseSize = minOf(canvasWidth, canvasHeight)
        val cellSize = baseSize / gridPixels

        withTransform({
            translate(panOffsetX, panOffsetY)
            scale(zoomLevel.scale, zoomLevel.scale, Offset.Zero)
        }) {
            drawRect(
                color = Color.White,
                topLeft = Offset.Zero,
                size = Size(baseSize, baseSize)
            )

            val gridColor = Color(0xFFE0E0E0)
            for (i in 0..gridPixels) {
                val pos = i * cellSize
                drawLine(
                    color = gridColor,
                    start = Offset(pos, 0f),
                    end = Offset(pos, baseSize),
                    strokeWidth = 0.5f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, pos),
                    end = Offset(baseSize, pos),
                    strokeWidth = 0.5f
                )
            }

            pixels.forEach { (coords, color) ->
                val (x, y) = coords
                drawRect(
                    color = color.color,
                    topLeft = Offset(x * cellSize, y * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }

            linePreview?.forEach { (x, y) ->
                drawRect(
                    color = selectedColor.color.copy(alpha = 0.5f),
                    topLeft = Offset(x * cellSize, y * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }

            if (showCursor) {
                val cursorPath = Path().apply {
                    val left = cursorX * cellSize
                    val top = cursorY * cellSize
                    val right = left + cellSize
                    val bottom = top + cellSize
                    moveTo(left, top)
                    lineTo(right, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                drawPath(
                    path = cursorPath,
                    color = Color.Black,
                    style = Stroke(width = 2f)
                )
                drawPath(
                    path = cursorPath,
                    color = Color.White,
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

@Composable
fun DoodlePreview(
    canvasSize: CanvasSize,
    pixels: Map<Pair<Int, Int>, DoodleColor>,
    pixelGap: Float = 0f,
    modifier: Modifier = Modifier
) {
    val gridPixels = canvasSize.pixels

    Canvas(modifier = modifier.background(Color.White)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val baseSize = minOf(canvasWidth, canvasHeight)
        val cellSize = (baseSize - (gridPixels - 1) * pixelGap) / gridPixels

        drawRect(
            color = Color.White,
            topLeft = Offset.Zero,
            size = Size(baseSize, baseSize)
        )

        pixels.forEach { (coords, color) ->
            val (x, y) = coords
            val left = x * (cellSize + pixelGap)
            val top = y * (cellSize + pixelGap)
            drawRect(
                color = color.color,
                topLeft = Offset(left, top),
                size = Size(cellSize, cellSize)
            )
        }
    }
}
