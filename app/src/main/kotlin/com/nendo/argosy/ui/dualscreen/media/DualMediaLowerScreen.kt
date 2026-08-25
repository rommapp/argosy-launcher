/**
 * DUAL-SCREEN COMPONENT - Lower display media panel.
 * Rendered in both companion roles: interactive on the control screen, passive in showcase.
 */
package com.nendo.argosy.ui.dualscreen.media

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
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
 * here is a touch target and no focus cursor appears: an episode plays on tap, the toolbar swaps
 * seasons and layouts on tap, and none of it moves the player's own focus. The cursor and the
 * gamepad footer exist only in the one state without a player - browsing the rails with nothing
 * open. [isInteractive] is false in the showcase role, where this display is a readout.
 */
@Composable
fun DualMediaLowerScreen(
    state: DualMediaUiState,
    isInteractive: Boolean,
    onRowTapped: (Int) -> Unit,
    onRowConfirmed: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSeasonPickerToggled: () -> Unit = {},
    onSeasonSelected: (Int) -> Unit = {},
    onEpisodeLayoutSelected: (DualMediaEpisodeLayout) -> Unit = {},
    onJumpToNowPlaying: () -> Unit = {},
    onEpisodeTapped: (String) -> Unit = {}
) {
    val showCursor = isInteractive && !state.isPlaybackLive

    Box(
        modifier = modifier
            .fillMaxSize()
            .surfaceBackdrop(BackdropRole.CONTENT)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.nowPlaying?.let { playing ->
                DualMediaNowPlaying(
                    item = playing,
                    fallbackTitle = state.nowPlayingTitle,
                    isPlaying = state.isPlaying,
                    showBrief = state.isShowMode
                )
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
                        onSeasonPickerToggled = onSeasonPickerToggled,
                        onSeasonSelected = onSeasonSelected,
                        onEpisodeLayoutSelected = onEpisodeLayoutSelected,
                        onJumpToNowPlaying = onJumpToNowPlaying,
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

        if (showCursor && state.hasRows) {
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

/**
 * The show mode below the hero: the season toolbar, the season strip while it is open, and the
 * selected season's episodes as a list or a rail. Every control is a touch target and none draws
 * focus - the pad is driving the player above.
 */
@Composable
private fun DualMediaShowBody(
    state: DualMediaUiState,
    onSeasonPickerToggled: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeLayoutSelected: (DualMediaEpisodeLayout) -> Unit,
    onJumpToNowPlaying: () -> Unit,
    onEpisodeTapped: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val nowPlayingIndex = state.episodes.indexOfFirst { it.itemId == state.nowPlayingEpisodeId }

    LaunchedEffect(state.jumpNonce, state.episodeLayout, nowPlayingIndex >= 0) {
        if (nowPlayingIndex >= 0) listState.animateScrollToItem(nowPlayingIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DualMediaToolbar(
            state = state,
            onSeasonPickerToggled = onSeasonPickerToggled,
            onEpisodeLayoutSelected = onEpisodeLayoutSelected,
            onJumpToNowPlaying = onJumpToNowPlaying
        )

        AnimatedVisibility(visible = state.isSeasonPickerOpen && state.seasons.isNotEmpty()) {
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
                title = if (state.isLoading) "Loading" else "No episodes here yet",
                message = null
            )
            state.episodeLayout == DualMediaEpisodeLayout.RAIL -> LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingLg,
                    top = Dimens.spacingXs,
                    bottom = Dimens.spacingSm
                ),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(
                    items = state.episodes,
                    key = { _, episode -> episode.itemId }
                ) { _, episode ->
                    DualMediaEpisodeRailCard(
                        episode = episode,
                        isNowPlaying = episode.itemId == state.nowPlayingEpisodeId,
                        onClick = { onEpisodeTapped(episode.itemId) }
                    )
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = Dimens.spacingXs,
                    bottom = Dimens.spacingLg
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(
                    items = state.episodes,
                    key = { _, episode -> episode.itemId }
                ) { _, episode ->
                    MediaEpisodeRow(
                        episode = episode,
                        isFocused = false,
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
 * The season toolbar: the season selector on the left, then the rail, jump and list affordances.
 * The jump button returns the list to the episode being watched, re-selecting its season first.
 */
@Composable
private fun DualMediaToolbar(
    state: DualMediaUiState,
    onSeasonPickerToggled: () -> Unit,
    onEpisodeLayoutSelected: (DualMediaEpisodeLayout) -> Unit,
    onJumpToNowPlaying: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.seasons.isNotEmpty()) {
            val shape = RoundedCornerShape(Dimens.radiusPill)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (state.isSeasonPickerOpen) theme.surfaceRaised else theme.surfaceBase,
                        shape
                    )
                    .clickableNoFocus { onSeasonPickerToggled() }
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Text(
                    text = state.selectedSeason?.let { season ->
                        season.seasonNumber?.let { "Season $it" } ?: season.name
                    } ?: "Seasons",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.textPrimary,
                    maxLines = 1
                )
                Icon(
                    imageVector = if (state.isSeasonPickerOpen) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = "Choose season",
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
        DualMediaToolbarButton(
            icon = Icons.Filled.ViewCarousel,
            contentDescription = "Episode rail",
            isSelected = state.episodeLayout == DualMediaEpisodeLayout.RAIL,
            onClick = { onEpisodeLayoutSelected(DualMediaEpisodeLayout.RAIL) }
        )
        DualMediaToolbarButton(
            icon = Icons.Filled.MyLocation,
            contentDescription = "Jump to playing episode",
            isSelected = false,
            onClick = onJumpToNowPlaying
        )
        DualMediaToolbarButton(
            icon = Icons.AutoMirrored.Filled.ViewList,
            contentDescription = "Episode list",
            isSelected = state.episodeLayout == DualMediaEpisodeLayout.LIST,
            onClick = { onEpisodeLayoutSelected(DualMediaEpisodeLayout.LIST) }
        )
    }
}

@Composable
private fun DualMediaToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Box(
        modifier = Modifier
            .size(Dimens.iconXl)
            .clip(shape)
            .background(if (isSelected) theme.surfaceRaised else theme.surfaceBase, shape)
            .clickableNoFocus { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

/**
 * One episode as a rail card: the thumbnail with progress, the catalog line, and the runtime. The
 * playing episode carries the same marker the list rows do.
 */
@Composable
private fun DualMediaEpisodeRailCard(
    episode: MediaItemUi,
    isNowPlaying: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Column(
        modifier = Modifier
            .width(Dimens.mediaBackdropWidth)
            .clip(shape)
            .then(
                if (isNowPlaying) Modifier.background(theme.surfaceRaised, shape) else Modifier
            )
            .clickableNoFocus { onClick() }
            .padding(Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (isNowPlaying) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Playing",
                    tint = theme.focusAccent,
                    modifier = Modifier.size(Dimens.iconXs)
                )
            }
            episode.episodeLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.focusAccent
                )
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        episode.runtimeLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMute,
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
 * The hero for what the player has open. An episode leads with its show: the series name on top,
 * then the catalog line - season and episode number, title, runtime - then the brief. A film leads
 * with its own title and keeps its synopsis in the body below instead.
 */
@Composable
private fun DualMediaNowPlaying(
    item: MediaItemUi,
    fallbackTitle: String,
    isPlaying: Boolean,
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
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    tint = if (isPlaying) theme.focusAccent else theme.textMute,
                    modifier = Modifier.size(Dimens.iconSm)
                )
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
