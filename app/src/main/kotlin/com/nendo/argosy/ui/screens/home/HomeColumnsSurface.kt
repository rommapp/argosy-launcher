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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

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
        val slots = homeColumnsSlotCount(
            configuration.screenWidthDp.toFloat(),
            configuration.screenHeightDp.toFloat()
        )
        LaunchedEffect(slots) { onSlotCount(slots) }
        Column(
            modifier = Modifier.matchParentSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            HomeColumnsStrip(
                state = uiState.columns,
                slots = slots,
                showFocus = showFocus,
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
                    .weight(ComponentDefaults.HomeColumns.stripWeightRatio)
            )
            val bandDrop = Dimens.spacingXxl
            HomeColumnsBand(
                uiState = uiState,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(ComponentDefaults.HomeColumns.bandWeightRatio)
                    .graphicsLayer {
                        val progress = takeoverProgress()
                        alpha = 1f - progress
                        translationY = progress * bandDrop.toPx()
                    }
                    .padding(horizontal = Dimens.spacingXxl)
            )
        }
    }
}

@Composable
private fun HomeColumnsBand(
    uiState: HomeUiState,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val game = uiState.focusedGame
    val media = uiState.focusedMedia
    val title = game?.title ?: media?.title ?: return
    val reduced = reducedMotion()
    BoxWithConstraints(modifier) {
        val bandHeight = maxHeight
        val coverHeight = bandHeight * (1f + ComponentDefaults.HomeColumns.bandCoverOverlapRatio)
        val coverWidth = coverHeight * LocalBoxArtStyle.current.aspectRatio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
                AnimatedContent(
                    targetState = uiState.columns.focusedItem,
                    contentKey = ::bandItemKey,
                    transitionSpec = { bandCoverTransition(reduced) },
                    label = "band-cover",
                    modifier = Modifier
                        .width(coverWidth)
                        .height(bandHeight)
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                        .requiredHeight(coverHeight)
                ) { item ->
                    if (item != null) {
                        ColumnCover(
                            item = item,
                            onCoverLoadFailed = onCoverLoadFailed,
                            onCoverLoaded = onCoverLoaded,
                            modifier = Modifier.width(coverWidth).height(coverHeight)
                        )
                    }
                }
                AnimatedContent(
                    targetState = BandText(
                        game = game,
                        title = title,
                        subtitle = if (game == null) media?.subtitle else null,
                        progress = uiState.columns.progressFor(game?.id)
                    ),
                    contentKey = { it.game?.id ?: it.title },
                    transitionSpec = { bandTextTransition(reduced) },
                    label = "band-title",
                    modifier = Modifier.weight(1f)
                ) { text ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)) {
                        Column(
                            modifier = Modifier.weight(ComponentDefaults.HomeColumns.bandTitleWeightRatio),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            GameTitle(
                                title = text.title,
                                titleStyle = MaterialTheme.typography.titleLarge,
                                titleColor = MaterialTheme.colorScheme.onSurface,
                                seriesScale = HOME_TITLE_SERIES_SCALE,
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
                                BandInfoLine(game = it)
                                BandBadges(game = it, friends = uiState.friendsFor(it))
                            }
                        }
                        val panelModifier = Modifier.weight(1f - ComponentDefaults.HomeColumns.bandTitleWeightRatio)
                        val panelGame = text.game
                        if (panelGame != null) {
                            HomeColumnsProgressPanel(
                                game = panelGame,
                                progress = text.progress,
                                reduced = reduced,
                                modifier = panelModifier
                            )
                        } else {
                            Spacer(panelModifier)
                        }
                    }
                }
        }
    }
}

@Composable
private fun BandInfoLine(game: HomeGameUi) {
    val facts = listOfNotNull(
        game.platformDisplayName.takeIf { it.isNotBlank() },
        game.releaseYear?.toString(),
        game.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    )
    val players = game.players?.trim()?.takeIf { it.isNotEmpty() }
    if (facts.isEmpty() && players == null) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (facts.isNotEmpty()) {
            val lead = facts.joinToString(BAND_FACT_SEPARATOR)
            Text(
                text = if (players != null) lead + BAND_FACT_SEPARATOR else lead,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandBadges(game: HomeGameUi, friends: List<FriendActivity>) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val badges = gameStatBadges(
        rating = game.rating,
        userRating = game.userRating,
        userDifficulty = game.userDifficulty,
        achievementCount = 0,
        earnedAchievementCount = 0,
        timeToBeatMainSec = null,
        textColor = textColor
    )
    val status = CompletionStatus.fromApiValue(game.status)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        FriendsActivityBadge(friends = friends, textColor = textColor)
        badges.forEach { badge -> StatBadgeItem(badge, badge.tint, textColor) }
        status?.let {
            StatBadgeItem(StatBadge(it.icon, it.color, stringResource(it.labelRes)), it.color, textColor)
        }
    }
}

internal const val BAND_FACT_SEPARATOR = " · "

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
