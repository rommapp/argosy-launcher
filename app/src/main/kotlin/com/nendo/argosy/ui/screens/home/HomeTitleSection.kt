package com.nendo.argosy.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.RunningLineLabels
import com.nendo.argosy.ui.components.RunningLineText
import com.nendo.argosy.ui.components.RunningSubLine
import com.nendo.argosy.ui.components.runningLineText
import com.nendo.argosy.ui.components.StatBadgeItem
import com.nendo.argosy.ui.components.gameStatBadges
import com.nendo.argosy.ui.components.friends.FriendsActivityBadge
import com.nendo.argosy.ui.components.friends.FRIENDS_AVATAR_OVERLAP_SHARE
import com.nendo.argosy.ui.components.friends.FRIENDS_LINE_MAX_LINES
import com.nendo.argosy.ui.components.friends.FRIENDS_MAX_STACKED_AVATARS
import com.nendo.argosy.ui.components.friends.friendsActivityLine
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatTimeToBeat

private const val HOME_TITLE_SERIES_SCALE = 0.75f

private val HOME_TITLE_RUNNING_LABELS = RunningLineLabels(
    running = R.string.home_title_running,
    runningIn = R.string.home_title_running_in,
    saving = R.string.home_title_saving,
    runningWithTime = R.string.home_title_running_with_time
)

internal data class HomeMetadataMeasureInput(
    val subtitle: String?,
    val badges: List<String>,
    val friendsLabel: String?,
    val avatarCount: Int,
    val runningLine: RunningLineText? = null
)

private data class HomeTextMeasureKey(
    val text: String,
    val style: TextStyle,
    val maxWidth: Int,
    val maxLines: Int
)

private const val HOME_TEXT_MEASURE_CACHE_SIZE = 2048

private class HomeTextMeasureCache : LinkedHashMap<HomeTextMeasureKey, HomeFlowSize>(
    HOME_TEXT_MEASURE_CACHE_SIZE, 0.75f, true
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HomeTextMeasureKey, HomeFlowSize>?): Boolean =
        size > HOME_TEXT_MEASURE_CACHE_SIZE
}

@Composable
internal fun rememberHomeTitleMetadata(uiState: HomeUiState): List<HomeMetadataMeasureInput> {
    val items = uiState.currentItems
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val running = LocalRunningGames.current
    return remember(items, uiState.friendsActivity, context, configuration, running) {
        items.mapNotNull { item ->
            when (item) {
                is HomeRowItem.Game -> {
                    val game = item.game
                    val friends = uiState.friendsFor(game)
                    val playing = friends.filter { it.playingNow }
                    val shown = playing.ifEmpty { friends }
                    HomeMetadataMeasureInput(
                        runningLine = running.markFor(game.id)?.let {
                            runningLineText(context.resources, it, HOME_TITLE_RUNNING_LABELS)
                        },
                        subtitle = null,
                        badges = gameStatBadges(
                            game.rating, game.userRating, game.userDifficulty,
                            game.achievementCount, game.earnedAchievementCount,
                            timeToBeat = formatTimeToBeat(context, game.timeToBeatMainSec),
                            textColor = Color.Unspecified,
                            ratingColor = Color.Unspecified
                        ).map { it.label },
                        friendsLabel = shown.takeIf { it.isNotEmpty() }?.let {
                            friendsActivityLine(context.resources, it, playingNow = playing.isNotEmpty())
                        },
                        avatarCount = shown.size.coerceAtMost(FRIENDS_MAX_STACKED_AVATARS)
                    )
                }
                is HomeRowItem.Media -> HomeMetadataMeasureInput(item.media.subtitle, emptyList(), null, 0)
                is HomeRowItem.ViewAll -> null
            }
        }
    }
}

