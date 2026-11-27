package com.nendo.argosy.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.GamePlayActivityCalculator
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTimeShort
import com.nendo.argosy.util.formatRelativeTimeVerbose
import com.nendo.argosy.util.formatTimeToBeat
import java.time.Instant

private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun HomeColumnsProgressPanel(
    game: HomeGameUi,
    progress: ColumnsGameProgress?,
    reduced: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val labels = progressLabels(maxWidth)
        val enter = fadeIn(tween(if (reduced) 0 else Motion.durationPage, easing = Motion.argosyEase))
        val activity = progress?.let { activityLine(game, it, compact) } ?: ActivityLine.NONE
        DropColumn(gap = Dimens.spacingXs) {
            StoryRow(game = game, labels = labels)
            TrophiesRow(game = game, labels = labels, modifier = Modifier.dropRank(DROP_SECOND))
            AnimatedVisibility(
                visible = activity != ActivityLine.NONE,
                enter = enter,
                exit = ExitTransition.None,
                modifier = Modifier.dropRank(DROP_FIRST)
            ) {
                progress?.let { ActivityContent(line = activity, progress = it) }
            }
            AnimatedVisibility(
                visible = progress?.lastPlayedAt != null,
                enter = enter,
                exit = ExitTransition.None
            ) {
                progress?.let { LastPlayedLine(progress = it) }
            }
        }
    }
}

private data class ProgressLabels(val iconOnly: Boolean, val width: Dp)

@Composable
private fun progressLabelStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(
    letterSpacing = ComponentDefaults.HomeColumns.kickerTrackingRatio.em
)

@Composable
private fun progressLabels(panelWidth: Dp): ProgressLabels {
    if (panelWidth < ComponentDefaults.HomeColumns.progressLabelMinPanelWidthDp.dp) {
        return ProgressLabels(iconOnly = true, width = Dimens.iconXs)
    }
    val style = progressLabelStyle()
    val measurer = rememberTextMeasurer()
    val story = stringResource(R.string.home_columns_progress_story).uppercase()
    val trophies = stringResource(R.string.home_columns_progress_trophies).uppercase()
    val widestPx = remember(measurer, style, story, trophies) {
        listOf(story, trophies).maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
    }
    val widest = with(LocalDensity.current) { widestPx.toDp() }
    return ProgressLabels(
        iconOnly = false,
        width = max(panelWidth * ComponentDefaults.HomeColumns.progressLabelWidthRatio, widest)
    )
}

@Composable
private fun StoryRow(game: HomeGameUi, labels: ProgressLabels) {
    val context = LocalContext.current
    val played = game.playTimeMinutes.takeIf { it > 0 }?.let { formatPlayTime(context, it) }
    val lengthSec = game.timeToBeatMainSec?.takeIf { it > 0 }
    val length = formatTimeToBeat(context, lengthSec)
    val value = when {
        played != null && length != null ->
            stringResource(R.string.home_columns_progress_story_of, played, length)
        played != null -> played
        length != null -> stringResource(R.string.home_columns_progress_story_length, length)
        else -> return
    }
    val fraction = lengthSec?.let { (game.playTimeMinutes * SECONDS_PER_MINUTE / it.toFloat()).coerceIn(0f, 1f) }
    ProgressRow(
        label = stringResource(R.string.home_columns_progress_story),
        icon = Icons.Default.Schedule,
        value = value,
        fraction = fraction,
        tint = null,
        labels = labels
    )
}

@Composable
private fun TrophiesRow(game: HomeGameUi, labels: ProgressLabels, modifier: Modifier = Modifier) {
    val total = game.achievementCount.takeIf { it > 0 } ?: return
    val earned = game.earnedAchievementCount.coerceIn(0, total)
    ProgressRow(
        label = stringResource(R.string.home_columns_progress_trophies),
        icon = Icons.Filled.EmojiEvents,
        value = stringResource(R.string.home_columns_progress_trophies_count, earned, total),
        fraction = earned / total.toFloat(),
        tint = ColorTokens.Domain.trophyAmber,
        labels = labels,
        modifier = modifier
    )
}

