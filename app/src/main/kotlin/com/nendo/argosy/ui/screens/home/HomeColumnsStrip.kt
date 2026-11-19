package com.nendo.argosy.ui.screens.home

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.HomeColumnKind
import com.nendo.argosy.ui.common.alwaysCrossfade
import com.nendo.argosy.ui.components.DownloadProgressBadge
import com.nendo.argosy.ui.components.DownloadWaterFill
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.common.stackedScreenshotBackdrop
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.motionSpec
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign

private val ColumnsFocus = FocusIndicators(ring = true, halo = true)

/**
 * The art behind one column, sized to the column and framed on its subject by [frame]. The focused
 * column draws its background through the strip's `focusedBackground` slot, which defaults to this.
 */
@Composable
fun BoxScope.HomeColumnHero(item: HomeRowItem, frame: HomeHeroFrame) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val widthPx = heightPx
        val (path, isScreenshot) = when (item) {
            is HomeRowItem.Game -> rememberHomeBackgroundArt(item.game)?.let { it.path to it.isScreenshot }
            is HomeRowItem.Media -> (item.media.backdropUrl ?: item.media.posterUrl) to false
            is HomeRowItem.ViewAll -> null
        } ?: (null to false)
        val context = LocalContext.current
        val model = rememberFileImageModel(path)
        val crossfade = alwaysCrossfade(ComponentDefaults.HomeColumns.heroCrossfadeMs)
        val request = remember(model, isScreenshot, widthPx, heightPx, crossfade) {
            ImageRequest.Builder(context)
                .data(model)
                .size(widthPx, heightPx)
                .transitionFactory(crossfade)
                .apply { if (isScreenshot) stackedScreenshotBackdrop() }
                .build()
        }
        val focus = rememberHeroFocus(path, frame.spec)
        val x = mixHero(focus.narrowX, focus.wideX, frame.widthProgress)
        val zoom = mixHero(1f, focus.zoom, frame.widthProgress)
        if (model != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(x, focus.y),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin((1f + x) / 2f, (1f + focus.y) / 2f)
                    }
            )
        }
    }
}

@Composable
internal fun HomeColumnsStrip(
    state: ColumnsState,
    slots: Int,
    showFocus: Boolean,
    friendsFor: (HomeGameUi?) -> List<FriendActivity>,
    onColumnTap: (offset: Int) -> Unit,
    onColumnLongPress: () -> Unit,
    onStep: (Int) -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    focusedBackground: @Composable BoxScope.(HomeRowItem, HomeHeroFrame) -> Unit,
    downloadIndicatorFor: (HomeRowItem) -> GameDownloadIndicator,
    modifier: Modifier = Modifier,
    takeoverProgress: () -> Float = { 0f }
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val slideOutPx = constraints.maxWidth.toFloat() *
            if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val gap = Dimens.spacingSm
        val geometry = homeColumnsGeometry(maxWidth.value, slots, gap.value)
        val columnHeight = maxHeight
        val heroSpec = remember(geometry, columnHeight) { homeHeroFocusSpec(geometry, columnHeight.value) }
        val animatedPosition by animateFloatAsState(
            targetValue = state.position.toFloat(),
            animationSpec = motionSpec(tween(Motion.durationPage, easing = Motion.argosyEase)),
            label = "columns-strip"
        )
        val animatedFocus by animateFloatAsState(
            targetValue = state.focusIndex.toFloat(),
            animationSpec = motionSpec(tween(Motion.durationPage, easing = Motion.argosyEase)),
            label = "columns-focus"
        )
        val size = state.columns.size
        val slotsShown = if (state.wraps) {
            val half = slots / 2 + 1
            (floor(animatedPosition).toInt() - half..ceil(animatedPosition).toInt() + half).map { k ->
                ColumnSlot(k, k.mod(size), k - animatedPosition, k - state.position, abs(k - state.position) > slots / 2)
            }
        } else {
            state.columns.indices.map { i ->
                ColumnSlot(i, i, i - animatedFocus, i - state.focusIndex, sliver = false)
            }
        }
        slotsShown.forEach { slot ->
            key(slot.key) {
                val column = state.columns[slot.columnIndex]
                val isFocused = slot.fromFocus == 0
                val x = with(density) { (geometry.startOf(slot.offset)).dp.roundToPx() }
                HomeColumnCard(
                    column = column,
                    itemIndex = state.itemIndexOf(column),
                    isFocused = isFocused,
                    showFocus = showFocus,
                    sliver = slot.sliver,
                    width = geometry.widthAt(slot.offset).dp,
                    height = columnHeight,
                    heroFrame = HomeHeroFrame(heroSpec, geometry.widthProgressAt(slot.offset)),
                    friendsFor = friendsFor,
                    onTap = { onColumnTap(slot.fromFocus) },
                    onLongPress = { if (isFocused) onColumnLongPress() else onColumnTap(slot.fromFocus) },
                    onStep = onStep,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    focusedBackground = focusedBackground,
                    downloadIndicatorFor = downloadIndicatorFor,
                    modifier = Modifier
                        .offset { IntOffset(x, 0) }
                        .graphicsLayer {
                            translationX = slot.fromFocus.sign * takeoverProgress() * slideOutPx
                        }
                )
            }
        }
    }
}

