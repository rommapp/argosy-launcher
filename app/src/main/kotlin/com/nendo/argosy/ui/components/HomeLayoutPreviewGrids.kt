package com.nendo.argosy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.domain.model.AutoGridConfig
import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeSectionStyle
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.GridUtils
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val AUTO_GRID_SECTIONS = 3
private const val AUTO_GRID_ROWS_PER_SECTION = 2
private const val AUTO_GRID_HORIZONTAL_OVERSCAN = 2
private const val CUSTOM_GRID_PAGES = 3

/**
 * Auto-grid schematic. Column count and cell gap come from [GridUtils] against the real device
 * width, so the preview shows the same number of covers per row the library grid would.
 */
@Composable
internal fun AutoGridSchematic(
    config: AutoGridConfig,
    preview: HomeLayoutPreviewMetrics,
    gridDensity: GridDensity,
    screenWidthDp: Int,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val columns = GridUtils.getGameGridColumns(gridDensity, screenWidthDp)
    val gap = GridUtils.getGridSpacingDp(gridDensity).dp * preview.scale
    val progress = if (animate) {
        rememberLoopProgress(ComponentDefaults.HomeLayoutPreview.scrollCycleMs)
    } else {
        0f
    }
    val showHeadings = config.sectionStyle == HomeSectionStyle.HEADINGS
    Canvas(modifier = modifier.clipToBounds().fillMaxSize()) {
        val geometry = AutoGridGeometry(
            columns = columns,
            gapPx = gap.toPx(),
            coverAspectRatio = preview.coverAspectRatio,
            cornerPx = preview.coverCornerRadius.toPx(),
            headingPx = if (showHeadings) preview.barHeight.toPx() else 0f,
            titlePx = if (config.showTitles) preview.barHeight.toPx() else 0f,
            blockColor = preview.block,
            textColor = preview.text
        )
        when (config.scrollAxis) {
            HomeScrollAxis.VERTICAL -> drawVerticalAutoGrid(geometry, progress)
            HomeScrollAxis.HORIZONTAL -> drawHorizontalAutoGrid(geometry, progress)
        }
    }
}

/**
 * Custom-grid schematic: one page of [CustomGridConfig.columns] by [CustomGridConfig.rows] covers,
 * paging sideways. Cells keep the cover aspect ratio and are centred in the slot the page division
 * gives them, so a wide grid thins the covers instead of stretching them.
 */
@Composable
internal fun CustomGridSchematic(
    config: CustomGridConfig,
    preview: HomeLayoutPreviewMetrics,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(ComponentDefaults.HomeLayoutPreview.pageDwellMs.toLong())
            page += 1
        }
    }
    val offset by animateFloatAsState(
        targetValue = page.toFloat(),
        animationSpec = tween(durationMillis = Motion.durationPage, easing = Motion.argosyEase),
        label = "home-layout-preview-page"
    )
    val dotSize = ComponentDefaults.Carousel.dotSizeActive.dp * preview.scale
    val dotGap = ComponentDefaults.Carousel.dotGap.dp * preview.scale
    Canvas(modifier = modifier.clipToBounds().fillMaxSize()) {
        val lanes = config.laneCount
        val isPortrait = size.height >= size.width
        val laneExtent = if (isPortrait) size.width / lanes else size.height / lanes
        val cellHeight = if (isPortrait) laneExtent / preview.coverAspectRatio else laneExtent
        val cellWidth = if (isPortrait) laneExtent else laneExtent * preview.coverAspectRatio
        val alongCount = if (isPortrait) {
            (size.height / cellHeight).toInt()
        } else {
            (size.width / cellWidth).toInt()
        }.coerceAtLeast(1)
        drawCustomGrid(
            columns = if (isPortrait) lanes else alongCount,
            rows = if (isPortrait) alongCount else lanes,
            gapPx = preview.gap.toPx(),
            coverAspectRatio = preview.coverAspectRatio,
            cornerPx = preview.coverCornerRadius.toPx(),
            dotSizePx = dotSize.toPx(),
            dotGapPx = dotGap.toPx(),
            offset = offset,
            blockColor = preview.block,
            activeColor = preview.focus
        )
    }
}

@Composable
private fun rememberLoopProgress(cycleMs: Int): Float {
    val transition = rememberInfiniteTransition(label = "home-layout-preview-loop")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "home-layout-preview-progress"
    )
    return progress
}

private class AutoGridGeometry(
    val columns: Int,
    val gapPx: Float,
    val coverAspectRatio: Float,
    val cornerPx: Float,
    val headingPx: Float,
    val titlePx: Float,
    val blockColor: Color,
    val textColor: Color
)

private fun DrawScope.drawVerticalAutoGrid(geometry: AutoGridGeometry, progress: Float) {
    val cellWidth = cellWidthOf(geometry)
    if (cellWidth <= 0f) return
    val cellHeight = cellWidth / geometry.coverAspectRatio
    val titleBlock = titleBlockOf(geometry)
    val headingBlock = headingBlockOf(geometry)
    val rowHeight = cellHeight + titleBlock + geometry.gapPx
    val sectionHeight = headingBlock + AUTO_GRID_ROWS_PER_SECTION * rowHeight
    val contentHeight = AUTO_GRID_SECTIONS * sectionHeight
    if (contentHeight <= 0f) return
    val base = -progress * contentHeight
    repeat(2) { tile ->
        var y = base + tile * contentHeight
        repeat(AUTO_GRID_SECTIONS) {
            if (geometry.headingPx > 0f) {
                drawBar(
                    x = 0f,
                    y = y,
                    width = size.width * ComponentDefaults.HomeLayoutPreview.sectionHeadingWidthRatio,
                    height = geometry.headingPx,
                    color = geometry.textColor
                )
                y += headingBlock
            }
            repeat(AUTO_GRID_ROWS_PER_SECTION) {
                drawCoverRow(geometry, y, cellWidth, cellHeight, 0f, geometry.columns)
                y += rowHeight
            }
        }
    }
}

