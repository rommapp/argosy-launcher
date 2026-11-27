package com.nendo.argosy.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.common.color
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.playerCountGlyph
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.StatBadge
import com.nendo.argosy.ui.components.StatBadgeItem
import com.nendo.argosy.ui.components.friends.FriendsActivityBadge
import com.nendo.argosy.ui.components.gameStatBadges
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion

@Composable
internal fun HomeColumnsSurface(
    uiState: HomeUiState,
    showFocus: Boolean,
    onSlotCount: (Int) -> Unit,
    onColumnTap: (offset: Int) -> Unit,
    onColumnLongPress: () -> Unit,
    onStep: (Int) -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    downloadIndicatorFor: (HomeRowItem) -> GameDownloadIndicator,
    modifier: Modifier = Modifier,
    takeoverProgress: () -> Float = { 0f },
    focusedBackground: @Composable BoxScope.(HomeRowItem, HomeHeroFrame) -> Unit = { item, frame ->
        HomeColumnHero(item, frame)
    }
) {
    Box(modifier) {
        val configuration = LocalConfiguration.current
        val config = uiState.columnsConfig
        val slots = homeColumnsSlotCount(
            configuration.screenWidthDp.toFloat(),
            configuration.screenHeightDp.toFloat(),
            config.columnCount
        )
        val tier = homeColumnsBandTier(configuration.screenWidthDp, configuration.screenHeightDp)
        LaunchedEffect(slots) { onSlotCount(slots) }
        Column(
            modifier = Modifier.matchParentSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            HomeColumnsStrip(
                state = uiState.columns,
                slots = slots,
                tier = tier,
                showFocus = showFocus,
                showStepControls = !config.showAllGames,
                showInfoBand = config.showInfoBand,
                friendsFor = uiState::friendsFor,
                onColumnTap = onColumnTap,
                onColumnLongPress = onColumnLongPress,
                onStep = onStep,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                focusedBackground = focusedBackground,
                downloadIndicatorFor = downloadIndicatorFor,
                takeoverProgress = takeoverProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(tier.stripWeight)
            )
            if (config.showInfoBand) {
                val bandDrop = Dimens.spacingXxl
                HomeColumnsBand(
                    uiState = uiState,
                    tier = tier,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(tier.bandWeight)
                        .graphicsLayer {
                            val progress = takeoverProgress()
                            alpha = 1f - progress
                            translationY = progress * bandDrop.toPx()
                        }
                        .padding(horizontal = tier.bandPadding)
                )
            }
        }
    }
}

