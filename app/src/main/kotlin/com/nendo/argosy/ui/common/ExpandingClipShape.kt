package com.nendo.argosy.ui.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rounded rectangle that grows from [from] at [progress] 0 to the whole layout at 1, its corner
 * radius shrinking from [radiusPx] to square on the way.
 */
class ExpandingClipShape(
    private val from: Rect,
    private val progress: Float,
    private val radiusPx: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = lerp(from, Rect(Offset.Zero, size), progress)
        return Outline.Rounded(RoundRect(rect, CornerRadius(radiusPx * (1f - progress))))
    }
}
