package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.theme.LocalBoxArtStyle

private val BASE_WIDTH_DP = 150.dp
private val BASE_FONT_SIZE_SP = 11.sp
private val BASE_HORIZONTAL_PADDING_DP = 4.dp
private val BASE_VERTICAL_PADDING_DP = 2.dp
private val SPINE_THICKNESS_DP = 22.dp

internal enum class SpineEdge { TOP, BOTTOM, LEFT, RIGHT }

internal fun SystemIconPosition.spineEdge(aspectRatio: Float): SpineEdge {
    val vertical = aspectRatio < 1f
    return when (this) {
        SystemIconPosition.TOP_LEFT -> if (vertical) SpineEdge.LEFT else SpineEdge.TOP
        SystemIconPosition.TOP_RIGHT -> if (vertical) SpineEdge.RIGHT else SpineEdge.TOP
        SystemIconPosition.BOTTOM_LEFT -> if (vertical) SpineEdge.LEFT else SpineEdge.BOTTOM
        SystemIconPosition.BOTTOM_RIGHT -> if (vertical) SpineEdge.RIGHT else SpineEdge.BOTTOM
        else -> SpineEdge.TOP
    }
}

internal fun coverShapeForSpine(edge: SpineEdge, outerCornerRadius: Dp): RoundedCornerShape = when (edge) {
    SpineEdge.TOP -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = outerCornerRadius, bottomEnd = outerCornerRadius)
    SpineEdge.BOTTOM -> RoundedCornerShape(topStart = outerCornerRadius, topEnd = outerCornerRadius, bottomStart = 0.dp, bottomEnd = 0.dp)
    SpineEdge.LEFT -> RoundedCornerShape(topStart = 0.dp, topEnd = outerCornerRadius, bottomStart = 0.dp, bottomEnd = outerCornerRadius)
    SpineEdge.RIGHT -> RoundedCornerShape(topStart = outerCornerRadius, topEnd = 0.dp, bottomStart = outerCornerRadius, bottomEnd = 0.dp)
}

private enum class EarPosition {
    TOP_LEFT_RIGHT, TOP_LEFT_BOTTOM,
    TOP_RIGHT_LEFT, TOP_RIGHT_BOTTOM,
    BOTTOM_LEFT_RIGHT, BOTTOM_LEFT_TOP,
    BOTTOM_RIGHT_LEFT, BOTTOM_RIGHT_TOP
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
        return Outline.Generic(when (position) {
            EarPosition.TOP_LEFT_RIGHT -> earTopLeftHorizontal(r)
            EarPosition.TOP_LEFT_BOTTOM -> earTopLeftVertical(r)
            EarPosition.TOP_RIGHT_LEFT -> earTopRightHorizontal(r)
            EarPosition.TOP_RIGHT_BOTTOM -> earTopRightVertical(r)
            EarPosition.BOTTOM_LEFT_RIGHT -> earBottomLeftHorizontal(r)
            EarPosition.BOTTOM_LEFT_TOP -> earBottomLeftVertical(r)
            EarPosition.BOTTOM_RIGHT_LEFT -> earBottomRightHorizontal(r)
            EarPosition.BOTTOM_RIGHT_TOP -> earBottomRightVertical(r)
        })
    }

    private fun earTopLeftHorizontal(r: Float): Path = Path().apply {
        moveTo(0f, 0f); lineTo(r, 0f); arcTo(Rect(0f, 0f, r * 2, r * 2), 270f, -90f, false); close()
    }
    private fun earTopLeftVertical(r: Float): Path = Path().apply {
        moveTo(0f, 0f); lineTo(r, 0f); arcTo(Rect(0f, 0f, r * 2, r * 2), 270f, -90f, false); close()
    }
    private fun earTopRightHorizontal(r: Float): Path = Path().apply {
        moveTo(r, 0f); lineTo(0f, 0f); arcTo(Rect(-r, 0f, r, r * 2), 270f, 90f, false); close()
    }
    private fun earTopRightVertical(r: Float): Path = Path().apply {
        moveTo(r, 0f); lineTo(0f, 0f); arcTo(Rect(-r, 0f, r, r * 2), 270f, 90f, false); close()
    }
    private fun earBottomLeftHorizontal(r: Float): Path = Path().apply {
        moveTo(0f, r); lineTo(r, r); arcTo(Rect(0f, -r, r * 2, r), 90f, -90f, false); close()
    }
    private fun earBottomLeftVertical(r: Float): Path = Path().apply {
        moveTo(0f, r); lineTo(0f, 0f); arcTo(Rect(0f, -r, r * 2, r), 180f, -90f, false); close()
    }
    private fun earBottomRightHorizontal(r: Float): Path = Path().apply {
        moveTo(r, r); lineTo(0f, r); arcTo(Rect(-r, -r, r, r), 90f, 90f, false); close()
    }
    private fun earBottomRightVertical(r: Float): Path = Path().apply {
        moveTo(r, r); lineTo(r, 0f); arcTo(Rect(-r, -r, r, r), 0f, -90f, false); close()
    }
}