@Composable
private fun HomeColumnsBand(
    uiState: HomeUiState,
    tier: HomeColumnsBandTier,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val game = uiState.focusedGame
    val media = uiState.focusedMedia
    val title = game?.title ?: media?.title ?: return
    val reduced = reducedMotion()
    val item = uiState.columns.focusedItem
    val text = BandText(
        game = game,
        title = title,
        subtitle = if (game == null) media?.subtitle else null,
        progress = uiState.columns.progressFor(game?.id)
    )
    val friendsFor: (HomeGameUi?) -> List<FriendActivity> = uiState::friendsFor
    BoxWithConstraints(modifier) {
        if (tier == HomeColumnsBandTier.STACKED) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val rowHeight = maxHeight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
                    ) {
                        BandCover(item, rowHeight, rowHeight, reduced, onCoverLoadFailed, onCoverLoaded)
                        BandTextContent(text, reduced, BAND_TITLE_LABEL, Modifier.weight(1f)) { shown ->
                            BandTitleColumn(shown, tier, friendsFor, Modifier.fillMaxWidth())
                        }
                    }
                }
                BandTextContent(text, reduced, BAND_PANEL_LABEL, Modifier.fillMaxWidth()) { shown ->
                    BandPanel(shown, tier, reduced, Modifier.fillMaxWidth())
                }
            }
        } else {
            val bandHeight = maxHeight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
            ) {
                BandCover(
                    item = item,
                    slotHeight = bandHeight,
                    coverHeight = bandHeight * (1f + tier.coverOverlap),
                    reduced = reduced,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded
                )
                BandTextContent(text, reduced, BAND_TITLE_LABEL, Modifier.weight(1f)) { shown ->
                    Row(horizontalArrangement = Arrangement.spacedBy(tier.titlePanelGap)) {
                        BandTitleColumn(shown, tier, friendsFor, Modifier.weight(tier.titleWeight))
                        BandPanel(shown, tier, reduced, Modifier.weight(1f - tier.titleWeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun BandCover(
    item: HomeRowItem?,
    slotHeight: Dp,
    coverHeight: Dp,
    reduced: Boolean,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit
) {
    val coverWidth = coverHeight * LocalBoxArtStyle.current.aspectRatio
    AnimatedContent(
        targetState = item,
        contentKey = ::bandItemKey,
        transitionSpec = { bandCoverTransition(reduced) },
        label = "band-cover",
        modifier = Modifier
            .width(coverWidth)
            .height(slotHeight)
            .wrapContentHeight(Alignment.Bottom, unbounded = true)
            .requiredHeight(coverHeight)
    ) { shown ->
        if (shown != null) {
            ColumnCover(
                item = shown,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = Modifier.width(coverWidth).height(coverHeight)
            )
        }
    }
}

@Composable
private fun BandTextContent(
    text: BandText,
    reduced: Boolean,
    label: String,
    modifier: Modifier,
    content: @Composable (BandText) -> Unit
) {
    AnimatedContent(
        targetState = text,
        contentKey = { it.game?.id ?: it.title },
        transitionSpec = { bandTextTransition(reduced) },
        label = label,
        modifier = modifier
    ) { shown -> content(shown) }
}

@Composable
private fun BandTitleColumn(
    text: BandText,
    tier: HomeColumnsBandTier,
    friendsFor: (HomeGameUi?) -> List<FriendActivity>,
    modifier: Modifier
) {
    DropColumn(gap = tier.titleGap, modifier = modifier) {
        GameTitle(
            title = text.title,
            titleStyle = MaterialTheme.typography.titleLarge,
            titleColor = MaterialTheme.colorScheme.onSurface,
            seriesScale = HOME_TITLE_SERIES_SCALE,
            maxLines = tier.titleMaxLines,
            adaptiveSize = true
        )
        text.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        text.game?.let {
            BandInfoLine(game = it, modifier = Modifier.dropRank(DROP_FIRST))
            BandBadges(game = it, friends = friendsFor(it))
        }
    }
}

@Composable
private fun BandPanel(text: BandText, tier: HomeColumnsBandTier, reduced: Boolean, modifier: Modifier) {
    val game = text.game
    if (game == null) {
        Spacer(modifier)
        return
    }
    HomeColumnsProgressPanel(
        game = game,
        progress = text.progress,
        reduced = reduced,
        compact = tier == HomeColumnsBandTier.COMPACT,
        modifier = modifier
    )
}

@Composable
private fun BandInfoLine(game: HomeGameUi, modifier: Modifier = Modifier) {
    val fullName = game.platformDisplayName.trim()
    val shortName = game.platformShortName.trim()
    val year = game.releaseYear?.toString()
    val genre = game.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    val players = game.players?.trim()?.takeIf { it.isNotEmpty() }
    val parts = listOfNotNull(
        BandMetaPart.PLATFORM.takeIf { fullName.isNotEmpty() },
        BandMetaPart.YEAR.takeIf { year != null },
        BandMetaPart.GENRE.takeIf { genre != null }
    )
    if (parts.isEmpty() && players == null) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val playersExtraPx = with(LocalDensity.current) { (Dimens.iconXs + Dimens.spacingXs * 2).roundToPx() }
    val lead = { name: String, kept: List<BandMetaPart> ->
        val joined = kept.map {
            when (it) {
                BandMetaPart.PLATFORM -> name
                BandMetaPart.YEAR -> year.orEmpty()
                BandMetaPart.GENRE -> genre.orEmpty()
            }
        }.joinToString(BAND_FACT_SEPARATOR)
        if (players != null && joined.isNotEmpty()) joined + BAND_FACT_SEPARATOR else joined
    }
    BoxWithConstraints(modifier) {
        val maxPx = constraints.maxWidth
        val fit = remember(measurer, style, maxPx, fullName, shortName, year, genre, players, parts) {
            val playersPx = players?.let {
                measurer.measure(it, style, maxLines = 1, softWrap = false).size.width + playersExtraPx
            } ?: 0
            fitBandMeta(fullName, shortName, parts) { name, kept ->
                !constraints.hasBoundedWidth ||
                    measurer.measure(lead(name, kept), style, maxLines = 1, softWrap = false).size.width + playersPx <= maxPx
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            val leadText = lead(fit.platformName, fit.parts)
            if (leadText.isNotEmpty()) {
                Text(
                    text = leadText,
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            players?.let {
                Icon(
                    imageVector = playerCountGlyph(it),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(Dimens.iconXs)
                )
                Text(text = it, style = style, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BandBadges(game: HomeGameUi, friends: List<FriendActivity>) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ratingColor = MaterialTheme.colorScheme.primary
    val statBadge = { rating: Float?, userRating: Int, difficulty: Int ->
        gameStatBadges(rating, userRating, difficulty, 0, 0, null, textColor, ratingColor).firstOrNull()
    }
    val stats = listOfNotNull(
        statBadge(game.rating, 0, 0)?.let { BandBadgePart.COMMUNITY_RATING to it },
        statBadge(null, game.userRating, 0)?.let { BandBadgePart.USER_RATING to it },
        statBadge(null, 0, game.userDifficulty)?.let { BandBadgePart.DIFFICULTY to it }
    )
    val status = CompletionStatus.fromApiValue(game.status)
    val parts = buildList {
        if (friends.isNotEmpty()) add(BandBadgePart.FRIENDS)
        stats.forEach { add(it.first) }
        if (status != null) add(BandBadgePart.STATUS)
    }
    if (parts.isEmpty()) return
    BandBadgeRow(gap = Dimens.spacingSm, parts = parts) {
        if (friends.isNotEmpty()) {
            FriendsActivityBadge(
                friends = friends,
                modifier = Modifier.layoutId(BandBadgePart.FRIENDS),
                textColor = textColor
            )
        }
        stats.forEach { (part, badge) ->
            Box(Modifier.layoutId(part)) { StatBadgeItem(badge, badge.tint, textColor) }
        }
        status?.let {
            val label = stringResource(it.labelRes)
            Box(Modifier.layoutId(BandBadgePart.STATUS)) {
                StatBadgeItem(StatBadge(it.icon, it.color, label), it.color, textColor)
            }
            Icon(
                imageVector = it.icon,
                contentDescription = label,
                tint = it.color,
                modifier = Modifier.layoutId(BandBadgePart.STATUS_ICON).size(Dimens.iconXs)
            )
        }
    }
}

internal const val BAND_FACT_SEPARATOR = " · "

private const val BAND_TITLE_LABEL = "band-title"
private const val BAND_PANEL_LABEL = "band-panel"

private data class BandText(
    val game: HomeGameUi?,
    val title: String,
    val subtitle: String?,
    val progress: ColumnsGameProgress?
)

private fun bandItemKey(item: HomeRowItem?): Any? = when (item) {
    is HomeRowItem.Game -> item.game.id
    is HomeRowItem.Media -> item.media.itemId
    is HomeRowItem.ViewAll, null -> null
}

private fun bandTextTransition(reduced: Boolean): ContentTransform {
    val duration = if (reduced) 0 else Motion.durationPage
    return ContentTransform(
        targetContentEnter = fadeIn(tween(duration, easing = Motion.argosyEase)),
        initialContentExit = fadeOut(tween(duration, easing = Motion.argosyEase)),
        sizeTransform = SizeTransform(clip = false)
    )
}

private fun bandCoverTransition(reduced: Boolean): ContentTransform {
    val duration = if (reduced) 0 else Motion.durationPage
    val spec = tween<IntOffset>(duration, easing = Motion.argosyEase)
    val fade = tween<Float>(duration, easing = Motion.argosyEase)
    return ContentTransform(
        targetContentEnter = slideInVertically(spec) { -it / 2 } + fadeIn(fade),
        initialContentExit = slideOutVertically(spec) { -it / 2 } + fadeOut(fade),
        sizeTransform = SizeTransform(clip = false)
    )
}