@Composable
private fun ProgressRow(
    label: String,
    icon: ImageVector,
    value: String,
    fraction: Float?,
    tint: Color?,
    labels: ProgressLabels,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (labels.iconOnly) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint ?: muted,
                modifier = Modifier.size(Dimens.iconXs)
            )
        } else {
            Text(
                text = label.uppercase(),
                style = progressLabelStyle(),
                color = muted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(labels.width)
            )
        }
        if (fraction != null) {
            ArgosyProgressBar(progress = fraction, tint = tint, modifier = Modifier.weight(1f))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

private enum class ActivityLine { STRIP_CAPTIONED, STRIP, UNPLAYED, IDLE, NONE }

private fun activityLine(game: HomeGameUi, progress: ColumnsGameProgress, compact: Boolean): ActivityLine = when {
    progress.playedInWindow -> if (compact) ActivityLine.STRIP else ActivityLine.STRIP_CAPTIONED
    game.lastPlayedAt == null && progress.lastPlayedAt == null -> ActivityLine.UNPLAYED
    compact && progress.lastPlayedAt != null -> ActivityLine.NONE
    else -> ActivityLine.IDLE
}

@Composable
private fun ActivityContent(line: ActivityLine, progress: ColumnsGameProgress) {
    val weeks = GamePlayActivityCalculator.WEEKS
    val mutedStyle = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (line) {
        ActivityLine.STRIP_CAPTIONED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            ActivityStrip(shares = progress.weeks, modifier = Modifier.weight(1f))
            Text(
                text = pluralStringResource(R.plurals.home_columns_activity_window, weeks, weeks),
                style = mutedStyle,
                color = muted,
                maxLines = 1
            )
        }
        ActivityLine.STRIP -> ActivityStrip(shares = progress.weeks, modifier = Modifier.fillMaxWidth())
        ActivityLine.UNPLAYED -> Text(
            text = stringResource(R.string.home_columns_activity_unplayed),
            style = mutedStyle,
            color = muted,
            maxLines = 1
        )
        ActivityLine.IDLE -> Text(
            text = pluralStringResource(R.plurals.home_columns_activity_idle, weeks, weeks),
            style = mutedStyle,
            color = muted,
            maxLines = 1
        )
        ActivityLine.NONE -> Unit
    }
}

@Composable
private fun ActivityStrip(shares: List<Float>, modifier: Modifier = Modifier) {
    val fill = LocalArgosyTheme.current.focusAccent
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = ComponentDefaults.HomeColumns.activityTrackAlpha)
    val barWidth = ComponentDefaults.HomeColumns.activityBarWidthRatio
    Row(
        modifier = modifier.height(ComponentDefaults.HomeColumns.activityStripHeightDp.dp),
        horizontalArrangement = Arrangement.spacedBy(ComponentDefaults.HomeColumns.activityBarGapDp.dp)
    ) {
        shares.forEach { share ->
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(Modifier.fillMaxWidth(barWidth).fillMaxHeight().background(track))
                if (share > 0f) {
                    Box(Modifier.fillMaxWidth(barWidth).fillMaxHeight(share).background(fill))
                }
            }
        }
    }
}

@Composable
private fun LastPlayedLine(progress: ColumnsGameProgress) {
    val at = progress.lastPlayedAt ?: return
    val context = LocalContext.current
    val instant = Instant.ofEpochMilli(at)
    val ago = formatRelativeTimeVerbose(context, instant)
    val withDevice = progress.lastPlayedDevice?.let { stringResource(R.string.home_columns_last_played_on, ago, it) }
    val verbose = stringResource(R.string.home_columns_last_played, ago)
    val short = stringResource(R.string.home_columns_last_played_short, formatRelativeTimeShort(context, instant))
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val maxPx = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        val text = remember(measurer, style, maxPx, withDevice, verbose, short) {
            firstFitting(listOfNotNull(withDevice, verbose), short) {
                !bounded || measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= maxPx
            }
        }
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