@Composable
fun PlatformBadge(
    platformDisplayName: String,
    platformSlug: String,
    cardWidthDp: Dp,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val position = boxArtStyle.systemIconPosition
    if (position == SystemIconPosition.OFF) return

    val scale = (cardWidthDp / BASE_WIDTH_DP).coerceIn(0.5f, 2f)
    val outerCornerRadius = boxArtStyle.cornerRadiusDp
    val innerCornerRadius = boxArtStyle.cornerRadiusDp * scale

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

    val content = boxArtStyle.platformIndicatorContent
    val iconUri = resolveIconUri(platformSlug, content)
    val effectiveContent = if (content == PlatformIndicatorContent.ICON && iconUri == null) PlatformIndicatorContent.NAME else content

    val tabContent: @Composable () -> Unit = {
        BadgeInnerContent(
            content = effectiveContent,
            iconUri = iconUri,
            displayName = platformDisplayName.take(8).uppercase(),
            fontSize = fontSize,
            textColor = textColor
        )
    }

    when (position) {
        SystemIconPosition.TOP_LEFT -> TabTopLeft(
            modifier, badgeColor, innerCornerRadius, outerCornerRadius, earSize,
            horizontalPadding, verticalPadding, borderOffset, tabContent
        )
        SystemIconPosition.TOP_RIGHT -> TabTopRight(
            modifier, badgeColor, innerCornerRadius, outerCornerRadius, earSize,
            horizontalPadding, verticalPadding, borderOffset, tabContent
        )
        SystemIconPosition.BOTTOM_LEFT -> TabBottomLeft(
            modifier, badgeColor, innerCornerRadius, outerCornerRadius, earSize,
            horizontalPadding, verticalPadding, borderOffset, tabContent
        )
        SystemIconPosition.BOTTOM_RIGHT -> TabBottomRight(
            modifier, badgeColor, innerCornerRadius, outerCornerRadius, earSize,
            horizontalPadding, verticalPadding, borderOffset, tabContent
        )
        else -> Unit
    }
}

@Composable
private fun BadgeInnerContent(
    content: PlatformIndicatorContent,
    iconUri: String?,
    displayName: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color
) {
    when (content) {
        PlatformIndicatorContent.ICON -> AsyncImage(
            model = iconUri,
            contentDescription = displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size((fontSize.value * 1.5f).dp)
        )
        PlatformIndicatorContent.NAME -> Text(
            text = displayName,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor,
            lineHeight = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun TabTopLeft(
    modifier: Modifier, badgeColor: Color, innerR: Dp, outerR: Dp, earSize: Dp,
    hPad: Dp, vPad: Dp, borderOffset: Dp, body: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(topStart = outerR, bottomEnd = innerR)
    Column(modifier = modifier) {
        Row {
            Box(Modifier.clip(shape).background(badgeColor).padding(horizontal = hPad, vertical = vPad), Alignment.Center) { body() }
            Box(Modifier.offset(x = (-1).dp, y = borderOffset - 1.dp).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.TOP_LEFT_RIGHT) }).background(badgeColor))
        }
        Row {
            Box(Modifier.offset(x = borderOffset - 1.dp, y = (-1).dp).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.TOP_LEFT_BOTTOM) }).background(badgeColor))
        }
    }
}

@Composable
private fun TabTopRight(
    modifier: Modifier, badgeColor: Color, innerR: Dp, outerR: Dp, earSize: Dp,
    hPad: Dp, vPad: Dp, borderOffset: Dp, body: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(topEnd = outerR, bottomStart = innerR)
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row {
            Box(Modifier.offset(x = 1.dp, y = borderOffset - 1.dp).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.TOP_RIGHT_LEFT) }).background(badgeColor))
            Box(Modifier.clip(shape).background(badgeColor).padding(horizontal = hPad, vertical = vPad), Alignment.Center) { body() }
        }
        Row(modifier = Modifier.align(Alignment.End)) {
            Box(Modifier.offset(x = -(borderOffset - 1.dp), y = (-1).dp).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.TOP_RIGHT_BOTTOM) }).background(badgeColor))
        }
    }
}

@Composable
private fun TabBottomLeft(
    modifier: Modifier, badgeColor: Color, innerR: Dp, outerR: Dp, earSize: Dp,
    hPad: Dp, vPad: Dp, borderOffset: Dp, body: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(bottomStart = outerR, topEnd = innerR)
    Column(modifier = modifier) {
        Row {
            Box(Modifier.offset(x = borderOffset - 1.dp, y = 1.dp).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.BOTTOM_LEFT_TOP) }).background(badgeColor))
        }
        Row {
            Box(Modifier.clip(shape).background(badgeColor).padding(horizontal = hPad, vertical = vPad), Alignment.Center) { body() }
            Box(Modifier.offset(x = (-1).dp, y = -(borderOffset - 1.dp)).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.BOTTOM_LEFT_RIGHT) }).background(badgeColor))
        }
    }
}

