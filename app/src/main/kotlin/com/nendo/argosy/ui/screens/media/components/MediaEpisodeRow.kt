package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * One episode in the season list. Long press raises the resume choice the way hold-confirm does on a
 * gamepad, so both modalities reach Start Over.
 */
@Composable
fun MediaEpisodeRow(
    episode: MediaItemUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNowPlaying: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (isNowPlaying) Modifier.background(theme.surfaceRaised, shape) else Modifier)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaBackdropWidth)
                .height(Dimens.mediaBackdropHeight)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised)
        ) {
            AsyncImage(
                model = episode.thumbUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (episode.progressFraction > 0f) {
                MediaProgressBar(
                    fraction = episode.progressFraction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                episode.episodeLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.focusAccent
                    )
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isFocused) theme.textPrimary else theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.played) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Watched",
                        tint = theme.focusAccent,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                }
                MediaDownloadBadge(availability = episode.availability, size = Dimens.iconXs)
            }
            val supporting = listOfNotNull(episode.runtimeLabel, episode.overview).joinToString(" - ")
            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
