package com.nendo.argosy.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.components.StatBadgeItem
import com.nendo.argosy.ui.components.gameStatBadges
import com.nendo.argosy.ui.components.friends.FriendsActivityBadge
import com.nendo.argosy.ui.components.friends.friendsActivityLine
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatTimeToBeat
import kotlin.math.ceil

internal data class HomeMetadataMeasureInput(
    val developer: String?,
    val badges: List<String>,
    val friendsLabel: String?,
    val avatarCount: Int
)

@Composable
internal fun rememberHomeTitleMetadata(uiState: HomeUiState): List<HomeMetadataMeasureInput> {
    val items = uiState.currentItems
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(items, uiState.friendsActivity, context, configuration) {
        items.mapNotNull { item ->
            when (item) {
                is HomeRowItem.Game -> {
                    val game = item.game
                    val friends = uiState.friendsFor(game)
                    val playing = friends.filter { it.playingNow }
                    val shown = playing.ifEmpty { friends }
                    HomeMetadataMeasureInput(
                        developer = game.developer,
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
                        avatarCount = shown.size.coerceAtMost(3)
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
    val developerStyle = MaterialTheme.typography.bodyMedium
    val badgeStyle = MaterialTheme.typography.labelMedium
    val gap = with(density) { Dimens.spacingXs.roundToPx() }
    val rowGap = with(density) { Dimens.spacingSm.roundToPx() }
    val friendGap = with(density) { Dimens.spacingSm.roundToPx() }
    val icon = with(density) { Dimens.iconXs.roundToPx() }
    val avatar = with(density) { Dimens.iconMd.toPx() }
    return remember(
        inputs, maxWidth, titleStyle, developerStyle, badgeStyle, density, configuration,
        textMeasurer, gap, rowGap, friendGap, icon, avatar
    ) {
        val width = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        fun measure(
            value: String,
            style: TextStyle,
            available: Int = width,
            maxLines: Int = Int.MAX_VALUE
        ): HomeFlowSize {
            val result = textMeasurer.measure(
                value, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = available.coerceAtLeast(1))
            )
            return HomeFlowSize(result.size.width, result.size.height)
        }
        val metadata = inputs.maxOfOrNull { input ->
            val items = buildList {
                input.developer?.let { add(measure(it, developerStyle)) }
                input.friendsLabel?.let {
                    val avatarsWidth = (avatar + avatar * 0.65f * (input.avatarCount - 1)).toInt()
                    val label = measure(it, badgeStyle, width - avatarsWidth - friendGap, maxLines = 2)
                    add(HomeFlowSize(avatarsWidth + friendGap + label.width, maxOf(avatar.toInt(), label.height)))
                }
                input.badges.forEach {
                    val label = measure(it, badgeStyle, width - icon - gap)
                    add(HomeFlowSize(icon + gap + label.width, maxOf(icon, label.height)))
                }
            }
            val metadataHeight = homeFlowSize(items, width, rowGap, gap).height
            metadataHeight + if (metadataHeight > 0) gap else 0
        } ?: 0
        val twoTitleLines = measure("M\nM", titleStyle).height
        with(density) { (twoTitleLines + metadata).toDp() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeTitleSection(
    game: HomeGameUi?,
    title: String,
    developer: String?,
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
            HomeMeasuredTitle(title, maxWidth, centered, titleColor)
            Box(Modifier.graphicsLayer { alpha = metadataAlpha }) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(
                        Dimens.spacingSm,
                        if (centered) Alignment.CenterHorizontally else Alignment.Start
                    ),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    developer?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
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

@Composable
private fun HomeMeasuredTitle(title: String, maxWidth: Dp, centered: Boolean, color: Color) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val style = MaterialTheme.typography.headlineMedium.copy(
        textAlign = if (centered) TextAlign.Center else TextAlign.Start
    )
    val result = textMeasurer.measure(
        text = title,
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = with(density) { maxWidth.roundToPx().coerceAtLeast(1) })
    )
    val visibleWidth = ceil((0 until result.lineCount).maxOfOrNull {
        result.getLineRight(it) - result.getLineLeft(it)
    } ?: 0f).toInt().coerceAtMost(result.size.width)
    val x = when {
        centered -> (visibleWidth - result.size.width) / 2f
        direction == LayoutDirection.Rtl -> (visibleWidth - result.size.width).toFloat()
        else -> 0f
    }
    Canvas(
        Modifier
            .size(with(density) { visibleWidth.toDp() }, with(density) { result.size.height.toDp() })
            .semantics { text = AnnotatedString(title) }
    ) {
        drawText(result, color = color, topLeft = Offset(x, 0f))
    }
}