private fun DrawScope.drawHorizontalAutoGrid(geometry: AutoGridGeometry, progress: Float) {
    val cellWidth = cellWidthOf(geometry)
    if (cellWidth <= 0f) return
    val cellHeight = cellWidth / geometry.coverAspectRatio
    val titleBlock = titleBlockOf(geometry)
    val headingBlock = headingBlockOf(geometry)
    val rowHeight = headingBlock + cellHeight + titleBlock + geometry.gapPx
    if (rowHeight <= 0f) return
    val itemsPerRow = geometry.columns + AUTO_GRID_HORIZONTAL_OVERSCAN
    val contentWidth = itemsPerRow * (cellWidth + geometry.gapPx)
    val rows = ceil(size.height / rowHeight).toInt().coerceAtLeast(1)
    var y = 0f
    repeat(rows) {
        if (geometry.headingPx > 0f) {
            drawBar(
                x = 0f,
                y = y,
                width = size.width * ComponentDefaults.HomeLayoutPreview.sectionHeadingWidthRatio,
                height = geometry.headingPx,
                color = geometry.textColor
            )
            y += headingBlock
        }
        repeat(2) { tile ->
            val originX = -progress * contentWidth + tile * contentWidth
            drawCoverRow(geometry, y, cellWidth, cellHeight, originX, itemsPerRow)
        }
        y += cellHeight + titleBlock + geometry.gapPx
    }
}

private fun DrawScope.drawCoverRow(
    geometry: AutoGridGeometry,
    y: Float,
    cellWidth: Float,
    cellHeight: Float,
    originX: Float,
    count: Int
) {
    repeat(count) { column ->
        val x = originX + column * (cellWidth + geometry.gapPx)
        drawBlock(x, y, cellWidth, cellHeight, geometry.cornerPx, geometry.blockColor)
        if (geometry.titlePx > 0f) {
            drawBar(
                x = x,
                y = y + cellHeight + geometry.gapPx / 2f,
                width = cellWidth * ComponentDefaults.HomeLayoutPreview.coverTitleBarWidthRatio,
                height = geometry.titlePx,
                color = geometry.textColor
            )
        }
    }
}

private fun DrawScope.cellWidthOf(geometry: AutoGridGeometry): Float =
    (size.width - geometry.gapPx * (geometry.columns - 1)) / geometry.columns

private fun titleBlockOf(geometry: AutoGridGeometry): Float =
    if (geometry.titlePx > 0f) geometry.titlePx + geometry.gapPx / 2f else 0f

private fun headingBlockOf(geometry: AutoGridGeometry): Float =
    if (geometry.headingPx > 0f) geometry.headingPx + geometry.gapPx else 0f

private fun DrawScope.drawCustomGrid(
    columns: Int,
    rows: Int,
    gapPx: Float,
    coverAspectRatio: Float,
    cornerPx: Float,
    dotSizePx: Float,
    dotGapPx: Float,
    offset: Float,
    blockColor: Color,
    activeColor: Color
) {
    val dotStrip = dotSizePx + gapPx
    val pageWidth = size.width
    val gridHeight = size.height - dotStrip
    val cellWidth = (pageWidth - gapPx * (columns - 1)) / columns
    val cellHeight = (gridHeight - gapPx * (rows - 1)) / rows
    if (cellWidth <= 0f || cellHeight <= 0f) return
    val coverWidth = minOf(cellWidth, cellHeight * coverAspectRatio)
    val coverHeight = coverWidth / coverAspectRatio
    val stride = pageWidth + gapPx
    val within = offset.mod(1f) * stride
    repeat(2) { page ->
        val originX = page * stride - within
        repeat(rows) { row ->
            repeat(columns) { column ->
                drawBlock(
                    x = originX + column * (cellWidth + gapPx) + (cellWidth - coverWidth) / 2f,
                    y = row * (cellHeight + gapPx) + (cellHeight - coverHeight) / 2f,
                    width = coverWidth,
                    height = coverHeight,
                    cornerPx = cornerPx,
                    color = blockColor
                )
            }
        }
    }
    val activePage = offset.roundToInt().mod(CUSTOM_GRID_PAGES)
    val stripWidth = CUSTOM_GRID_PAGES * dotSizePx + (CUSTOM_GRID_PAGES - 1) * dotGapPx
    val stripLeft = (size.width - stripWidth) / 2f
    repeat(CUSTOM_GRID_PAGES) { index ->
        val color = if (index == activePage) {
            activeColor
        } else {
            blockColor.copy(alpha = ComponentDefaults.Carousel.dotInactiveAlpha)
        }
        drawCircle(
            color = color,
            radius = dotSizePx / 2f,
            center = Offset(
                x = stripLeft + index * (dotSizePx + dotGapPx) + dotSizePx / 2f,
                y = size.height - dotSizePx / 2f
            )
        )
    }
}

private fun DrawScope.drawBlock(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    cornerPx: Float,
    color: Color
) {
    if (width <= 0f || height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerPx, cornerPx)
    )
}

private fun DrawScope.drawBar(x: Float, y: Float, width: Float, height: Float, color: Color) {
    drawBlock(x, y, width, height, height / 2f, color)
}
