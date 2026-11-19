package com.nendo.argosy.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.GamePlayActivityCalculator
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTimeVerbose
import com.nendo.argosy.util.formatTimeToBeat
import java.time.Instant

private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun HomeColumnsProgressPanel(
    game: HomeGameUi,
    progress: ColumnsGameProgress?,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val labelWidth = maxWidth * ComponentDefaults.HomeColumns.progressLabelWidthRatio
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            StoryRow(game = game, labelWidth = labelWidth)
            TrophiesRow(game = game, labelWidth = labelWidth)
            AnimatedVisibility(
                visible = progress != null,
                enter = fadeIn(tween(if (reduced) 0 else Motion.durationPage, easing = Motion.argosyEase)),
                exit = ExitTransition.None
            ) {
                progress?.let { ActivityRows(game = game, progress = it) }
            }
        }
    }
}

@Composable
private fun StoryRow(game: HomeGameUi, labelWidth: Dp) {
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
        value = value,
        fraction = fraction,
        tint = null,
        labelWidth = labelWidth
    )
}

@Composable
private fun TrophiesRow(game: HomeGameUi, labelWidth: Dp) {
    val total = game.achievementCount.takeIf { it > 0 } ?: return
    val earned = game.earnedAchievementCount.coerceIn(0, total)
    ProgressRow(
        label = stringResource(R.string.home_columns_progress_trophies),
        value = stringResource(R.string.home_columns_progress_trophies_count, earned, total),
        fraction = earned / total.toFloat(),
        tint = ColorTokens.Domain.trophyAmber,
        labelWidth = labelWidth
    )
}

@Composable
private fun ProgressRow(label: String, value: String, fraction: Float?, tint: Color?, labelWidth: Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = ComponentDefaults.HomeColumns.kickerTrackingRatio.em
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )
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

@Composable
private fun ActivityRows(game: HomeGameUi, progress: ColumnsGameProgress) {
    val weeks = GamePlayActivityCalculator.WEEKS
    val mutedStyle = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        if (progress.playedInWindow) {
            Row(
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
        } else {
            Text(
                text = if (game.lastPlayedAt == null && progress.lastPlayedAt == null) {
                    stringResource(R.string.home_columns_activity_unplayed)
                } else {
                    pluralStringResource(R.plurals.home_columns_activity_idle, weeks, weeks)
                },
                style = mutedStyle,
                color = muted,
                maxLines = 1
            )
        }
        LastPlayedLine(progress = progress)
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
    val ago = formatRelativeTimeVerbose(LocalContext.current, Instant.ofEpochMilli(at))
    val device = progress.lastPlayedDevice
    Text(
        text = if (device != null) {
            stringResource(R.string.home_columns_last_played_on, ago, device)
        } else {
            stringResource(R.string.home_columns_last_played, ago)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
