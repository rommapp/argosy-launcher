package com.nendo.argosy.ui.components.playtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.Instant
import kotlin.math.roundToInt

data class MosaicGame(
    val gameId: Long,
    val title: String,
    val activeMs: Long,
    val lastPlayed: Instant,
    val coverPath: String?
)

/**
 * One treemap tile. [gameId] is null for the Others tile, which folds [foldedCount] games that
 * were too small to stand on their own; [foldedCovers] holds their cover paths, most played
 * first, for the collage it draws.
 */
data class MosaicTile(
    val key: String,
    val gameId: Long?,
    val title: String,
    val activeMs: Long,
    val lastPlayed: Instant?,
    val coverPath: String?,
    val foldedCount: Int,
    val foldedCovers: List<String> = emptyList()
)

/**
 * Picks the games that get a tile of their own: at most [maxTiles], each holding at least
 * [minShare] of the total, largest first. Everything else folds into one Others tile at the
 * end, so tile area stays proportional to active time across the whole set.
 */
fun foldMosaic(games: List<MosaicGame>, maxTiles: Int, minShare: Float): List<MosaicTile> {
    val played = games.filter { it.activeMs > 0L }.sortedByDescending { it.activeMs }
    val total = played.sumOf { it.activeMs }
    if (total <= 0L) return emptyList()
    val kept = played.take(maxTiles.coerceAtLeast(1))
        .takeWhile { it.activeMs.toDouble() / total >= minShare }
        .ifEmpty { played.take(1) }
    val tiles = kept.map { game ->
        MosaicTile(
            key = "game_${game.gameId}",
            gameId = game.gameId,
            title = game.title,
            activeMs = game.activeMs,
            lastPlayed = game.lastPlayed,
            coverPath = game.coverPath,
            foldedCount = 0
        )
    }
    val othersMs = total - kept.sumOf { it.activeMs }
    if (othersMs <= 0L) return tiles
    val folded = played.drop(kept.size)
    return tiles + MosaicTile(
        key = "others",
        gameId = null,
        title = "",
        activeMs = othersMs,
        lastPlayed = null,
        coverPath = null,
        foldedCount = folded.size,
        foldedCovers = folded.mapNotNull { it.coverPath }
    )
}

private const val COLLAGE_SCRIM_ALPHA = 0.85f

/**
 * Top games as a squarified cover treemap: tile area is proportional to active time, each tile
 * drawing the game's cover through the same loader the home tiles use, or a tinted tile with the
 * title where no cover exists. While [isEngaged] the selected tile grows over its neighbours.
 */
@Composable
fun PlayCoverMosaic(
    tiles: List<MosaicTile>,
    othersLabel: String,
    othersCountLabel: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalUiScale.current.scale
    val density = LocalDensity.current
    val height = (ComponentDefaults.PlayTimeChart.mosaicHeight * s).dp
    val gap = (ComponentDefaults.PlayTimeChart.surfaceGap * s).dp
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val fallbackTint = MaterialTheme.colorScheme.primary.copy(alpha = ComponentDefaults.PlayTimeChart.mosaicFallbackAlpha)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { height.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val rects = remember(tiles, widthPx, heightPx, gapPx) {
            Squarify.layout(tiles.map { it.activeMs.toFloat() }, TreemapRect(0f, 0f, widthPx, heightPx))
                .map { it.inset(gapPx / 2f) }
        }
        tiles.forEachIndexed { index, tile ->
            val rect = rects.getOrNull(index) ?: return@forEachIndexed
            if (rect.width <= 0f || rect.height <= 0f) return@forEachIndexed
            key(tile.key) {
                MosaicTileBox(
                    tile = tile,
                    rect = rect,
                    othersLabel = othersLabel,
                    othersCountLabel = othersCountLabel,
                    shape = shape,
                    fallbackTint = fallbackTint,
                    onTap = onOpen
                )
            }
        }
    }
}

@Composable
private fun MosaicTileBox(
    tile: MosaicTile,
    rect: TreemapRect,
    othersLabel: String,
    othersCountLabel: String?,
    shape: RoundedCornerShape,
    fallbackTint: Color,
    onTap: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val density = LocalDensity.current
    val cover = rememberFileImageModel(tile.coverPath)
    val isOthers = tile.gameId == null
    Box(
        modifier = Modifier
            .offset { IntOffset(rect.x.roundToInt(), rect.y.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .clip(shape)
            .background(if (cover != null) theme.surfaceBase else fallbackTint)
            .clickableNoFocus(onClick = onTap)
    ) {
        when {
            isOthers -> OthersCollage(
                covers = tile.foldedCovers,
                widthPx = rect.width,
                heightPx = rect.height,
                label = othersLabel,
                countLabel = othersCountLabel
            )
            cover != null -> AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(Dimens.spacingXs),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun OthersCollage(
    covers: List<String>,
    widthPx: Float,
    heightPx: Float,
    label: String,
    countLabel: String?
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val density = LocalDensity.current
    val cell = (ComponentDefaults.PlayTimeChart.mosaicCollageCell * s).dp
    val cellPx = with(density) { cell.toPx() }
    val columns = (widthPx / cellPx).toInt().coerceAtLeast(1)
    val rows = (heightPx / cellPx).toInt().coerceAtLeast(1)
    val shown = remember(covers, columns, rows) { covers.take(columns * rows) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().alpha(ComponentDefaults.PlayTimeChart.mosaicCollageAlpha)) {
            shown.chunked(columns).forEach { row ->
                Row {
                    row.forEach { path ->
                        key(path) {
                            val model = rememberFileImageModel(path)
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(cell)
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, theme.surfaceBase.copy(alpha = COLLAGE_SCRIM_ALPHA))
                    )
                )
                .padding(Dimens.spacingXs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (countLabel != null) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun TreemapRect.inset(amount: Float): TreemapRect {
    val w = (width - 2 * amount).coerceAtLeast(0f)
    val h = (height - 2 * amount).coerceAtLeast(0f)
    return TreemapRect(x + amount, y + amount, w, h)
}

