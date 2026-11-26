package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val MS_PER_SECOND = 1000L

private val PlayingRowStripe = FocusIndicators(stripe = true)

@Composable
fun QueueTrackRow(
    number: Int,
    track: MusicQueueTrack,
    isCurrent: Boolean,
    isFocused: Boolean,
    currentDescription: String,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val detail = track.artist ?: track.gameTitle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight)
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .argosyFocusIndicators(
                focused = false,
                selected = isCurrent,
                indicators = PlayingRowStripe,
                stripeColor = theme.focusAccent,
                stripeWidth = Dimens.borderMedium
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(Dimens.iconLg), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = currentDescription,
                    tint = theme.focusAccent,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        track.durationSec?.let { seconds ->
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = formatPlaybackTime(seconds * MS_PER_SECOND),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1
            )
        }
    }
}
