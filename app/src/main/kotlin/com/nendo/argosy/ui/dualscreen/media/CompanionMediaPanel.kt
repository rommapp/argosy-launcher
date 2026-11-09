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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.components.MediaCastRail
import com.nendo.argosy.ui.screens.media.components.MediaEpisodeRow
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.screens.media.components.MediaSeasonTabs
import com.nendo.argosy.ui.screens.media.episodeLabel
import com.nendo.argosy.ui.screens.media.runtimeLabel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import kotlinx.coroutines.launch

/**
 * The presentation screen's view of the title the player has open: its season and episode list
 * for a show, its synopsis, cast and related titles for a film. Every control is a touch target;
 * the controller stays with the player on the other screen.
 */
@Composable
fun CompanionMediaPanel(itemId: String, bottomInset: Dp, modifier: Modifier = Modifier) {
    val manager = DualScreenManagerHolder.instance ?: return
    val presenter = remember(manager) { CompanionMediaPresenter(manager) }
    LaunchedEffect(presenter, itemId) { presenter.follow(itemId) }
    val state by presenter.state.collectAsState()
    val playback by manager.mediaPlayback.collectAsState()
    val scope = rememberCoroutineScope()
    val play: (String) -> Unit = { id -> scope.launch { presenter.play(id) } }

    Column(modifier = modifier.fillMaxSize().surfaceBackdrop(BackdropRole.CONTENT)) {
        state.nowPlaying?.let { item ->
            NowPlayingHero(
                item = item,
                fallbackTitle = playback?.title.orEmpty(),
                isPlaying = playback?.isPlaying == true,
                showsTransport = playback != null,
                showsBrief = state.isShowMode
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isShowMode -> ShowBody(state, bottomInset, presenter::selectSeason, play)
                state.isLoading && state.nowPlaying == null -> MediaMessageState(
                    icon = Icons.Outlined.Movie,
                    title = stringResource(R.string.dual_media_loading_title),
                    message = null
                )
                else -> TitleBody(state, bottomInset, play)
            }
        }
    }
}

@Composable
private fun ShowBody(
    state: CompanionMediaState,
    bottomInset: Dp,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeTapped: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val nowPlayingIndex = state.episodes.indexOfFirst { it.itemId == state.nowPlayingEpisodeId }
    LaunchedEffect(state.episodes, state.nowPlayingEpisodeId) {
        if (nowPlayingIndex >= 0) listState.animateScrollToItemCentered(nowPlayingIndex)
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
        if (state.episodes.isEmpty()) {
            val busy = state.isLoading || state.isFetchingEpisodes
            MediaMessageState(
                icon = Icons.Outlined.Movie,
                title = when {
                    busy -> stringResource(R.string.dual_media_episodes_loading_title)
                    state.episodeFetchError != null -> stringResource(R.string.dual_media_episodes_error_title)
                    else -> stringResource(R.string.dual_media_episodes_empty_title)
                },
                message = state.episodeFetchError?.takeIf { !busy }
            )
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Dimens.spacingXs, bottom = bottomInset + Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            items(items = state.episodes, key = { it.itemId }) { episode ->
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

@Composable
private fun TitleBody(state: CompanionMediaState, bottomInset: Dp, onTitleTapped: (String) -> Unit) {
    val theme = LocalArgosyTheme.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Dimens.spacingSm, bottom = bottomInset + Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        state.overview?.takeIf { it.isNotBlank() }?.let { synopsis ->
            item(key = "overview") {
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
                )
            }
        }
        if (state.cast.isNotEmpty()) {
            item(key = "cast-header") { SectionHeader(stringResource(R.string.dual_media_cast_header)) }
            item(key = "cast") {
                MediaCastRail(cast = state.cast, focusedIndex = 0, isSectionFocused = false, onSelect = {})
            }
        }
        if (state.related.isNotEmpty()) {
            item(key = "related-header") { SectionHeader(stringResource(R.string.dual_media_related_header)) }
            items(items = state.related, key = { it.itemId }) { title ->
                MediaEpisodeRow(
                    episode = title,
                    isFocused = false,
                    onClick = { onTitleTapped(title.itemId) },
                    onLongClick = { onTitleTapped(title.itemId) },
                    modifier = Modifier.padding(horizontal = Dimens.spacingMd)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = LocalArgosyTheme.current.textDim,
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
private fun NowPlayingHero(
    item: MediaItemUi,
    fallbackTitle: String,
    isPlaying: Boolean,
    showsTransport: Boolean,
    showsBrief: Boolean
) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val heading = item.seriesName ?: item.title.ifBlank { fallbackTitle }
    val catalog = if (item.seriesName != null) {
        listOfNotNull(item.episodeLabel(context), item.title.takeIf { it.isNotBlank() }, item.runtimeLabel(context))
            .joinToString("  ")
    } else {
        listOfNotNull(item.year?.toString(), item.runtimeLabel(context)).joinToString(" - ")
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
                MediaProgressBar(fraction = item.progressFraction, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (showsTransport) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(
                            if (isPlaying) {
                                R.string.dual_media_transport_playing_description
                            } else {
                                R.string.dual_media_transport_paused_description
                            }
                        ),
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
            if (showsBrief) {
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