private data class ColumnSlot(
    val key: Int,
    val columnIndex: Int,
    val offset: Float,
    val fromFocus: Int,
    val sliver: Boolean
)

@Composable
private fun HomeColumnCard(
    column: HomeColumnUi,
    itemIndex: Int,
    isFocused: Boolean,
    showFocus: Boolean,
    sliver: Boolean,
    width: Dp,
    height: Dp,
    heroFrame: HomeHeroFrame,
    friendsFor: (HomeGameUi?) -> List<FriendActivity>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onStep: (Int) -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    focusedBackground: @Composable BoxScope.(HomeRowItem, HomeHeroFrame) -> Unit,
    downloadIndicatorFor: (HomeRowItem) -> GameDownloadIndicator,
    modifier: Modifier = Modifier
) {
    val item = column.items.getOrNull(itemIndex) ?: return
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusPanel)
    val dim by animateFloatAsState(
        targetValue = when {
            isFocused -> 0f
            sliver -> ComponentDefaults.HomeColumns.sliverDimAlpha
            else -> ComponentDefaults.HomeColumns.unfocusedDimAlpha
        },
        animationSpec = tween(Motion.durationPage, easing = Motion.argosyEase),
        label = "column-dim"
    )
    val secondary = Color.White.copy(alpha = ComponentDefaults.HomeColumns.secondaryTextAlpha)
    val footerShown by animateFloatAsState(
        targetValue = if (isFocused) 0f else 1f,
        animationSpec = tween(Motion.durationPage, easing = Motion.argosyEase),
        label = "column-footer"
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .argosyFocusIndicators(
                focused = isFocused && showFocus,
                indicators = ColumnsFocus,
                ringThickness = ComponentDefaults.HomeColumns.ringWidthDp.dp,
                shape = shape
            )
            .clip(shape)
            .background(theme.surfaceBase)
            .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
    ) {
        val download = downloadIndicatorFor(item)
        if (download.isShown) {
            DownloadWaterFill(
                progress = download.progress,
                paused = download.isPaused,
                waveAmplitudePx = { it * ComponentDefaults.HomeColumns.waterAmplitudeRatio },
                modifier = Modifier.matchParentSize()
            ) {
                if (isFocused) focusedBackground(item, heroFrame) else HomeColumnHero(item, heroFrame)
            }
        } else {
            if (isFocused) focusedBackground(item, heroFrame) else HomeColumnHero(item, heroFrame)
        }
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
        if (sliver) return@Box
        if (download.isShown) {
            DownloadProgressBadge(
                progress = download.progress,
                paused = download.isPaused,
                badgeSize = if (isFocused) Dimens.iconXl else Dimens.iconLg,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * ComponentDefaults.HomeColumns.topScrimHeightRatio)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = ComponentDefaults.HomeColumns.topScrimAlpha), Color.Transparent)
                    )
                )
        )
        if (footerShown > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(height * ComponentDefaults.HomeColumns.bottomScrimHeightRatio)
                    .graphicsLayer { alpha = footerShown }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = ComponentDefaults.HomeColumns.bottomScrimAlpha))
                        )
                    )
            )
        }
        ColumnLabel(
            column = column,
            accent = if (isFocused && showFocus) theme.focusAccent else Color.White,
            secondary = secondary,
            modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
        )
        if (footerShown > 0f) {
            ColumnFooter(
                column = column,
                item = item,
                shown = footerShown,
                coverWidth = width * ComponentDefaults.HomeColumns.coverWidthRatio,
                secondary = secondary,
                friends = friendsFor((item as? HomeRowItem.Game)?.game),
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.spacingSm)
            )
        }
        if (isFocused && column.items.size > 1) {
            StepControls(
                count = column.items.size,
                index = itemIndex,
                accent = theme.focusAccent,
                onStep = onStep,
                modifier = Modifier
                    .align(BiasAlignment(1f, ComponentDefaults.HomeColumns.stepControlsCenterRatio * 2f - 1f))
                    .padding(end = Dimens.spacingXs)
            )
        }
    }
}

