package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.boxArtFrame
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * One movie or series in the browse grid. Long press mirrors the gamepad's hold-confirm, so the
 * resume prompt is reachable without a controller.
 */
@Composable
fun MediaPosterCard(
    item: MediaItemUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    scaleOverride: Float? = null
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .width(Dimens.mediaPosterWidth)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaPosterWidth)
                .height(Dimens.mediaPosterHeight)
                .boxArtFrame(
                    isFocused = isFocused,
                    scaleOverride = scaleOverride,
                    background = SolidColor(theme.surfaceRaised)
                )
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.posterUrl.isBlank()) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = theme.textMute,
                    modifier = Modifier.size(Dimens.iconLg).align(Alignment.Center)
                )
            }
            MediaTileBadges(
                item = item,
                modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.spacingXs)
            )
            if (item.progressFraction > 0f) {
                MediaProgressBar(
                    fraction = item.progressFraction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MediaTileBadges(item: MediaItemUi, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (item.played) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Watched",
                tint = theme.focusAccent,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        MediaDownloadBadge(availability = item.availability, size = Dimens.iconSm)
    }
}

@Composable
fun MediaProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.borderThick)
            .background(theme.hairlineLow)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(Dimens.borderThick)
                .background(theme.focusAccent)
        )
    }
}
