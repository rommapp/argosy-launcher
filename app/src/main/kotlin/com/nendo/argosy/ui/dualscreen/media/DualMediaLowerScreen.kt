/**
 * DUAL-SCREEN COMPONENT - Lower display media panel.
 * Rendered in both companion roles: interactive on the control screen, passive in showcase.
 */
package com.nendo.argosy.ui.dualscreen.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.components.MediaEpisodeRow
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop

/**
 * The media panel on the companion screen.
 *
 * The header describes what the player has open and the list below it is what else there is to
 * watch, so the small screen answers "what is this" and "what is next" without the film giving up
 * any of the big one. [isInteractive] is false in the showcase role, where this display is a
 * readout and the other one takes the input - the cursor is still drawn there, because it says what
 * the other screen's presses will land on.
 */
@Composable
fun DualMediaLowerScreen(
    state: DualMediaUiState,
    isInteractive: Boolean,
    onRowTapped: (Int) -> Unit,
    onRowConfirmed: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusedRowIndex) {
        if (state.focusedRowIndex >= 0 && state.focusedRowIndex < state.rows.size) {
            listState.animateScrollToItem(state.focusedRowIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .surfaceBackdrop(BackdropRole.CONTENT)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.nowPlaying?.let { playing ->
                DualMediaNowPlaying(
                    item = playing,
                    title = state.nowPlayingTitle.ifBlank { playing.title },
                    subtitle = state.nowPlayingSubtitle,
                    isPlaying = state.isPlaying
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !state.isSignedIn -> MediaMessageState(
                        icon = Icons.Outlined.Movie,
                        title = "No media account",
                        message = "Sign in to a media server from Settings to watch here."
                    )
                    state.isLoading && !state.hasRows -> MediaMessageState(
                        icon = Icons.Outlined.Movie,
                        title = "Loading",
                        message = null
                    )
                    state.isEmpty -> MediaMessageState(
                        icon = Icons.Outlined.Movie,
                        title = "Nothing to watch yet",
                        message = "Titles you start appear here once they are under way."
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = Dimens.spacingSm,
                            bottom = Dimens.footerHeight + Dimens.spacingLg
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        state.overview?.takeIf { it.isNotBlank() }?.let { synopsis ->
                            item(key = "overview") {
                                Text(
                                    text = synopsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = Dimens.spacingMd,
                                        vertical = Dimens.spacingXs
                                    )
                                )
                            }
                        }
                        if (state.cast.isNotEmpty()) {
                            item(key = "cast-header") { DualMediaSectionHeader("Cast") }
                            item(key = "cast") {
                                Text(
                                    text = state.cast.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = Dimens.spacingMd,
                                        vertical = Dimens.spacingXs
                                    )
                                )
                            }
                        }
                        itemsIndexed(
                            items = state.rows,
                            key = { index, row ->
                                when (row) {
                                    is DualMediaRow.Header -> "header-$index-${row.label}"
                                    is DualMediaRow.Item -> row.item.itemId
                                }
                            }
                        ) { index, row ->
                            when (row) {
                                is DualMediaRow.Header -> DualMediaSectionHeader(row.label)
                                is DualMediaRow.Item -> MediaEpisodeRow(
                                    episode = row.item,
                                    isFocused = index == state.focusedRowIndex,
                                    isNowPlaying = row.isNowPlaying,
                                    onClick = {
                                        onRowTapped(index)
                                        onRowConfirmed(index)
                                    },
                                    onLongClick = { onRowTapped(index) },
                                    modifier = Modifier.padding(horizontal = Dimens.spacingMd)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isInteractive && state.hasRows) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                FooterBar(
                    hints = listOf(
                        InputButton.A to "Watch",
                        InputButton.B to "Library"
                    )
                )
            }
        }
    }
}

@Composable
private fun DualMediaSectionHeader(label: String) {
    val theme = LocalArgosyTheme.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = theme.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            start = Dimens.spacingLg,
            end = Dimens.spacingLg,
            top = Dimens.spacingSm,
            bottom = Dimens.spacingXs
        )
    )
}

@Composable
private fun DualMediaNowPlaying(
    item: MediaItemUi,
    title: String,
    subtitle: String?,
    isPlaying: Boolean
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaBackdropWidth)
                .height(Dimens.mediaBackdropHeight)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised)
        ) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.progressFraction > 0f) {
                MediaProgressBar(
                    fraction = item.progressFraction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    tint = if (isPlaying) theme.focusAccent else theme.textMute,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val supporting = listOfNotNull(subtitle, item.year?.toString(), item.runtimeLabel)
                .joinToString(" - ")
            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item.overview?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