@Composable
private fun TabBottomRight(
    modifier: Modifier, badgeColor: Color, innerR: Dp, outerR: Dp, earSize: Dp,
    hPad: Dp, vPad: Dp, borderOffset: Dp, body: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(bottomEnd = outerR, topStart = innerR)
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row(modifier = Modifier.align(Alignment.End)) {
            Box(Modifier.offset(x = -(borderOffset - 1.dp), y = 1.dp).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.BOTTOM_RIGHT_TOP) }).background(badgeColor))
        }
        Row {
            Box(Modifier.offset(x = 1.dp, y = -(borderOffset - 1.dp)).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.BOTTOM_RIGHT_LEFT) }).background(badgeColor))
            Box(Modifier.clip(shape).background(badgeColor).padding(horizontal = hPad, vertical = vPad), Alignment.Center) { body() }
        }
    }
}

@Composable
internal fun PlatformSpine(
    platformDisplayName: String,
    platformSlug: String,
    edge: SpineEdge,
    bottomAnchored: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val spineColor = when {
        isFocused && boxArtStyle.borderStyle != BoxArtBorderStyle.SOLID -> Color.Black.copy(alpha = 0.45f)
        boxArtStyle.borderStyle != BoxArtBorderStyle.SOLID -> Color.Black.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = Color.White
    val fontSize = 12.sp
    val content = boxArtStyle.platformIndicatorContent
    val iconUri = resolveIconUri(platformSlug, content)
    val effectiveContent = if (content == PlatformIndicatorContent.ICON && iconUri == null) PlatformIndicatorContent.NAME else content
    val displayName = platformDisplayName.uppercase()

    val isHorizontal = edge == SpineEdge.TOP || edge == SpineEdge.BOTTOM

    Box(
        modifier = modifier
            .background(spineColor),
        contentAlignment = Alignment.Center
    ) {
        when (effectiveContent) {
            PlatformIndicatorContent.ICON -> AsyncImage(
                model = iconUri,
                contentDescription = displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(18.dp)
            )
            PlatformIndicatorContent.NAME -> {
                val rotation = when {
                    isHorizontal -> 0f
                    edge == SpineEdge.LEFT && bottomAnchored -> 90f
                    edge == SpineEdge.LEFT -> -90f
                    edge == SpineEdge.RIGHT && bottomAnchored -> -90f
                    else -> 90f
                }
                Text(
                    text = displayName,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    lineHeight = fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .rotate(rotation)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun resolveIconUri(platformSlug: String, content: PlatformIndicatorContent): String? {
    if (content != PlatformIndicatorContent.ICON) return null
    val context = LocalContext.current
    return remember(platformSlug) { PlatformIconAssets.resolveAssetUri(context, platformSlug) }
}

@Suppress("unused")
private fun spineThickness(): Dp = SPINE_THICKNESS_DP

@Composable
fun PlatformSpinePlacement(
    platformDisplayName: String,
    platformSlug: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    cover: @Composable (coverShape: RoundedCornerShape) -> Unit
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val edge = boxArtStyle.systemIconPosition.spineEdge(boxArtStyle.aspectRatio)
    val bottomAnchored = boxArtStyle.systemIconPosition == SystemIconPosition.BOTTOM_LEFT ||
        boxArtStyle.systemIconPosition == SystemIconPosition.BOTTOM_RIGHT
    val outerR = boxArtStyle.cornerRadiusDp
    val coverShape = coverShapeForSpine(edge, outerR)
    val spineThickness = SPINE_THICKNESS_DP

    when (edge) {
        SpineEdge.TOP -> Column(modifier = modifier) {
            PlatformSpine(platformDisplayName, platformSlug, edge, bottomAnchored, isFocused,
                Modifier.fillMaxWidth().heightIn(min = spineThickness, max = spineThickness))
            Box(modifier = Modifier.weight(1f, fill = true)) { cover(coverShape) }
        }
        SpineEdge.BOTTOM -> Column(modifier = modifier) {
            Box(modifier = Modifier.weight(1f, fill = true)) { cover(coverShape) }
            PlatformSpine(platformDisplayName, platformSlug, edge, bottomAnchored, isFocused,
                Modifier.fillMaxWidth().heightIn(min = spineThickness, max = spineThickness))
        }
        SpineEdge.LEFT -> Row(modifier = modifier) {
            PlatformSpine(platformDisplayName, platformSlug, edge, bottomAnchored, isFocused,
                Modifier.fillMaxHeight().widthIn(min = spineThickness, max = spineThickness))
            Box(modifier = Modifier.weight(1f, fill = true)) { cover(coverShape) }
        }
        SpineEdge.RIGHT -> Row(modifier = modifier) {
            Box(modifier = Modifier.weight(1f, fill = true)) { cover(coverShape) }
            PlatformSpine(platformDisplayName, platformSlug, edge, bottomAnchored, isFocused,
                Modifier.fillMaxHeight().widthIn(min = spineThickness, max = spineThickness))
        }
    }
}