@Composable
internal fun rememberHomeTitleReserve(inputs: List<HomeMetadataMeasureInput>, maxWidth: Dp): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val titleStyle = MaterialTheme.typography.headlineMedium
    val subtitleStyle = MaterialTheme.typography.bodyMedium
    val badgeStyle = MaterialTheme.typography.labelMedium
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val resolvedTypefaces by remember(fontFamilyResolver, titleStyle, subtitleStyle, badgeStyle) {
        derivedStateOf {
            listOf(titleStyle, subtitleStyle, badgeStyle).map { style ->
                fontFamilyResolver.resolve(
                    fontFamily = style.fontFamily,
                    fontWeight = style.fontWeight ?: FontWeight.Normal,
                    fontStyle = style.fontStyle ?: FontStyle.Normal,
                    fontSynthesis = style.fontSynthesis ?: FontSynthesis.All
                ).value
            }
        }
    }
    val measurements = remember(
        maxWidth, titleStyle, subtitleStyle, badgeStyle, density, configuration,
        textMeasurer, resolvedTypefaces
    ) { HomeTextMeasureCache() }
    val gap = with(density) { Dimens.spacingXs.roundToPx() }
    val rowGap = with(density) { Dimens.spacingSm.roundToPx() }
    val friendGap = with(density) { Dimens.spacingSm.roundToPx() }
    val icon = with(density) { Dimens.iconXs.roundToPx() }
    val avatar = with(density) { Dimens.iconMd.toPx() }
    return remember(
        inputs, maxWidth, titleStyle, subtitleStyle, badgeStyle, density, configuration,
        textMeasurer, resolvedTypefaces, measurements, gap, rowGap, friendGap, icon, avatar
    ) {
        val width = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        fun measure(
            value: String,
            style: TextStyle,
            available: Int = width,
            maxLines: Int = Int.MAX_VALUE
        ): HomeFlowSize {
            val key = HomeTextMeasureKey(value, style, available.coerceAtLeast(1), maxLines)
            return measurements.getOrPut(key) {
                val result = textMeasurer.measure(
                    value, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = key.maxWidth)
                )
                HomeFlowSize(result.size.width, result.size.height)
            }
        }
        val metadata = inputs.distinct().maxOfOrNull { input ->
            val subtitleHeight = input.subtitle?.let { measure(it, subtitleStyle, maxLines = 1).height } ?: 0
            val items = buildList {
                input.runningLine?.let { line ->
                    val lead = measure(line.lead, badgeStyle, maxLines = 1)
                    val time = line.trailingTime?.let { measure(it, badgeStyle, maxLines = 1) }
                    val lineWidth = lead.width + (time?.let { it.width + rowGap } ?: 0)
                    add(HomeFlowSize(lineWidth.coerceAtMost(width), maxOf(lead.height, time?.height ?: 0)))
                }
                input.friendsLabel?.let {
                    val avatarAdvance = avatar * (1f - FRIENDS_AVATAR_OVERLAP_SHARE)
                    val avatarsWidth = (avatar + avatarAdvance * (input.avatarCount - 1)).toInt()
                    val label = measure(
                        it, badgeStyle, width - avatarsWidth - friendGap, maxLines = FRIENDS_LINE_MAX_LINES
                    )
                    add(HomeFlowSize(avatarsWidth + friendGap + label.width, maxOf(avatar.toInt(), label.height)))
                }
                input.badges.forEach {
                    val label = measure(it, badgeStyle, width - icon - gap)
                    add(HomeFlowSize(icon + gap + label.width, maxOf(icon, label.height)))
                }
            }
            val badgesHeight = homeFlowSize(items, width, rowGap, gap).height
            val stacked = listOf(subtitleHeight, badgesHeight).filter { it > 0 }
            stacked.sum() + gap * stacked.size
        } ?: 0
        val seriesStyle = titleStyle.copy(fontSize = titleStyle.fontSize * HOME_TITLE_SERIES_SCALE)
        val twoTitleLines = measure("M", seriesStyle).height + measure("M", titleStyle).height
        with(density) { (twoTitleLines + metadata).toDp() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeTitleSection(
    game: HomeGameUi?,
    title: String,
    subtitle: String?,
    friends: List<FriendActivity>,
    maxWidth: Dp,
    centered: Boolean,
    showMetadata: Boolean,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null
) {
    val metadataAlpha by animateFloatAsState(
        targetValue = if (showMetadata) 1f else 0f,
        animationSpec = tween(500),
        label = "metadataAlpha"
    )
    val titleColor = textColorOverride ?: MaterialTheme.colorScheme.onSurface
    val subtitleColor = textColorOverride?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val badges = gameStatBadges(
        rating = game?.rating,
        userRating = game?.userRating ?: 0,
        userDifficulty = game?.userDifficulty ?: 0,
        achievementCount = game?.achievementCount ?: 0,
        earnedAchievementCount = game?.earnedAchievementCount ?: 0,
        timeToBeatMainSec = game?.timeToBeatMainSec,
        textColor = subtitleColor
    )
    val gap = Dimens.spacingXs
    Layout(
        modifier = modifier,
        content = {
            GameTitle(
                title = title,
                titleStyle = MaterialTheme.typography.headlineMedium,
                titleColor = titleColor,
                seriesScale = HOME_TITLE_SERIES_SCALE,
                adaptiveSize = true,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = metadataAlpha }
                )
            }
            Box(Modifier.graphicsLayer { alpha = metadataAlpha }) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(
                        Dimens.spacingSm,
                        if (centered) Alignment.CenterHorizontally else Alignment.Start
                    ),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    val running = game?.let { LocalRunningGames.current.markFor(it.id) }
                    if (running != null) {
                        RunningSubLine(
                            running = running,
                            labels = HOME_TITLE_RUNNING_LABELS,
                            style = MaterialTheme.typography.labelMedium,
                            timeColor = subtitleColor
                        )
                    }
                    FriendsActivityBadge(friends = friends, textColor = subtitleColor)
                    badges.forEach { badge ->
                        StatBadgeItem(badge, textColorOverride ?: badge.tint, subtitleColor)
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val contentConstraints = Constraints(
            maxWidth = minOf(maxWidth.roundToPx().coerceAtLeast(0), constraints.maxWidth)
        )
        val placeables = measurables.map { it.measure(contentConstraints) }
        val visible = placeables.filter { it.height > 0 }
        val width = visible.maxOfOrNull { it.width } ?: 0
        val height = visible.sumOf { it.height } + gap.roundToPx() * (visible.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            visible.forEach { child ->
                child.placeRelative(if (centered) (width - child.width) / 2 else 0, y)
                y += child.height + gap.roundToPx()
            }
        }
    }
}
