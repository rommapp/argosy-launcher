package com.nendo.argosy.ui.components.boxart

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.nendo.argosy.data.preferences.SystemIconPosition

internal class GlassCombinedShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val badgePosition: SystemIconPosition,
    private val badgeWidth: Float,
    private val badgeHeight: Float,
    private val badgeCornerRadius: Float,
    private val oneDpPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(0f, 0f, size.width, size.height, CornerRadius(outerCornerRadius))
            )
        }
        val innerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    frameWidth, frameWidth,
                    size.width - frameWidth, size.height - frameWidth,
                    CornerRadius(innerRadius)
                )
            )
        }
        val framePath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition == SystemIconPosition.OFF) {
            return Outline.Generic(framePath)
        }

        val badgePath = createBadgePath(size)
        val combinedPath = Path().apply {
            op(framePath, badgePath, PathOperation.Union)
        }
        return Outline.Generic(combinedPath)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = frameWidth - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(
                            rightEarX,
                            rightEarY,
                            rightEarX + badgeCornerRadius * 2,
                            rightEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)
                val bottomEarX = frameWidth - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX,
                            bottomEarY,
                            bottomEarX + badgeCornerRadius * 2,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = frameWidth - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(
                            leftEarX - badgeCornerRadius * 2,
                            leftEarY,
                            leftEarX,
                            leftEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)
                val bottomEarX = size.width - frameWidth + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX - badgeCornerRadius * 2,
                            bottomEarY,
                            bottomEarX,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}

internal class InnerEffectShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val effectWidth: Float,
    private val badgePosition: SystemIconPosition = SystemIconPosition.OFF,
    private val badgeWidth: Float = 0f,
    private val badgeHeight: Float = 0f,
    private val badgeCornerRadius: Float = 0f,
    private val oneDpPx: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = frameWidth,
                    top = frameWidth,
                    right = size.width - frameWidth,
                    bottom = size.height - frameWidth,
                    cornerRadius = CornerRadius(outerRadius)
                )
            )
        }
        val innerInset = frameWidth + effectWidth
        val innerRadius = (outerCornerRadius - innerInset).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = innerInset,
                    top = innerInset,
                    right = size.width - innerInset,
                    bottom = size.height - innerInset,
                    cornerRadius = CornerRadius(innerRadius)
                )
            )
        }
        var effectPath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition != SystemIconPosition.OFF) {
            val badgePath = createBadgePath(size)
            effectPath = Path().apply {
                op(effectPath, badgePath, PathOperation.Difference)
            }
        }

        return Outline.Generic(effectPath)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = frameWidth - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(
                            rightEarX,
                            rightEarY,
                            rightEarX + badgeCornerRadius * 2,
                            rightEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)
                val bottomEarX = frameWidth - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX,
                            bottomEarY,
                            bottomEarX + badgeCornerRadius * 2,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = frameWidth - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(
                            leftEarX - badgeCornerRadius * 2,
                            leftEarY,
                            leftEarX,
                            leftEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)
                val bottomEarX = size.width - frameWidth + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX - badgeCornerRadius * 2,
                            bottomEarY,
                            bottomEarX,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}

internal class GlassRingShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val innerEffectWidth: Float,
    private val startProgress: Float,
    private val endProgress: Float,
    private val badgePosition: SystemIconPosition = SystemIconPosition.OFF,
    private val badgeWidth: Float = 0f,
    private val badgeHeight: Float = 0f,
    private val badgeCornerRadius: Float = 0f,
    private val oneDpPx: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerInset = frameWidth + (innerEffectWidth * startProgress)
        val outerRadius = (outerCornerRadius - outerInset).coerceAtLeast(0f)
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = outerInset,
                    top = outerInset,
                    right = size.width - outerInset,
                    bottom = size.height - outerInset,
                    cornerRadius = CornerRadius(outerRadius)
                )
            )
        }

        val innerInset = frameWidth + (innerEffectWidth * endProgress)
        val innerRadius = (outerCornerRadius - innerInset).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = innerInset,
                    top = innerInset,
                    right = size.width - innerInset,
                    bottom = size.height - innerInset,
                    cornerRadius = CornerRadius(innerRadius)
                )
            )
        }

        var ringPath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition != SystemIconPosition.OFF) {
            val badgePath = createBadgePath(size)
            ringPath = Path().apply {
                op(ringPath, badgePath, PathOperation.Difference)
            }
        }

        return Outline.Generic(ringPath)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = frameWidth - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(rightEarX, rightEarY, rightEarX + badgeCornerRadius * 2, rightEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)
                val bottomEarX = frameWidth - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX, bottomEarY, bottomEarX + badgeCornerRadius * 2, bottomEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = frameWidth - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(leftEarX - badgeCornerRadius * 2, leftEarY, leftEarX, leftEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)
                val bottomEarX = size.width - frameWidth + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX - badgeCornerRadius * 2, bottomEarY, bottomEarX, bottomEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}

internal class GradientMaskShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val isStub: Boolean,
    private val badgePosition: SystemIconPosition,
    private val badgeWidth: Float,
    private val badgeHeight: Float,
    private val badgeCornerRadius: Float,
    private val oneDpPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()

        if (isStub) {
            path.addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(outerCornerRadius)
                )
            )
        } else {
            val outerPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = CornerRadius(outerCornerRadius)
                    )
                )
            }
            val innerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
            val innerPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = frameWidth,
                        top = frameWidth,
                        right = size.width - frameWidth,
                        bottom = size.height - frameWidth,
                        cornerRadius = CornerRadius(innerRadius)
                    )
                )
            }
            path.op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition != SystemIconPosition.OFF) {
            val badgePath = createBadgePath(size)
            path.op(path, badgePath, PathOperation.Union)
        }

        return Outline.Generic(path)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        val borderOffsetPx = frameWidth

        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = borderOffsetPx - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(rightEarX, rightEarY, rightEarX + badgeCornerRadius * 2, rightEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)

                val bottomEarX = borderOffsetPx - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX, bottomEarY, bottomEarX + badgeCornerRadius * 2, bottomEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = borderOffsetPx - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(leftEarX - badgeCornerRadius * 2, leftEarY, leftEarX, leftEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)

                val bottomEarX = size.width - borderOffsetPx + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX - badgeCornerRadius * 2, bottomEarY, bottomEarX, bottomEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}
