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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.components.MediaCastRail
import com.nendo.argosy.ui.screens.media.components.MediaEpisodeRow
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.screens.media.components.MediaSeasonTabs
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The media panel on the companion screen.
 *
 * While the player runs on the other display the controller belongs to it, so everything drawn
 * here is a touch target and no focus cursor appears: an episode plays on tap, a season tab swaps
 * the listed episodes, and none of it moves the player's own focus. The cursor and the gamepad
 * footer exist only in the states without a player - browsing the rails with nothing open, and the
 * information view opened for one title. [isInteractive] is false in the showcase role, where this
 * display is a readout. [playerLocked] is the exception to the live-playback rule: a viewer who
 * locked the player's controls has handed the pad to this panel on purpose, so the cursor comes
 * back while the film plays on untouched.
 */
@Composable
fun DualMediaLowerScreen(
    state: DualMediaUiState,
    isInteractive: Boolean,
    onRowTapped: (Int) -> Unit,
    onRowConfirmed: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSeasonSelected: (Int) -> Unit = {},
    onEpisodeTapped: (String) -> Unit = {},
    onBackTapped: () -> Unit = {},
    backHint: String = "Library",
    playerLocked: Boolean = false
) {
    val showCursor = isInteractive && (!state.isPlaybackLive || playerLocked)
    val isSeriesBrowse = showCursor && state.isEpisodeBrowse

    Box(
        modifier = modifier
            .fillMaxSize()
            .surfaceBackdrop(BackdropRole.CONTENT)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.nowPlaying?.let { playing ->
                if (isSeriesBrowse) {
                    DualMediaBrowseHeader(
                        title = playing.seriesName
                            ?: playing.title.ifBlank { state.nowPlayingTitle },
                        countLabel = state.episodes.size.takeIf { it > 0 }?.let { count ->
                            if (count == 1) "1 episode" else "$count episodes"
                        },
                        onBack = onBackTapped
                    )
                } else {
                    DualMediaNowPlaying(
                        item = playing,
                        fallbackTitle = state.nowPlayingTitle,
                        isPlaying = state.isPlaying,
                        showTransport = state.isPlaybackLive,
                        showBrief = state.isShowMode
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !state.isSignedIn -> MediaMessageState(
                        icon = Icons.Outlined.Movie,
                        title = "No media account",
                        message = "Sign in to a media server from Settings to watch here."
                    )
                    state.isShowMode -> DualMediaShowBody(
                        state = state,
                        showCursor = showCursor,
                        onSeasonSelected = onSeasonSelected,
                        onEpisodeTapped = onEpisodeTapped
                    )
                    state.isLoading && !state.hasRows && state.nowPlaying == null ->
                        MediaMessageState(
                            icon = Icons.Outlined.Movie,
                            title = "Loading",
                            message = null
                        )
                    state.isEmpty -> MediaMessageState(
                        icon = Icons.Outlined.Movie,
                        title = "Nothing to watch yet",
                        message = "Titles you start appear here once they are under way."
                    )
                    else -> DualMediaTitleBody(
                        state = state,
                        showCursor = showCursor,
                        onRowTapped = onRowTapped,
                        onRowConfirmed = onRowConfirmed
                    )
                }
            }
        }

        val showEpisodeFooter = showCursor && state.isShowMode && state.episodes.isNotEmpty()
        if (showEpisodeFooter || (showCursor && state.hasRows)) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                FooterBar(
                    hints = buildList {
                        if (showEpisodeFooter && state.seasons.size > 1) {
                            add(InputButton.DPAD_HORIZONTAL to "Season")
                        }
                        add(InputButton.A to "Watch")
                        add(InputButton.B to backHint)
                    }
                )
            }
        }
    }
}

/**
 * The show mode below the hero: the season list, always visible, and the selected season's
 * episodes as a scrollable list. Every control is a touch target. While a playback is live the pad
 * drives the player above, no focus is drawn, and the list follows the playing episode; with
 * nothing playing [showCursor] puts the pad's episode cursor on the rows and the list follows it.
 */
