package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
        moveTo(0f, r); lineTo(r, r); arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false); close()
    }
    private fun earBottomLeftVertical(r: Float): Path = Path().apply {
        moveTo(0f, r); lineTo(r, r); arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false); close()
    }
    private fun earBottomRightHorizontal(r: Float): Path = Path().apply {
        moveTo(r, r); lineTo(0f, r); arcTo(Rect(-r, -r, r, r), 90f, -90f, false); close()
    }
    private fun earBottomRightVertical(r: Float): Path = Path().apply {
        moveTo(r, r); lineTo(0f, r); arcTo(Rect(-r, -r, r, r), 90f, -90f, false); close()
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
    if (position == SystemIconPosition.OFF || boxArtStyle.platformIndicatorStyle == com.nendo.argosy.data.preferences.PlatformIndicatorStyle.OFF) return

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
    val effectiveContent = when {
        content == PlatformIndicatorContent.ICON && iconUri == null -> PlatformIndicatorContent.NAME
        content == PlatformIndicatorContent.NAME_AND_ICON && iconUri == null -> PlatformIndicatorContent.NAME
        else -> content
    }

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
        PlatformIndicatorContent.NAME_AND_ICON -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AsyncImage(
                model = iconUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size((fontSize.value * 1.3f).dp)
            )
            Text(
                text = displayName,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                lineHeight = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
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
        Row(verticalAlignment = Alignment.Bottom) {
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
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.offset(x = 1.dp, y = -(borderOffset - 1.dp)).size(earSize)
                .clip(remember(outerR) { CurvedEarShape(outerR, EarPosition.BOTTOM_RIGHT_LEFT) }).background(badgeColor))
            Box(Modifier.clip(shape).background(badgeColor).padding(horizontal = hPad, vertical = vPad), Alignment.Center) { body() }
        }
    }
}

// Swap measured width/height so a rotated child reports a vertical bounding box
// to its parent. Compose's Modifier.rotate is draw-only and leaves the layout slot
// unchanged, which clips rotated text inside narrow vertical containers.
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE))
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2)
        )
    }
}

@Composable
private fun resolveIconUri(platformSlug: String, content: PlatformIndicatorContent): String? {
    if (content == PlatformIndicatorContent.NAME) return null
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
    coverArea: @Composable (coverShape: RoundedCornerShape) -> Unit
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val edge = boxArtStyle.systemIconPosition.spineEdge(boxArtStyle.aspectRatio)
    val bottomAnchored = boxArtStyle.systemIconPosition == SystemIconPosition.BOTTOM_LEFT ||
        boxArtStyle.systemIconPosition == SystemIconPosition.BOTTOM_RIGHT
    val outerR = boxArtStyle.cornerRadiusDp
    val spineThickness = SPINE_THICKNESS_DP + boxArtStyle.systemIconPaddingDp
    val coverShape = RoundedCornerShape(outerR)

    val margin = if (isFocused) boxArtStyle.borderThicknessDp else 0.dp
    val coverPadding = when (edge) {
        SpineEdge.TOP -> androidx.compose.foundation.layout.PaddingValues(top = spineThickness, start = margin, end = margin, bottom = margin)
        SpineEdge.BOTTOM -> androidx.compose.foundation.layout.PaddingValues(bottom = spineThickness, start = margin, end = margin, top = margin)
        SpineEdge.LEFT -> androidx.compose.foundation.layout.PaddingValues(start = spineThickness, top = margin, bottom = margin, end = margin)
        SpineEdge.RIGHT -> androidx.compose.foundation.layout.PaddingValues(end = spineThickness, top = margin, bottom = margin, start = margin)
    }

    val labelAlign = when (edge) {
        SpineEdge.TOP -> Alignment.TopCenter
        SpineEdge.BOTTOM -> Alignment.BottomCenter
        SpineEdge.LEFT -> Alignment.CenterStart
        SpineEdge.RIGHT -> Alignment.CenterEnd
    }
    val labelSize: Modifier = when (edge) {
        SpineEdge.TOP, SpineEdge.BOTTOM -> Modifier.fillMaxWidth().heightIn(min = spineThickness, max = spineThickness)
        SpineEdge.LEFT, SpineEdge.RIGHT -> Modifier.fillMaxHeight().widthIn(min = spineThickness, max = spineThickness)
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier.align(labelAlign).then(labelSize),
            contentAlignment = Alignment.Center
        ) {
            PlatformSpineLabel(
                platformDisplayName = platformDisplayName,
                platformSlug = platformSlug,
                edge = edge,
                bottomAnchored = bottomAnchored
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(coverPadding)) {
            Box(modifier = Modifier.fillMaxSize().clip(coverShape)) {
                coverArea(coverShape)
            }
        }
    }
}

@Composable
private fun PlatformSpineLabel(
    platformDisplayName: String,
    platformSlug: String,
    edge: SpineEdge,
    bottomAnchored: Boolean
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val content = boxArtStyle.platformIndicatorContent
    val iconUri = resolveIconUri(platformSlug, content)
    val effectiveContent = when {
        content == PlatformIndicatorContent.ICON && iconUri == null -> PlatformIndicatorContent.NAME
        content == PlatformIndicatorContent.NAME_AND_ICON && iconUri == null -> PlatformIndicatorContent.NAME
        else -> content
    }
    val displayName = platformDisplayName.uppercase()
    val isHorizontal = edge == SpineEdge.TOP || edge == SpineEdge.BOTTOM
    val textColor = Color.White
    val fontSize = 12.sp

    val rotation = when {
        isHorizontal -> 0f
        edge == SpineEdge.LEFT && bottomAnchored -> 90f
        edge == SpineEdge.LEFT -> -90f
        edge == SpineEdge.RIGHT && bottomAnchored -> -90f
        else -> 90f
    }

    val labelModifier = if (isHorizontal) {
        Modifier.padding(horizontal = 8.dp)
    } else {
        Modifier.vertical().graphicsLayer { rotationZ = rotation }
    }

    when (effectiveContent) {
        PlatformIndicatorContent.ICON -> AsyncImage(
            model = iconUri,
            contentDescription = displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(16.dp)
        )
        PlatformIndicatorContent.NAME -> Text(
            text = displayName,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor,
            lineHeight = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = labelModifier
        )
        PlatformIndicatorContent.NAME_AND_ICON -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = labelModifier
        ) {
            AsyncImage(
                model = iconUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = displayName,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                lineHeight = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
