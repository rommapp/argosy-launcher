package com.nendo.argosy.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.aspectRatioClassOf
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * How the Columns details band lays out on one screen. [FULL] puts the cover beside the text and
 * lets it rise into the strip, [COMPACT] keeps the cover inside a taller band, and [STACKED] puts
 * the progress panel under the cover and title.
 */
enum class HomeColumnsBandTier { FULL, COMPACT, STACKED }

/**
 * The band tier for a [widthDp] by [heightDp] screen. A screen no wider than it is tall stacks; a
 * landscape screen is [HomeColumnsBandTier.FULL] when its aspect class is wide and it is at least
 * [ComponentDefaults.HomeColumns.compactWidthDp] across, else [HomeColumnsBandTier.COMPACT].
 */
fun homeColumnsBandTier(widthDp: Int, heightDp: Int): HomeColumnsBandTier = when {
    widthDp.toFloat() / heightDp.coerceAtLeast(1) <= 1f -> HomeColumnsBandTier.STACKED
    aspectRatioClassOf(widthDp, heightDp).isWide && widthDp >= ComponentDefaults.HomeColumns.compactWidthDp ->
        HomeColumnsBandTier.FULL
    else -> HomeColumnsBandTier.COMPACT
}

internal val HomeColumnsBandTier.stripWeight: Float
    get() = when (this) {
        HomeColumnsBandTier.FULL -> ComponentDefaults.HomeColumns.stripWeightRatio
        HomeColumnsBandTier.COMPACT -> ComponentDefaults.HomeColumns.stripWeightRatioCompact
        HomeColumnsBandTier.STACKED -> ComponentDefaults.HomeColumns.stripWeightRatioStacked
    }

internal val HomeColumnsBandTier.bandWeight: Float
    get() = when (this) {
        HomeColumnsBandTier.FULL -> ComponentDefaults.HomeColumns.bandWeightRatio
        HomeColumnsBandTier.COMPACT -> ComponentDefaults.HomeColumns.bandWeightRatioCompact
        HomeColumnsBandTier.STACKED -> ComponentDefaults.HomeColumns.bandWeightRatioStacked
    }

internal val HomeColumnsBandTier.coverOverlap: Float
    get() = if (this == HomeColumnsBandTier.FULL) {
        ComponentDefaults.HomeColumns.bandCoverOverlapRatio
    } else {
        ComponentDefaults.HomeColumns.bandCoverOverlapRatioCompact
    }

internal val HomeColumnsBandTier.titleWeight: Float
    get() = if (this == HomeColumnsBandTier.FULL) {
        ComponentDefaults.HomeColumns.bandTitleWeightRatio
    } else {
        ComponentDefaults.HomeColumns.bandTitleWeightRatioCompact
    }

internal val HomeColumnsBandTier.titleMaxLines: Int
    get() = if (this == HomeColumnsBandTier.FULL) FULL_TITLE_MAX_LINES else ComponentDefaults.HomeColumns.bandTitleMaxLinesCompact

internal val HomeColumnsBandTier.bandPadding: Dp
    @Composable get() = if (this == HomeColumnsBandTier.FULL) Dimens.spacingXxl else Dimens.spacingLg

internal val HomeColumnsBandTier.titlePanelGap: Dp
    @Composable get() = if (this == HomeColumnsBandTier.FULL) Dimens.spacingXl else Dimens.spacingLg

internal val HomeColumnsBandTier.titleGap: Dp
    @Composable get() = if (this == HomeColumnsBandTier.FULL) Dimens.spacingSm else Dimens.spacingXs

private const val FULL_TITLE_MAX_LINES = 1

internal fun <T> dropUntilFits(parts: List<T>, dropOrder: List<T>, fits: (List<T>) -> Boolean): List<T> {
    var kept = parts
    dropOrder.forEach { next ->
        if (fits(kept)) return kept
        kept = kept - next
    }
    return kept
}

internal fun <T> firstFitting(candidates: List<T>, fallback: T, fits: (T) -> Boolean): T =
    candidates.firstOrNull(fits) ?: fallback