@Composable
private fun DualMediaShowBody(
    state: DualMediaUiState,
    showCursor: Boolean,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeTapped: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val nowPlayingIndex = state.episodes.indexOfFirst { it.itemId == state.nowPlayingEpisodeId }

    LaunchedEffect(state.episodes, state.nowPlayingEpisodeId, showCursor) {
        if (!showCursor && nowPlayingIndex >= 0) {
            listState.animateScrollToItemCentered(nowPlayingIndex)
        }
    }
    LaunchedEffect(state.episodes, state.focusedEpisodeIndex, showCursor) {
        if (showCursor && state.focusedEpisodeIndex in state.episodes.indices) {
            listState.animateScrollToItemCentered(state.focusedEpisodeIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.seasons.isNotEmpty()) {
            MediaSeasonTabs(
                seasons = state.seasons,
                selectedIndex = state.selectedSeasonIndex.coerceAtLeast(0),
                isSectionFocused = false,
                onSelect = onSeasonSelected,
                modifier = Modifier.padding(horizontal = Dimens.spacingLg)
            )
        }

        when {
            state.episodes.isEmpty() -> MediaMessageState(
                icon = Icons.Outlined.Movie,
                title = when {
                    state.isLoading || state.isFetchingEpisodes -> "Loading"
                    state.episodeFetchError != null -> "Couldn't load episodes"
                    else -> "No episodes here yet"
                },
                message = state.episodeFetchError
                    ?.takeIf { !state.isLoading && !state.isFetchingEpisodes }
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = Dimens.spacingXs,
                    bottom = if (showCursor) {
                        Dimens.footerHeight + Dimens.spacingLg
                    } else {
                        Dimens.spacingLg
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(
                    items = state.episodes,
                    key = { _, episode -> episode.itemId }
                ) { index, episode ->
                    MediaEpisodeRow(
                        episode = episode,
                        isFocused = showCursor && index == state.focusedEpisodeIndex,
                        isNowPlaying = episode.itemId == state.nowPlayingEpisodeId,
                        onClick = { onEpisodeTapped(episode.itemId) },
                        onLongClick = { onEpisodeTapped(episode.itemId) },
                        modifier = Modifier.padding(horizontal = Dimens.spacingMd)
                    )
                }
            }
        }
    }
}

/**
 * The movie mode below the hero, and the browse rails when nothing is open: synopsis, the cast
 * rail, and the tappable title rows.
 */
@Composable
private fun DualMediaTitleBody(
    state: DualMediaUiState,
    showCursor: Boolean,
    onRowTapped: (Int) -> Unit,
    onRowConfirmed: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusedRowIndex, showCursor) {
        if (showCursor && state.focusedRowIndex >= 0 &&
            state.focusedRowIndex < state.rows.size
        ) {
            listState.animateScrollToItem(state.focusedRowIndex)
        }
    }

    LazyColumn(
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
                MediaCastRail(
                    cast = state.cast,
                    focusedIndex = 0,
                    isSectionFocused = false,
                    onSelect = {}
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
                    isFocused = showCursor && index == state.focusedRowIndex,
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

/**
 * The compact header for browsing a series with nothing playing: a tappable back affordance (the
 * same exit B takes), the series title, and how many episodes the selected season lists. The
 * episode list is the point of that screen, so this is one row where the now-playing hero would
 * have described a playback that does not exist.
 */
@Composable
private fun DualMediaBrowseHeader(
    title: String,
    countLabel: String?,
    onBack: () -> Unit
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
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(theme.surfaceRaised)
                .clickableNoFocus { onBack() }
                .padding(Dimens.spacingSm),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = theme.textPrimary,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        countLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1
            )
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

/**
 * The hero for the title this panel describes. An episode leads with its show: the series name on
 * top, then the catalog line - season and episode number, title, runtime - then the brief. A film
 * leads with its own title and keeps its synopsis in the body below instead. The transport icon is
 * drawn only while a playback is live; an information view with nothing playing has no transport
 * state to report.
 */
@Composable
private fun DualMediaNowPlaying(
    item: MediaItemUi,
    fallbackTitle: String,
    isPlaying: Boolean,
    showTransport: Boolean,
    showBrief: Boolean
) {
    val theme = LocalArgosyTheme.current
    val heading = item.seriesName
        ?: item.title.ifBlank { fallbackTitle }
    val catalog = if (item.seriesName != null) {
        listOfNotNull(item.episodeLabel, item.title.takeIf { it.isNotBlank() }, item.runtimeLabel)
            .joinToString("  ")
    } else {
        listOfNotNull(item.year?.toString(), item.runtimeLabel).joinToString(" - ")
    }
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
                if (showTransport) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPlaying) "Playing" else "Paused",
                        tint = if (isPlaying) theme.focusAccent else theme.textMute,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (catalog.isNotBlank()) {
                Text(
                    text = catalog,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showBrief) {
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
}
