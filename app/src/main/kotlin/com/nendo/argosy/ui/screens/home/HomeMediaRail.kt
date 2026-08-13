package com.nendo.argosy.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.screens.media.components.MediaSignedOutState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * One of home's media rails.
 *
 * It is drawn separately from the game rails rather than folded into the carousel because the tiles
 * are a different shape and answer a different question: a poster with the episode it will play
 * written under it, not a cover with a platform badge.
 */
@Composable
fun HomeMediaRail(
    items: List<HomeMediaUi>,
    focusedIndex: Int,
    isSignedIn: Boolean,
    isLoading: Boolean,
    isNextUp: Boolean,
    onItemTap: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        !isSignedIn -> MediaSignedOutState(modifier = modifier)
        isLoading && items.isEmpty() -> MediaRailLoading(modifier = modifier)
        items.isEmpty() -> MediaRailEmptyState(isNextUp = isNextUp, modifier = modifier)
        else -> MediaRailTiles(
            items = items,
            focusedIndex = focusedIndex,
            onItemTap = onItemTap,
            onItemLongPress = onItemLongPress,
            modifier = modifier
        )
    }
}

@Composable
private fun MediaRailTiles(
    items: List<HomeMediaUi>,
    focusedIndex: Int,
    onItemTap: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex, items.size) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, items.lastIndex))
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.Bottom
    ) {
        itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
            HomeMediaTile(
                media = item,
                isFocused = index == focusedIndex,
                onClick = { onItemTap(index) },
                onLongClick = { onItemLongPress(index) }
            )
        }
    }
}

/**
 * One tile. The poster and the heading are the show; the line beneath is the episode that confirming
 * will start, so the tile never leaves which episode that is to be guessed at.
 */
@Composable
private fun HomeMediaTile(
    media: HomeMediaUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Column(
        modifier = Modifier
            .width(Dimens.mediaPosterWidth)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.Tile, shape = shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaPosterWidth)
                .height(Dimens.mediaPosterHeight)
                .clip(shape)
                .background(theme.surfaceRaised)
        ) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (media.posterUrl.isBlank()) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = theme.textMute,
                    modifier = Modifier.size(Dimens.iconLg).align(Alignment.Center)
                )
            }
            if (media.isDownloaded) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Downloaded",
                    tint = theme.textPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.spacingXs)
                        .size(Dimens.iconSm)
                )
            }
            if (media.progressFraction > 0f) {
                MediaProgressBar(
                    fraction = media.progressFraction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            text = media.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (media.subtitle != null) {
            Text(
                text = media.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MediaRailEmptyState(isNextUp: Boolean, modifier: Modifier = Modifier) {
    MediaMessageState(
        icon = Icons.Outlined.Inbox,
        title = if (isNextUp) "Nothing up next" else "Nothing to continue",
        message = if (isNextUp) {
            "Finish an episode and the next one shows up here."
        } else {
            "Anything you stop part way through shows up here."
        },
        modifier = modifier
    )
}

@Composable
private fun MediaRailLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(Dimens.mediaPosterHeight),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconXl),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}
