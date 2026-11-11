package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The clock chip drawn over a running game's last frame, with the play time of this run.
 */
@Composable
fun RunPlayTimeChip(playedMs: Long, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val totalSeconds = playedMs / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val text = when {
        hours > 0 -> stringResource(R.string.running_card_time_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.running_card_time_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.running_card_time_seconds, seconds)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceBase.copy(alpha = PLAY_TIME_SCRIM_ALPHA))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = theme.textPrimary,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            maxLines = 1
        )
    }
}

/**
 * Whether [playedMs] is long enough for [RunPlayTimeChip] to show.
 */
fun showsRunPlayTime(playedMs: Long): Boolean = playedMs >= MILLIS_PER_SECOND

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val PLAY_TIME_SCRIM_ALPHA = 0.85f
