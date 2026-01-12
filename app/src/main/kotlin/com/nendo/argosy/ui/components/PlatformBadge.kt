package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.theme.LocalBoxArtStyle

private val BASE_WIDTH_DP = 150.dp
private val BASE_FONT_SIZE_SP = 11.sp
private val BASE_HORIZONTAL_PADDING_DP = 4.dp
private val BASE_VERTICAL_PADDING_DP = 2.dp

private enum class EarPosition {
    TOP_LEFT_RIGHT,
    TOP_LEFT_BOTTOM,
    TOP_RIGHT_LEFT,
    TOP_RIGHT_BOTTOM
}

private class CurvedEarShape(
    private val cornerRadius: Dp,
    private val position: EarPosition
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val path = when (position) {
            EarPosition.TOP_LEFT_RIGHT -> createTopLeftRightEar(r)
            EarPosition.TOP_LEFT_BOTTOM -> createTopLeftBottomEar(r)
            EarPosition.TOP_RIGHT_LEFT -> createTopRightLeftEar(r)
            EarPosition.TOP_RIGHT_BOTTOM -> createTopRightBottomEar(r)
        }
        return Outline.Generic(path)
    }

    private fun createTopLeftRightEar(r: Float): Path = Path().apply {
        moveTo(0f, 0f)
        lineTo(r, 0f)
        arcTo(Rect(0f, 0f, r * 2, r * 2), 270f, -90f, false)
        close()
    }

    private fun createTopLeftBottomEar(r: Float): Path = Path().apply {
        moveTo(0f, 0f)
        lineTo(r, 0f)
        arcTo(Rect(0f, 0f, r * 2, r * 2), 270f, -90f, false)
        close()
    }

    private fun createTopRightLeftEar(r: Float): Path = Path().apply {
        moveTo(r, 0f)
        lineTo(0f, 0f)
        arcTo(Rect(-r, 0f, r, r * 2), 270f, 90f, false)
        close()
    }

    private fun createTopRightBottomEar(r: Float): Path = Path().apply {
        moveTo(r, 0f)
        lineTo(0f, 0f)
        arcTo(Rect(-r, 0f, r, r * 2), 270f, 90f, false)
        close()
    }
}

@Composable
fun PlatformBadge(
    platformDisplayName: String,
    cardWidthDp: Dp,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val displayText = platformDisplayName.take(8).uppercase()

    val scale = (cardWidthDp / BASE_WIDTH_DP).coerceIn(0.5f, 2f)
    val outerCornerRadius = boxArtStyle.cornerRadiusDp
    val innerCornerRadius = boxArtStyle.cornerRadiusDp * scale
    val position = boxArtStyle.systemIconPosition

    val badgeShape = when (position) {
        SystemIconPosition.TOP_LEFT -> RoundedCornerShape(
            topStart = outerCornerRadius,
            bottomEnd = innerCornerRadius
        )
        SystemIconPosition.TOP_RIGHT -> RoundedCornerShape(
            topEnd = outerCornerRadius,
            bottomStart = innerCornerRadius
        )
        else -> RoundedCornerShape(bottomEnd = innerCornerRadius)
    }

    val fontSize = BASE_FONT_SIZE_SP * scale
    val userPadding = boxArtStyle.systemIconPaddingDp
    val borderPadding = if (isFocused) boxArtStyle.borderThicknessDp * 1.5f else 0.dp
    val horizontalPadding = (BASE_HORIZONTAL_PADDING_DP + userPadding + borderPadding) * scale
    val verticalPadding = (BASE_VERTICAL_PADDING_DP + userPadding / 2 + borderPadding / 2) * scale

    val badgeColor = when {
        isFocused && boxArtStyle.borderStyle != BoxArtBorderStyle.SOLID -> Color.Transparent
        boxArtStyle.borderStyle != BoxArtBorderStyle.SOLID -> Color.Black.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = Color.White
    val borderOffset = if (isFocused) boxArtStyle.borderThicknessDp else 0.dp
    val isExtremeCase = isFocused && boxArtStyle.borderThicknessDp >= 4.dp && userPadding <= 1.dp
    val earSize = if (isExtremeCase) outerCornerRadius - boxArtStyle.borderThicknessDp / 2 else outerCornerRadius

    when (position) {
        SystemIconPosition.TOP_LEFT -> {
            Column(modifier = modifier) {
                Row {
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(badgeColor)
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayText,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            lineHeight = fontSize
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = (-1).dp, y = borderOffset - 1.dp)
                            .size(earSize)
                            .clip(remember(outerCornerRadius) { CurvedEarShape(outerCornerRadius, EarPosition.TOP_LEFT_RIGHT) })
                            .background(badgeColor)
                    )
                }
                Row {
                    Box(
                        modifier = Modifier
                            .offset(x = borderOffset - 1.dp, y = (-1).dp)
                            .size(earSize)
                            .clip(remember(outerCornerRadius) { CurvedEarShape(outerCornerRadius, EarPosition.TOP_LEFT_BOTTOM) })
                            .background(badgeColor)
                    )
                }
            }
        }
        SystemIconPosition.TOP_RIGHT -> {
            Column(modifier = modifier, horizontalAlignment = Alignment.End) {
                Row {
                    Box(
                        modifier = Modifier
                            .offset(x = 1.dp, y = borderOffset - 1.dp)
                            .size(earSize)
                            .clip(remember(outerCornerRadius) { CurvedEarShape(outerCornerRadius, EarPosition.TOP_RIGHT_LEFT) })
                            .background(badgeColor)
                    )
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(badgeColor)
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayText,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            lineHeight = fontSize
                        )
                    }
                }
                Row(modifier = Modifier.align(Alignment.End)) {
                    Box(
                        modifier = Modifier
                            .offset(x = -(borderOffset - 1.dp), y = (-1).dp)
                            .size(earSize)
                            .clip(remember(outerCornerRadius) { CurvedEarShape(outerCornerRadius, EarPosition.TOP_RIGHT_BOTTOM) })
                            .background(badgeColor)
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(badgeShape)
                    .background(badgeColor)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayText,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    lineHeight = fontSize
                )
            }
        }
    }
}