@Composable
private fun ColumnLabel(column: HomeColumnUi, accent: Color, secondary: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (column.kind == HomeColumnKind.PLATFORM) {
            Text(
                text = homeColumnTitle(column),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = homeColumnTitle(column),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = ComponentDefaults.HomeColumns.kickerTrackingRatio.em
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = homeColumnReason(column),
            style = MaterialTheme.typography.labelSmall,
            color = secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColumnFooter(
    column: HomeColumnUi,
    item: HomeRowItem,
    shown: Float,
    coverWidth: Dp,
    secondary: Color,
    friends: List<FriendActivity>,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        ColumnCover(
            item = item,
            onCoverLoadFailed = onCoverLoadFailed,
            onCoverLoaded = onCoverLoaded,
            modifier = Modifier
                .width(coverWidth)
                .graphicsLayer {
                    alpha = shown
                    translationY = (1f - shown) * size.height / 2f
                }
        )
        Spacer(Modifier.width(Dimens.spacingSm))
        Column(Modifier.weight(1f).graphicsLayer { alpha = shown }) {
            Text(
                text = when (item) {
                    is HomeRowItem.Game -> item.game.title
                    is HomeRowItem.Media -> item.media.title
                    is HomeRowItem.ViewAll -> ""
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            homeColumnFact(column, item, friends)?.let { fact ->
                Text(
                    text = fact,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ColumnCover(
    item: HomeRowItem,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val boxArt = LocalBoxArtStyle.current
    val shape = RoundedCornerShape(boxArt.cornerRadiusDp)
    val game = (item as? HomeRowItem.Game)?.game
    val path = when (item) {
        is HomeRowItem.Game -> rememberResolvedCoverPath(item.game.id, item.game.coverPath)
        is HomeRowItem.Media -> item.media.posterUrl
        is HomeRowItem.ViewAll -> null
    }
    Box(
        modifier
            .aspectRatio(boxArt.aspectRatio)
            .clip(shape)
            .background(LocalArgosyTheme.current.surfaceBase)
    ) {
        AsyncImage(
            model = rememberFileImageModel(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { result ->
                val bitmap = (result.result.drawable as? BitmapDrawable)?.bitmap
                if (game != null && bitmap != null) onCoverLoaded(game.id, bitmap)
            },
            onError = { if (game != null && path != null) onCoverLoadFailed(game.id, path) }
        )
    }
}

@Composable
private fun StepControls(
    count: Int,
    index: Int,
    accent: Color,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        StepButton(
            enabled = index > 0,
            accent = accent,
            up = true,
            onClick = { onStep(-1) }
        )
        repeat(count) { dot ->
            val active = dot == index
            Box(
                Modifier
                    .size(
                        if (active) ComponentDefaults.HomeColumns.dotSizeActiveDp.dp
                        else ComponentDefaults.HomeColumns.dotSizeDp.dp
                    )
                    .clip(CircleShape)
                    .background(
                        Color.White.copy(alpha = if (active) 1f else ComponentDefaults.HomeColumns.dotInactiveAlpha)
                    )
            )
        }
        StepButton(
            enabled = index < count - 1,
            accent = accent,
            up = false,
            onClick = { onStep(1) }
        )
    }
}

@Composable
private fun StepButton(enabled: Boolean, accent: Color, up: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0f
    Box(
        modifier = Modifier
            .size(ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp)
            .clickableNoFocus(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (up) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(
                if (up) R.string.home_columns_previous_game else R.string.home_columns_next_game
            ),
            tint = accent.copy(alpha = alpha),
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}