internal fun platformLabel(fullName: String, shortName: String, fits: (String) -> Boolean): String =
    if (shortName.isBlank()) fullName else firstFitting(listOf(fullName), shortName, fits)

enum class BandMetaPart { PLATFORM, YEAR, GENRE }

internal val BAND_META_DROP_ORDER = listOf(BandMetaPart.GENRE, BandMetaPart.YEAR)

internal data class BandMetaFit(val platformName: String, val parts: List<BandMetaPart>)

internal fun fitBandMeta(
    fullName: String,
    shortName: String,
    parts: List<BandMetaPart>,
    fits: (platformName: String, parts: List<BandMetaPart>) -> Boolean
): BandMetaFit {
    val name = platformLabel(fullName, shortName) { fits(it, parts) }
    return BandMetaFit(name, dropUntilFits(parts, BAND_META_DROP_ORDER) { fits(name, it) })
}

enum class BandBadgePart { FRIENDS, COMMUNITY_RATING, USER_RATING, DIFFICULTY, STATUS, STATUS_ICON }

internal val BAND_BADGE_DROP_ORDER = listOf(
    BandBadgePart.DIFFICULTY,
    BandBadgePart.COMMUNITY_RATING,
    BandBadgePart.USER_RATING
)

internal fun fitBandBadges(parts: List<BandBadgePart>, fits: (List<BandBadgePart>) -> Boolean): List<BandBadgePart> {
    val kept = dropUntilFits(parts, BAND_BADGE_DROP_ORDER, fits)
    if (fits(kept) || BandBadgePart.STATUS !in kept) return kept
    return kept.map { if (it == BandBadgePart.STATUS) BandBadgePart.STATUS_ICON else it }
}

internal const val DROP_FIRST = 0
internal const val DROP_SECOND = 1

private data class DropRank(val rank: Int)

internal fun Modifier.dropRank(rank: Int): Modifier = layoutId(DropRank(rank))

@Composable
internal fun DropColumn(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val child = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(child) }
        val ranks = measurables.map { (it.layoutId as? DropRank)?.rank }
        val shown = placeables.indices.filter { placeables[it].height > 0 }
        val dropOrder = shown.filter { ranks[it] != null }.sortedBy { ranks[it] }
        val stackHeight = { rows: List<Int> ->
            rows.sumOf { placeables[it].height } + gapPx * (rows.size - 1).coerceAtLeast(0)
        }
        val kept = if (constraints.hasBoundedHeight) {
            dropUntilFits(shown, dropOrder) { stackHeight(it) <= constraints.maxHeight }
        } else {
            shown
        }
        val width = constraints.constrainWidth(kept.maxOfOrNull { placeables[it].width } ?: 0)
        layout(width, constraints.constrainHeight(stackHeight(kept))) {
            var y = 0
            kept.forEach { index ->
                placeables[index].placeRelative(0, y)
                y += placeables[index].height + gapPx
            }
        }
    }
}

@Composable
internal fun BandBadgeRow(
    gap: Dp,
    parts: List<BandBadgePart>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val byPart = measurables.mapNotNull { measurable ->
            (measurable.layoutId as? BandBadgePart)?.let { it to measurable.measure(Constraints(maxHeight = constraints.maxHeight)) }
        }.toMap()
        val rowWidth = { placed: List<BandBadgePart> ->
            placed.sumOf { byPart[it]?.width ?: 0 } + gapPx * (placed.size - 1).coerceAtLeast(0)
        }
        val kept = if (constraints.hasBoundedWidth) {
            fitBandBadges(parts) { rowWidth(it) <= constraints.maxWidth }
        } else {
            parts
        }
        val height = kept.maxOfOrNull { byPart[it]?.height ?: 0 } ?: 0
        layout(constraints.constrainWidth(rowWidth(kept)), constraints.constrainHeight(height)) {
            var x = 0
            kept.forEach { part ->
                byPart[part]?.let { placeable ->
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width + gapPx
                }
            }
        }
    }
}
